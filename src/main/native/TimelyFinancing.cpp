//
// Created by Cagut on 20/11/2026.
//

// #include "TimelyFinancing.h"

// =============================================================================
//  titan_native.cpp  -  Cierre ultra-rápido de posiciones tras FUNDING_FEE
//
//  Diseño (prioridad: latencia de CPU, no escalabilidad):
//
//   * UN solo hilo "io" (anclable a un core) atiende las DOS conexiones TLS:
//       - UserData stream  (URL que entrega Java con uGetWWS())
//       - WS API de Binance (order.place)
//     Cuando llega el evento, el mismo hilo que lo leyó escribe la orden:
//     cero cambios de hilo, cero colas, cero locks en el camino crítico.
//
//   * Todo lo costoso se hace en prepareFunding() (antes de parkUntil):
//       - conexiones TLS ya abiertas y calientes
//       - frame WebSocket completo pre-construido por símbolo
//       - HMAC con estados ipad/opad pre-calculados
//     En el camino crítico solo queda: parsear -> escribir 13 dígitos de
//     timestamp -> 1 HMAC-SHA256 -> escribir 64 hex -> 1 SSL_write.
//
//   * Parsing del evento SIN JSON: memmem sobre el buffer crudo del frame.
//
//   * El callback a Java (resultado/logs) va por un hilo aparte para no frenar
//     nunca el hilo io.
// =============================================================================

#include <jni.h>

#include <openssl/err.h>
#include <openssl/sha.h>
#include <openssl/ssl.h>

#include <arpa/inet.h>
#include <fcntl.h>
#include <netdb.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <poll.h>
#include <pthread.h>
#include <sched.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <condition_variable>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <deque>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#ifndef SO_BUSY_POLL
#define SO_BUSY_POLL 46
#endif

namespace {

// ----------------------------------------------------------------------------
// Tiempo
// ----------------------------------------------------------------------------
inline int64_t realtimeMs() {
    timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1000LL + ts.tv_nsec / 1000000;
}
inline uint64_t monoNs() {
    timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<uint64_t>(ts.tv_sec) * 1000000000ULL + static_cast<uint64_t>(ts.tv_nsec);
}

// ----------------------------------------------------------------------------
// Cola de callbacks hacia Java (resultados y logs). Nunca en el camino crítico.
// ----------------------------------------------------------------------------
struct CbItem {
    int kind = 0;  // 0 = log, 1 = resultado
    std::string symbol;
    bool ok = false;
    int status = 0;
    int errCode = 0;
    long long latencyUs = 0;
    std::string text;
};

std::mutex g_cbMu;
std::condition_variable g_cbCv;
std::deque<CbItem> g_cbQ;
std::atomic<bool> g_running{false};

void pushCb(CbItem&& it) {
    {
        std::lock_guard<std::mutex> lk(g_cbMu);
        g_cbQ.push_back(std::move(it));
    }
    g_cbCv.notify_one();
}

void logf(const char* fmt, ...) {
    char buf[512];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    CbItem it;
    it.kind = 0;
    it.text = buf;
    pushCb(std::move(it));
}

// ----------------------------------------------------------------------------
// HMAC-SHA256 con ipad/opad pre-calculados (ver nota: API SHA256_* deprecada
// en OpenSSL 3, pero es la más rápida porque permite copiar el contexto).
// ----------------------------------------------------------------------------
struct HmacKey {
    SHA256_CTX inner;
    SHA256_CTX outer;
    bool ready = false;
};
HmacKey g_hmac;

void hmacInit(HmacKey& k, const uint8_t* key, size_t len) {
    uint8_t block[64];
    std::memset(block, 0, sizeof(block));
    if (len > 64) {
        SHA256(key, len, block);
    } else {
        std::memcpy(block, key, len);
    }
    uint8_t pad[64];
    for (int i = 0; i < 64; i++) pad[i] = block[i] ^ 0x36;
    SHA256_Init(&k.inner);
    SHA256_Update(&k.inner, pad, 64);
    for (int i = 0; i < 64; i++) pad[i] = block[i] ^ 0x5c;
    SHA256_Init(&k.outer);
    SHA256_Update(&k.outer, pad, 64);
    k.ready = true;
    std::memset(block, 0, sizeof(block));
    std::memset(pad, 0, sizeof(pad));
}

inline void hmacSign(const HmacKey& k, const void* msg, size_t n, uint8_t out[32]) {
    SHA256_CTX c = k.inner;
    SHA256_Update(&c, msg, n);
    uint8_t d[32];
    SHA256_Final(d, &c);
    SHA256_CTX o = k.outer;
    SHA256_Update(&o, d, 32);
    SHA256_Final(out, &o);
}

inline void toHex(const uint8_t* in, size_t n, char* out) {
    static const char* D = "0123456789abcdef";
    for (size_t i = 0; i < n; i++) {
        out[2 * i] = D[in[i] >> 4];
        out[2 * i + 1] = D[in[i] & 15];
    }
}

// Escribe exactamente 13 dígitos (timestamp en ms válido hasta el año 2286).
inline void write13(char* dst, int64_t v) {
    for (int i = 12; i >= 0; i--) {
        dst[i] = static_cast<char>('0' + (v % 10));
        v /= 10;
    }
}

// ----------------------------------------------------------------------------
// Conexión TLS + WebSocket mínima (cliente)
// ----------------------------------------------------------------------------
constexpr size_t kInitBuf = 64 * 1024;
constexpr size_t kMaxBuf = 4 * 1024 * 1024;

SSL_CTX* g_ctx = nullptr;
uint64_t g_rxNs = 0;  // instante monotónico en que se leyó el último paquete
const char* g_why = "";  // motivo de la última desconexión (diagnóstico)

struct Conn {
    const char* name = "";
    int fd = -1;
    SSL* ssl = nullptr;
    std::atomic<bool> up{false};
    std::string host, port, path;
    std::vector<uint8_t> buf;
    size_t len = 0;
    std::string frag;
    bool inFrag = false;
    int64_t lastAttemptMs = 0;
    uint64_t gen = 0;
};

Conn g_ud;   // UserData stream
Conn g_api;  // WS API (órdenes)

std::mutex g_urlMu;
std::string g_udUrl;
std::string g_apiUrl;
std::atomic<uint64_t> g_udGen{0};

void closeConn(Conn& c) {
    c.up.store(false, std::memory_order_release);
    if (c.ssl) {
        SSL_free(c.ssl);
        c.ssl = nullptr;
    }
    if (c.fd >= 0) {
        close(c.fd);
        c.fd = -1;
    }
    c.len = 0;
    c.inFrag = false;
    c.frag.clear();
}

bool parseUrl(const std::string& url, std::string& host, std::string& port, std::string& path) {
    static const std::string pfx = "wss://";
    if (url.compare(0, pfx.size(), pfx) != 0) return false;
    size_t s = pfx.size();
    size_t slash = url.find('/', s);
    std::string hp = url.substr(s, slash == std::string::npos ? std::string::npos : slash - s);
    path = (slash == std::string::npos) ? "/" : url.substr(slash);
    size_t colon = hp.rfind(':');
    if (colon != std::string::npos) {
        host = hp.substr(0, colon);
        port = hp.substr(colon + 1);
    } else {
        host = hp;
        port = "443";
    }
    return !host.empty();
}

// Escritura completa. Con el socket en no-bloqueante hace spin (es lo buscado).
bool sslWriteAll(Conn& c, const uint8_t* p, size_t n) {
    size_t off = 0;
    const int64_t deadline = realtimeMs() + 2000;
    while (off < n) {
        int w = SSL_write(c.ssl, p + off, static_cast<int>(n - off));
        if (w > 0) {
            off += static_cast<size_t>(w);
            continue;
        }
        int e = SSL_get_error(c.ssl, w);
        if (e == SSL_ERROR_WANT_WRITE || e == SSL_ERROR_WANT_READ) {
            if (realtimeMs() > deadline) return false;
            continue;
        }
        return false;
    }
    return true;
}

// NOTA sobre la máscara: el RFC 6455 exige el bit MASK en frames cliente->servidor.
// Sobre TLS la máscara no aporta seguridad real, y usar clave 0x00000000 es válido
// y evita el XOR del payload. Binance la acepta.
bool sendControl(Conn& c, uint8_t opcode, const uint8_t* p, size_t n) {
    uint8_t f[6 + 125];
    if (n > 125) n = 125;
    f[0] = static_cast<uint8_t>(0x80 | opcode);
    f[1] = static_cast<uint8_t>(0x80 | n);
    std::memset(f + 2, 0, 4);
    if (n) std::memcpy(f + 6, p, n);
    return sslWriteAll(c, f, 6 + n);
}

bool connectConn(Conn& c, const std::string& url) {
    closeConn(c);
    if (!parseUrl(url, c.host, c.port, c.path)) {
        logf("[%s] URL invalida: %s", c.name, url.c_str());
        return false;
    }
    addrinfo hints;
    std::memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    addrinfo* res = nullptr;
    if (getaddrinfo(c.host.c_str(), c.port.c_str(), &hints, &res) != 0) {
        logf("[%s] DNS fallo: %s", c.name, c.host.c_str());
        return false;
    }
    int fd = -1;
    for (addrinfo* p = res; p; p = p->ai_next) {
        fd = socket(p->ai_family, p->ai_socktype, p->ai_protocol);
        if (fd < 0) continue;
        timeval tv{5, 0};
        setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
        setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
        int one = 1;
        setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &one, sizeof(one));
        if (connect(fd, p->ai_addr, p->ai_addrlen) == 0) break;
        close(fd);
        fd = -1;
    }
    freeaddrinfo(res);
    if (fd < 0) {
        logf("[%s] connect fallo", c.name);
        return false;
    }
    int one = 1;
    setsockopt(fd, IPPROTO_TCP, TCP_QUICKACK, &one, sizeof(one));
    int busy = 50;  // us; requiere soporte del kernel/NIC, si falla se ignora
    setsockopt(fd, SOL_SOCKET, SO_BUSY_POLL, &busy, sizeof(busy));

    SSL* ssl = SSL_new(g_ctx);
    SSL_set_fd(ssl, fd);
    SSL_set_tlsext_host_name(ssl, c.host.c_str());
    SSL_set1_host(ssl, c.host.c_str());
    if (SSL_connect(ssl) != 1) {
        logf("[%s] TLS handshake fallo", c.name);
        SSL_free(ssl);
        close(fd);
        return false;
    }
    c.fd = fd;
    c.ssl = ssl;

    // Upgrade HTTP -> WebSocket
    std::string req = "GET " + c.path + " HTTP/1.1\r\nHost: " + c.host +
                      "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                      "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                      "Sec-WebSocket-Version: 13\r\n\r\n";
    if (!sslWriteAll(c, reinterpret_cast<const uint8_t*>(req.data()), req.size())) {
        closeConn(c);
        return false;
    }
    c.buf.assign(kInitBuf, 0);
    c.len = 0;
    size_t hdrEnd = 0;
    for (;;) {
        int n = SSL_read(c.ssl, c.buf.data() + c.len, static_cast<int>(c.buf.size() - c.len));
        if (n <= 0) {
            logf("[%s] upgrade: sin respuesta", c.name);
            closeConn(c);
            return false;
        }
        c.len += static_cast<size_t>(n);
        const char* b = reinterpret_cast<const char*>(c.buf.data());
        const char* e = static_cast<const char*>(memmem(b, c.len, "\r\n\r\n", 4));
        if (e) {
            hdrEnd = static_cast<size_t>(e - b) + 4;
            break;
        }
        if (c.len == c.buf.size()) {
            closeConn(c);
            return false;
        }
    }
    if (std::memcmp(c.buf.data(), "HTTP/1.1 101", 12) != 0) {
        logf("[%s] upgrade rechazado: %.40s", c.name, reinterpret_cast<const char*>(c.buf.data()));
        closeConn(c);
        return false;
    }
    std::memmove(c.buf.data(), c.buf.data() + hdrEnd, c.len - hdrEnd);
    c.len -= hdrEnd;

    // A partir de aqui: no-bloqueante
    timeval tv0{0, 0};
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv0, sizeof(tv0));
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv0, sizeof(tv0));
    fcntl(fd, F_SETFL, fcntl(fd, F_GETFL, 0) | O_NONBLOCK);
    c.up.store(true, std::memory_order_release);
    return true;
}

using MsgHandler = void (*)(const char*, size_t);

// Devuelve false si hay que reconectar.
bool processFrames(Conn& c, MsgHandler h) {
    size_t off = 0;
    while (c.len - off >= 2) {
        uint8_t* b = c.buf.data() + off;
        const bool fin = (b[0] & 0x80) != 0;
        const uint8_t op = b[0] & 0x0f;
        const bool masked = (b[1] & 0x80) != 0;
        uint64_t plen = b[1] & 0x7f;
        size_t hdr = 2;
        if (plen == 126) {
            if (c.len - off < 4) break;
            plen = (static_cast<uint64_t>(b[2]) << 8) | b[3];
            hdr = 4;
        } else if (plen == 127) {
            if (c.len - off < 10) break;
            plen = 0;
            for (int i = 2; i < 10; i++) plen = (plen << 8) | b[i];
            hdr = 10;
        }
        uint8_t mask[4] = {0, 0, 0, 0};
        if (masked) {
            if (c.len - off < hdr + 4) break;
            std::memcpy(mask, b + hdr, 4);
            hdr += 4;
        }
        if (plen > kMaxBuf) { g_why = "frame demasiado grande"; return false; }
        if (c.len - off < hdr + plen) {
            if (hdr + plen > c.buf.size()) c.buf.resize(hdr + plen + 1024);
            break;
        }
        uint8_t* pl = b + hdr;
        if (masked) {
            for (uint64_t i = 0; i < plen; i++) pl[i] ^= mask[i & 3];
        }
        switch (op) {
            case 0x0:
            case 0x1:
            case 0x2:
                if (fin && op != 0x0 && !c.inFrag) {
                    h(reinterpret_cast<const char*>(pl), static_cast<size_t>(plen));
                } else {
                    if (op != 0x0) {
                        c.frag.assign(reinterpret_cast<const char*>(pl), plen);
                        c.inFrag = true;
                    } else {
                        c.frag.append(reinterpret_cast<const char*>(pl), plen);
                    }
                    if (fin) {
                        c.inFrag = false;
                        h(c.frag.data(), c.frag.size());
                    }
                }
                break;
            case 0x8:
                g_why = "close frame del servidor";
                return false;  // close
            case 0x9:
                if (!sendControl(c, 0xA, pl, static_cast<size_t>(plen))) { g_why = "fallo enviando pong"; return false; }  // ping -> pong
                break;
            default:
                break;  // pong u otros
        }
        off += hdr + static_cast<size_t>(plen);
    }
    if (off) {
        std::memmove(c.buf.data(), c.buf.data() + off, c.len - off);
        c.len -= off;
    }
    return true;
}

// Lee todo lo disponible. false => reconectar.
bool readConn(Conn& c, MsgHandler h) {
    for (;;) {
        if (c.len == c.buf.size()) {
            if (c.buf.size() >= kMaxBuf) { g_why = "buffer lleno"; return false; }
            c.buf.resize(c.buf.size() * 2);
        }
        int n = SSL_read(c.ssl, c.buf.data() + c.len, static_cast<int>(c.buf.size() - c.len));
        if (n > 0) {
            g_rxNs = monoNs();
            c.len += static_cast<size_t>(n);
            if (!processFrames(c, h)) return false;
            if (SSL_pending(c.ssl) > 0) continue;
            return true;
        }
        int e = SSL_get_error(c.ssl, n);
        if (e == SSL_ERROR_WANT_READ || e == SSL_ERROR_WANT_WRITE) return true;
        g_why = (e == SSL_ERROR_ZERO_RETURN) ? "TLS cerrado por el servidor" : (e == SSL_ERROR_SYSCALL ? "error de socket" : "error TLS");
        return false;
    }
}

// ----------------------------------------------------------------------------
// Objetivos armados
// ----------------------------------------------------------------------------
struct Target {
    char symbol[32];
    size_t symLen = 0;
    std::atomic<int> state{0};  // 0 idle, 1 armado, 2 enviado, 3 respondido
    std::string query;          // string que se firma (timestamp parcheable)
    size_t tsQ = 0;
    std::vector<uint8_t> frame;  // frame WS completo (header + JSON)
    size_t tsF = 0, sigF = 0;
    uint64_t detectNs = 0;
};

struct ArmSet {
    std::unique_ptr<Target[]> t;
    size_t n = 0;
    int64_t minFireMs = 0;
    int64_t spinFromMs = 0;
    int64_t deadlineMs = 0;
};

std::atomic<ArmSet*> g_cur{nullptr};
std::atomic<bool> g_armed{false};
std::atomic<int64_t> g_delta{0};
std::vector<ArmSet*> g_retired;

bool validChars(const std::string& s) {
    if (s.empty()) return false;
    for (char ch : s) {
        bool ok = (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') ||
                  ch == '.' || ch == '_' || ch == '-';
        if (!ok) return false;
    }
    return true;
}

// Construye el frame WS pre-firmado a partir del query string que genero Java
// con buildQueryString() (claves ordenadas, timestamp=0000000000000).
bool buildTarget(Target& t, size_t idx, const std::string& q, std::string& err) {
    std::vector<std::pair<std::string, std::string>> kv;
    size_t tsValOff = std::string::npos;
    size_t i = 0;
    while (i <= q.size()) {
        size_t amp = q.find('&', i);
        if (amp == std::string::npos) amp = q.size();
        std::string part = q.substr(i, amp - i);
        size_t eq = part.find('=');
        if (eq == std::string::npos || eq == 0) {
            err = "par invalido en payload";
            return false;
        }
        std::string k = part.substr(0, eq), v = part.substr(eq + 1);
        if (!validChars(k) || !validChars(v)) {
            err = "caracteres no permitidos (¿URL-encoded?) en '" + k + "'";
            return false;
        }
        if (k == "timestamp") tsValOff = i + eq + 1;
        kv.emplace_back(std::move(k), std::move(v));
        i = amp + 1;
    }
    std::string symbol, reduceOnly, type, side, apiKey, ts;
    for (auto& p : kv) {
        if (p.first == "symbol") symbol = p.second;
        else if (p.first == "reduceOnly") reduceOnly = p.second;
        else if (p.first == "type") type = p.second;
        else if (p.first == "side") side = p.second;
        else if (p.first == "apiKey") apiKey = p.second;
        else if (p.first == "timestamp") ts = p.second;
    }
    // Salvaguardas: SIEMPRE mercado + reduceOnly
    if (reduceOnly != "true") { err = "reduceOnly debe ser true"; return false; }
    if (type != "MARKET") { err = "type debe ser MARKET"; return false; }
    if (side != "BUY" && side != "SELL") { err = "side invalido"; return false; }
    if (symbol.empty() || symbol.size() >= sizeof(t.symbol)) { err = "symbol invalido"; return false; }
    if (apiKey.empty()) { err = "falta apiKey"; return false; }
    if (ts != "0000000000000" || tsValOff == std::string::npos) {
        err = "timestamp debe ser el placeholder de 13 ceros";
        return false;
    }

    std::string json = "{\"id\":\"c" + std::to_string(idx) + "\",\"method\":\"order.place\",\"params\":{";
    size_t tsJson = 0;
    for (auto& p : kv) {
        json += "\"" + p.first + "\":";
        if (p.first == "timestamp") {
            tsJson = json.size();
            json += p.second;  // numero sin comillas
        } else {
            json += "\"" + p.second + "\"";
        }
        json += ",";
    }
    json += "\"signature\":\"";
    size_t sigJson = json.size();
    json.append(64, '0');
    json += "\"}}";

    std::vector<uint8_t> frame;
    frame.reserve(json.size() + 14);
    frame.push_back(0x81);  // FIN + text
    size_t hdr;
    if (json.size() < 126) {
        frame.push_back(static_cast<uint8_t>(0x80 | json.size()));
        hdr = 2 + 4;
    } else if (json.size() < 65536) {
        frame.push_back(0x80 | 126);
        frame.push_back(static_cast<uint8_t>(json.size() >> 8));
        frame.push_back(static_cast<uint8_t>(json.size() & 0xff));
        hdr = 4 + 4;
    } else {
        err = "payload demasiado grande";
        return false;
    }
    for (int m = 0; m < 4; m++) frame.push_back(0);  // mask key = 0 (ver sendControl)
    frame.insert(frame.end(), json.begin(), json.end());

    std::memcpy(t.symbol, symbol.c_str(), symbol.size() + 1);
    t.symLen = symbol.size();
    t.query = q;
    t.tsQ = tsValOff;
    t.frame = std::move(frame);
    t.tsF = hdr + tsJson;
    t.sigF = hdr + sigJson;
    t.state.store(1, std::memory_order_relaxed);
    return true;
}

void pushResult(const Target& t, bool ok, int status, int err, long long latUs, const char* text, size_t tl) {
    CbItem it;
    it.kind = 1;
    it.symbol = t.symbol;
    it.ok = ok;
    it.status = status;
    it.errCode = err;
    it.latencyUs = latUs;
    it.text.assign(text, tl);
    pushCb(std::move(it));
}

// ----------------------------------------------------------------------------
// CAMINO CRITICO
// ----------------------------------------------------------------------------
inline void fire(ArmSet* s, size_t i, uint64_t detectNs) {
    Target& t = s->t[i];
    int expected = 1;
    if (!t.state.compare_exchange_strong(expected, 2, std::memory_order_acq_rel)) return;  // ya disparado

    const int64_t ts = realtimeMs() + g_delta.load(std::memory_order_relaxed);
    write13(&t.query[t.tsQ], ts);
    write13(reinterpret_cast<char*>(t.frame.data()) + t.tsF, ts);
    uint8_t mac[32];
    hmacSign(g_hmac, t.query.data(), t.query.size(), mac);
    toHex(mac, 32, reinterpret_cast<char*>(t.frame.data()) + t.sigF);
    t.detectNs = detectNs;

    if (!g_api.up.load(std::memory_order_acquire) || !sslWriteAll(g_api, t.frame.data(), t.frame.size())) {
        t.state.store(3, std::memory_order_release);
        static const char m[] = "native-send-failed";
        pushResult(t, false, -1, 0, 0, m, sizeof(m) - 1);
        return;
    }
    const uint64_t sentNs = monoNs();
    logf("FUNDING_FEE %s: orden enviada en %lluus desde la lectura", t.symbol,
         static_cast<unsigned long long>((sentNs - detectNs) / 1000));
}

const char kFundingNeedle[] = "\"m\":\"FUNDING_FEE\"";

void onUserData(const char* p, size_t n) {
    if (n < 32) return;
    if (!memmem(p, n, kFundingNeedle, sizeof(kFundingNeedle) - 1)) return;  // 99.9% de los mensajes salen aqui
    if (!g_armed.load(std::memory_order_relaxed)) return;
    ArmSet* s = g_cur.load(std::memory_order_acquire);
    if (!s) return;
    if (realtimeMs() < s->minFireMs) return;  // funding viejo / anterior a la ventana
    const uint64_t detect = g_rxNs;

    const char* end = p + n;
    bool anySymbol = false;
    static const char* const needles[2] = {"\"s\":\"", "\"S\":\""};  // P[].s  (y 'S' como en tu handler Java)
    for (const char* needle : needles) {
        const char* q = p;
        while (q < end) {
            const char* k = static_cast<const char*>(memmem(q, static_cast<size_t>(end - q), needle, 5));
            if (!k) break;
            anySymbol = true;
            const char* v = k + 5;
            const char* e = static_cast<const char*>(memchr(v, '"', static_cast<size_t>(end - v)));
            if (!e) break;
            const size_t L = static_cast<size_t>(e - v);
            for (size_t i = 0; i < s->n; i++) {
                Target& t = s->t[i];
                if (t.symLen == L && std::memcmp(t.symbol, v, L) == 0) fire(s, i, detect);
            }
            q = e;
        }
    }
    // Margen cruzado: el evento llega solo con balance (B), sin simbolo => todos los armados.
    if (!anySymbol) {
        for (size_t i = 0; i < s->n; i++) fire(s, i, detect);
    }
}

void onApiMsg(const char* p, size_t n) {
    const char* k = static_cast<const char*>(memmem(p, n, "\"id\":\"c", 7));
    if (!k) return;
    size_t idx = static_cast<size_t>(std::strtoul(k + 7, nullptr, 10));
    ArmSet* s = g_cur.load(std::memory_order_acquire);
    if (!s || idx >= s->n) return;
    Target& t = s->t[idx];
    const char* st = static_cast<const char*>(memmem(p, n, "\"status\":", 9));
    int status = st ? std::atoi(st + 9) : 0;
    int code = 0;
    if (status != 200) {
        const char* cd = static_cast<const char*>(memmem(p, n, "\"code\":", 7));
        if (cd) code = std::atoi(cd + 7);
    }
    // -2022 = ReduceOnly rechazado => la posicion ya no existe => cierre OK
    const bool ok = (status == 200) || (code == -2022);
    const long long lat = t.detectNs ? static_cast<long long>((g_rxNs - t.detectNs) / 1000) : 0;
    t.state.store(3, std::memory_order_release);
    pushResult(t, ok, status, code, lat, p, n);
}

// ----------------------------------------------------------------------------
// Hilo IO
// ----------------------------------------------------------------------------
int g_cpu = -1;
bool g_realtime = false;

void maintain(Conn& c, bool isUd, int64_t now) {
    if (isUd) {
        uint64_t gen = g_udGen.load(std::memory_order_acquire);
        if (c.up.load(std::memory_order_relaxed) && gen != c.gen) closeConn(c);  // URL nueva (listenKey)
    }
    if (c.up.load(std::memory_order_relaxed)) return;
    if (now - c.lastAttemptMs < 500) return;
    c.lastAttemptMs = now;
    std::string url;
    uint64_t gen = 0;
    {
        std::lock_guard<std::mutex> lk(g_urlMu);
        url = isUd ? g_udUrl : g_apiUrl;
        gen = g_udGen.load(std::memory_order_relaxed);
    }
    if (url.empty()) return;
    c.gen = gen;
    if (connectConn(c, url)) logf("[%s] conectado", c.name);
}

void ioLoop() {
    if (g_cpu >= 0) {
        cpu_set_t set;
        CPU_ZERO(&set);
        CPU_SET(g_cpu, &set);
        if (pthread_setaffinity_np(pthread_self(), sizeof(set), &set) != 0) logf("no se pudo fijar afinidad a cpu %d", g_cpu);
    }
    if (g_realtime) {
        sched_param sp;
        std::memset(&sp, 0, sizeof(sp));
        sp.sched_priority = 20;
        if (pthread_setschedparam(pthread_self(), SCHED_FIFO, &sp) != 0) logf("SCHED_FIFO no disponible (sin privilegios)");
    }

    while (g_running.load(std::memory_order_relaxed)) {
        const int64_t now = realtimeMs();
        maintain(g_ud, true, now);
        maintain(g_api, false, now);

        ArmSet* s = g_cur.load(std::memory_order_acquire);
        const bool armed = g_armed.load(std::memory_order_relaxed) && s != nullptr;
        const bool spin = armed && now >= s->spinFromMs;

        if (!spin) {
            pollfd p[2];
            int np = 0;
            if (g_ud.up.load(std::memory_order_relaxed)) p[np++] = {g_ud.fd, POLLIN, 0};
            if (g_api.up.load(std::memory_order_relaxed)) p[np++] = {g_api.fd, POLLIN, 0};
            if (np) poll(p, static_cast<nfds_t>(np), armed ? 1 : 20);
            else usleep(5000);
        }

        if (g_ud.up.load(std::memory_order_relaxed) && !readConn(g_ud, onUserData)) {
            logf("[%s] desconectado: %s", g_ud.name, g_why);
            closeConn(g_ud);
        }
        if (g_api.up.load(std::memory_order_relaxed) && !readConn(g_api, onApiMsg)) {
            logf("[%s] desconectado: %s", g_api.name, g_why);
            closeConn(g_api);
        }
        if (armed && now > s->deadlineMs) {
            g_armed.store(false, std::memory_order_relaxed);
            logf("armado expirado (deadline)");
        }
    }
    closeConn(g_ud);
    closeConn(g_api);
}

// ----------------------------------------------------------------------------
// JNI / callbacks hacia Java
// ----------------------------------------------------------------------------
JavaVM* g_vm = nullptr;
jobject g_obj = nullptr;
jmethodID g_mResult = nullptr;
jmethodID g_mLog = nullptr;
std::thread g_ioThread, g_cbThread;
bool g_inited = false;

std::string sanitize(const std::string& s) {
    std::string o = s;
    for (auto& ch : o) {
        if (static_cast<unsigned char>(ch) >= 0x80 || ch == 0) ch = '?';
    }
    return o;
}

void cbLoop() {
    JNIEnv* env = nullptr;
    g_vm->AttachCurrentThreadAsDaemon(reinterpret_cast<void**>(&env), nullptr);
    for (;;) {
        CbItem it;
        {
            std::unique_lock<std::mutex> lk(g_cbMu);
            g_cbCv.wait(lk, [] { return !g_cbQ.empty() || !g_running.load(); });
            if (g_cbQ.empty() && !g_running.load()) break;
            it = std::move(g_cbQ.front());
            g_cbQ.pop_front();
        }
        if (it.kind == 0) {
            jstring m = env->NewStringUTF(sanitize(it.text).c_str());
            env->CallVoidMethod(g_obj, g_mLog, m);
            env->DeleteLocalRef(m);
        } else {
            jstring sym = env->NewStringUTF(it.symbol.c_str());
            jstring txt = env->NewStringUTF(sanitize(it.text).c_str());
            env->CallVoidMethod(g_obj, g_mResult, sym, static_cast<jboolean>(it.ok), static_cast<jint>(it.status),
                                static_cast<jint>(it.errCode), static_cast<jlong>(it.latencyUs), txt);
            env->DeleteLocalRef(sym);
            env->DeleteLocalRef(txt);
        }
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    g_vm->DetachCurrentThread();
}

std::string toStd(JNIEnv* env, jstring js) {
    if (!js) return {};
    const char* c = env->GetStringUTFChars(js, nullptr);
    std::string s(c);
    env->ReleaseStringUTFChars(js, c);
    return s;
}

}  // namespace

extern "C" {

    JNIEXPORT jboolean JNICALL Java_dev_cerez_titan_core_strategy_financing_timely_NativeConnector_nativeInit(
        JNIEnv* env, jobject thiz, jstring wsApiUrl, jstring secret, jint cpu, jboolean realtime) {
        if (g_inited) return JNI_TRUE;
        env->GetJavaVM(&g_vm);
        g_obj = env->NewGlobalRef(thiz);
        jclass cls = env->GetObjectClass(thiz);
        g_mResult = env->GetMethodID(cls, "onNativeResult", "(Ljava/lang/String;ZIIJLjava/lang/String;)V");
        g_mLog = env->GetMethodID(cls, "onNativeLog", "(Ljava/lang/String;)V");
        if (!g_mResult || !g_mLog) return JNI_FALSE;

        std::string sec = toStd(env, secret);
        hmacInit(g_hmac, reinterpret_cast<const uint8_t*>(sec.data()), sec.size());
        std::fill(sec.begin(), sec.end(), '\0');

        OPENSSL_init_ssl(0, nullptr);
        g_ctx = SSL_CTX_new(TLS_client_method());
        if (!g_ctx) return JNI_FALSE;
        SSL_CTX_set_min_proto_version(g_ctx, TLS1_2_VERSION);
        SSL_CTX_set_options(g_ctx, SSL_OP_NO_COMPRESSION);
        SSL_CTX_set_mode(g_ctx, SSL_MODE_ENABLE_PARTIAL_WRITE | SSL_MODE_ACCEPT_MOVING_WRITE_BUFFER);
        SSL_CTX_set_default_verify_paths(g_ctx);
        SSL_CTX_set_verify(g_ctx, SSL_VERIFY_PEER, nullptr);

        g_ud.name = "userdata";
        g_api.name = "ws-api";
        g_cpu = cpu;
        g_realtime = realtime == JNI_TRUE;
        {
            std::lock_guard<std::mutex> lk(g_urlMu);
            g_apiUrl = toStd(env, wsApiUrl);
        }
        g_running.store(true);
        g_cbThread = std::thread(cbLoop);
        g_ioThread = std::thread(ioLoop);
        g_inited = true;
        return JNI_TRUE;
    }

    // Firma de prueba: permite a Java verificar que C++ y Connector.hmacSha256 coinciden.
    JNIEXPORT jstring JNICALL Java_dev_cerez_titan_core_strategy_financing_timely_NativeConnector_nativeSign(
        JNIEnv* env, jobject, jstring data) {
        if (!g_hmac.ready) return nullptr;
        std::string d = toStd(env, data);
        uint8_t mac[32];
        hmacSign(g_hmac, d.data(), d.size(), mac);
        char hex[65];
        toHex(mac, 32, hex);
        hex[64] = 0;
        return env->NewStringUTF(hex);
    }

    // Se llama ANTES de parkUntil(waitFinal).
    //  userDataUrl     : resultado de uGetWWS()
    //  symbols[]       : simbolos a vigilar
    //  payloadToClose[]: query string (de buildQueryString) con timestamp=0000000000000
    //  clockDeltaMs    : delta servidor-cliente (se suma al timestamp, igual que sendSignedRequest)
    //  fundingLocalMs  : instante local esperado del funding (nextFundingTime - delta)
    JNIEXPORT jboolean JNICALL Java_dev_cerez_titan_core_strategy_financing_timely_NativeConnector_prepareFunding(
        JNIEnv* env, jobject, jstring userDataUrl, jobjectArray symbols, jobjectArray payloadToClose, jlong clockDeltaMs,
        jlong fundingLocalMs) {
        if (!g_inited) return JNI_FALSE;
        const jsize n = env->GetArrayLength(symbols);
        if (n <= 0 || n > 64 || n != env->GetArrayLength(payloadToClose)) {
            logf("prepareFunding: arrays invalidos (n=%d)", static_cast<int>(n));
            return JNI_FALSE;
        }
        std::unique_ptr<ArmSet> set(new ArmSet());
        set->n = static_cast<size_t>(n);
        set->t.reset(new Target[set->n]);
        for (jsize i = 0; i < n; i++) {
            std::string sym = toStd(env, static_cast<jstring>(env->GetObjectArrayElement(symbols, i)));
            std::string pay = toStd(env, static_cast<jstring>(env->GetObjectArrayElement(payloadToClose, i)));
            std::string err;
            if (!buildTarget(set->t[i], static_cast<size_t>(i), pay, err)) {
                logf("prepareFunding: payload[%d] rechazado: %s", static_cast<int>(i), err.c_str());
                return JNI_FALSE;
            }
            if (sym != set->t[i].symbol) {
                logf("prepareFunding: symbol[%d]=%s no coincide con payload (%s)", static_cast<int>(i), sym.c_str(),
                     set->t[i].symbol);
                return JNI_FALSE;
            }
        }
        set->minFireMs = fundingLocalMs - 500;
        set->spinFromMs = fundingLocalMs - 1500;
        set->deadlineMs = fundingLocalMs + 20000;
        g_delta.store(clockDeltaMs);

        const std::string url = toStd(env, userDataUrl);
        {
            std::lock_guard<std::mutex> lk(g_urlMu);
            if (url != g_udUrl) {
                g_udUrl = url;
                g_udGen.fetch_add(1, std::memory_order_release);
            }
        }

        g_armed.store(false);
        ArmSet* old = g_cur.exchange(set.release(), std::memory_order_acq_rel);
        if (old) g_retired.push_back(old);
        while (g_retired.size() > 2) {  // los mas viejos ya no los lee nadie
            delete g_retired.front();
            g_retired.erase(g_retired.begin());
        }
        g_armed.store(true, std::memory_order_release);

        // Esperar a que ambas conexiones esten arriba (para no llegar ciegos a la ventana).
        const int64_t until = realtimeMs() + 4000;
        while (realtimeMs() < until) {
            if (g_ud.up.load(std::memory_order_acquire) && g_api.up.load(std::memory_order_acquire)) return JNI_TRUE;
            usleep(1000);
        }
        logf("prepareFunding: conexiones no listas (ud=%d api=%d)", g_ud.up.load() ? 1 : 0, g_api.up.load() ? 1 : 0);
        return JNI_FALSE;
    }

    JNIEXPORT void JNICALL Java_dev_cerez_titan_core_strategy_financing_timely_NativeConnector_disarm0(JNIEnv*, jobject) {
        g_armed.store(false, std::memory_order_release);
    }

    JNIEXPORT void JNICALL Java_dev_cerez_titan_core_strategy_financing_timely_NativeConnector_shutdown(JNIEnv* env,
                                                                                                        jobject) {
        if (!g_inited) return;
        g_armed.store(false);
        g_running.store(false);
        g_cbCv.notify_all();
        if (g_ioThread.joinable()) g_ioThread.join();
        if (g_cbThread.joinable()) g_cbThread.join();
        if (g_obj) {
            env->DeleteGlobalRef(g_obj);
            g_obj = nullptr;
        }
        if (g_ctx) {
            SSL_CTX_free(g_ctx);
            g_ctx = nullptr;
        }
        g_inited = false;
    }

}  // extern "C"