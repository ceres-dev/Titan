package dev.cerez.titan.core.strategy.financing.timely;

import dev.cerez.titan.connector.connectors.BinanceConnector;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Puente JNI hacia {@code libtitan_native.so}.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li><b>Java (aquí, fuera del camino crítico):</b> construye el payload del cierre usando
 *       {@code buildQueryString} del Connector (vía reflexión, para mantener consistencia de
 *       codificación/orden) y entrega a C++ la URL del UserData stream ({@code uGetWWS()}).</li>
 *   <li><b>C++ (camino crítico):</b> escucha el stream, detecta {@code FUNDING_FEE}, parchea el
 *       timestamp, firma con HMAC-SHA256 y envía {@code order.place} MARKET + reduceOnly=true
 *       por la WS API de Binance.</li>
 * </ul>
 *
 * <p>Esta clase NO depende de tipos concretos del proyecto (solo reflexión sobre el connector),
 * así que no hay ciclos de compilación.
 *
 * @author Claude
 */
public final class NativeConnector implements AutoCloseable {

    /** 13 dígitos; C++ lo sobrescribe en sitio con el timestamp real al disparar. */
    private static final String TS_PLACEHOLDER = "0000000000000";

    static {
        String path = System.getenv("LIB_NATIVE_PATH");
        if (path != null && !path.isBlank()) {
            System.load(path);
        } else {
            System.loadLibrary("titan_native");
        }
    }

    // ------------------------------------------------------------------ API pública

    /** Qué cerrar: símbolo, lado de la orden de cierre y cantidad EXACTA que se abrió. */
    public record ClosePlan(String symbol, boolean buy, BigDecimal quantity) {}

    public record CloseResult(String symbol, boolean ok, int status, int errorCode, long latencyMicros, String response) {}

    @FunctionalInterface
    public interface Listener {
        /** Se invoca desde un hilo nativo: delega rápido (executor) y no bloquees. */
        void onCloseResult(CloseResult result);
    }

    // ------------------------------------------------------------------ estado

    private final BinanceConnector connector;
    private final Method buildQueryString;
    private final Method hmacSha256;
    private final Method uGetWWS;
    private final Method fGetWWS;
    private final String apiKey;

    private volatile Listener listener = r -> {};
    private volatile Consumer<String> logSink = msg -> System.out.println("[native] " + msg);
    private volatile boolean closed;

    /**
     * @param connector instancia de BinanceConnector (se usa solo por reflexión)
     * @param cpu       core al que anclar el hilo IO (-1 = sin anclar). Recomendado: un core aislado (isolcpus)
     * @param realtime  intenta SCHED_FIFO (requiere privilegios; si no, se ignora)
     */
    public NativeConnector(@NotNull BinanceConnector connector, int cpu, boolean realtime) {
        this.connector = connector;
        try {
            this.buildQueryString = findMethod(connector.getClass(), "buildQueryString", Map.class);
            this.hmacSha256 = findMethod(connector.getClass(), "hmacSha256", String.class, String.class);
            this.uGetWWS = findMethod(connector.getClass(), "uGetWWS");
            this.fGetWWS = findMethod(connector.getClass(), "fGetWWS");
            Object config = readField(connector, "config");
            this.apiKey = (String) invokeNoArgs(config, "getKey");
            String secret = (String) invokeNoArgs(config, "getSecret");

            String wsApiUrl = (String) fGetWWS.invoke(connector);
            if (apiKey == null || apiKey.isBlank() || secret == null || secret.isBlank()) {
                throw new IllegalStateException("API Key/Secret no configurados");
            }

            if (!nativeInit(wsApiUrl, secret, cpu, realtime)) {
                throw new IllegalStateException("nativeInit falló");
            }
            selfTest(secret);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo acceder al Connector por reflexión", e);
        }
    }

    @Contract(mutates = "this")
    public void setListener(Listener listener) {
        this.listener = listener == null ? r -> {} : listener;
    }

    @Contract(mutates = "this")
    public void setLogSink(Consumer<String> sink) {
        this.logSink = sink == null ? msg -> {} : sink;
    }

    /**
     * Llamar ANTES de {@code parkUntil(waitFinal)}.
     *
     * @param plans          una entrada por símbolo a vigilar
     * @param clockDeltaMs   delta servidor-cliente (mismo que usa sendSignedRequest: deltaClienteToServer)
     * @param fundingLocalMs instante LOCAL esperado del funding (nextFundingTime - delta)
     * @return true si el payload fue aceptado y ambas conexiones están listas
     */
    public boolean prepareFunding(List<ClosePlan> plans, long clockDeltaMs, long fundingLocalMs) {
        if (closed || plans.isEmpty()) return false;
        try {
            String url = (String) uGetWWS.invoke(connector);
            String[] symbols = new String[plans.size()];
            String[] payloads = new String[plans.size()];
            for (int i = 0; i < symbols.length; i++) {
                ClosePlan p = plans.get(i);
                symbols[i] = p.symbol();
                payloads[i] = buildClosePayload(p);
            }
            return prepareFunding(url, symbols, payloads, clockDeltaMs, fundingLocalMs);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new IllegalStateException("prepareFunding: fallo en reflexión", e);
        }
    }

    /** Deja de escuchar (no cierra las conexiones). */
    public void disarm() {
        if (!closed) disarm0();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        shutdown();
    }

    // ------------------------------------------------------------------ payload

    /**
     * Misma ruta de construcción que sendSignedRequest, salvo que el timestamp queda como
     * placeholder de 13 dígitos (C++ lo parchea) y que las claves van ORDENADAS
     * (la WS API firma los parámetros en orden alfabético).
     */
    String buildClosePayload(ClosePlan plan) throws InvocationTargetException, IllegalAccessException {
        Map<String, Object> params = new TreeMap<>();
        params.put("apiKey", apiKey);
        params.put("newOrderRespType", "ACK");   // respuesta mínima = más rápida
        params.put("quantity", formatQuantity(plan.quantity()));
        params.put("reduceOnly", "true");        // SIEMPRE
        params.put("side", plan.buy() ? "BUY" : "SELL");
        params.put("symbol", plan.symbol());
        params.put("timestamp", TS_PLACEHOLDER);
        params.put("type", "MARKET");            // SIEMPRE a mercado
        return (String) buildQueryString.invoke(connector, params);
    }

    /**
     * IMPORTANTE: debe producir EXACTAMENTE la misma cantidad que enviaría
     * connector.fSendOrderToMkt(...). Si ese método redondea al stepSize del símbolo,
     * aplica aquí el mismo redondeo.
     */
    private static String formatQuantity(BigDecimal q) {
        return q.abs().stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------ autoverificación

    /** Comprueba que C++ y Connector.hmacSha256/buildQueryString producen resultados idénticos. */
    private void selfTest(String secret) throws ReflectiveOperationException {
        Map<String, Object> sample = new TreeMap<>();
        sample.put("apiKey", "TEST");
        sample.put("quantity", "1.5");
        sample.put("reduceOnly", "true");
        sample.put("side", "BUY");
        sample.put("symbol", "BTCUSDT");
        sample.put("timestamp", "1700000000000");
        sample.put("type", "MARKET");
        String qs = (String) buildQueryString.invoke(connector, sample);
        String expected = "apiKey=TEST&quantity=1.5&reduceOnly=true&side=BUY&symbol=BTCUSDT&timestamp=1700000000000&type=MARKET";
        if (!expected.equals(qs)) {
            throw new IllegalStateException("buildQueryString cambió de formato: " + qs);
        }
        String javaSig = (String) hmacSha256.invoke(connector, qs, secret);
        String nativeSig = nativeSign(qs);
        if (!javaSig.equalsIgnoreCase(nativeSig)) {
            throw new IllegalStateException("HMAC inconsistente Java=" + javaSig + " C++=" + nativeSig);
        }
    }

    // ------------------------------------------------------------------ callbacks (invocados por JNI)

    @SuppressWarnings("unused")
    private void onNativeResult(String symbol, boolean ok, int status, int errorCode, long latencyMicros, String response) {
        try {
            listener.onCloseResult(new CloseResult(symbol, ok, status, errorCode, latencyMicros, response));
        } catch (Throwable t) {
            logSink.accept("listener lanzó: " + t);
        }
    }

    @SuppressWarnings("unused")
    private void onNativeLog(String msg) {
        logSink.accept(msg);
    }

    // ------------------------------------------------------------------ reflexión

    private static Method findMethod(Class<?> start, String name, Class<?>... types) throws NoSuchMethodException {
        for (Class<?> c = start; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, types);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                // sigue subiendo en la jerarquía
            }
        }
        throw new NoSuchMethodException(start.getName() + "#" + name);
    }

    private static Object readField(@NotNull Object target, String name) throws ReflectiveOperationException {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                // sigue subiendo
            }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "#" + name);
    }

    private static Object invokeNoArgs(@NotNull Object target, String name) throws ReflectiveOperationException {
        Method m = findMethod(target.getClass(), name);
        return m.invoke(target);
    }

    // ------------------------------------------------------------------ nativos

    private native boolean nativeInit(String wsApiUrl, String secret, int cpu, boolean realtime);

    private native String nativeSign(String data);

    /**
     * @param userDataUrl     resultado de uGetWWS()
     * @param symbols         símbolos vigilados
     * @param payloadToClose  query string (buildQueryString) con timestamp=0000000000000, alineado con symbols
     */
    private native boolean prepareFunding(String userDataUrl, String[] symbols, String[] payloadToClose,
                                          long clockDeltaMs, long fundingLocalMs);

    private native void disarm0();

    private native void shutdown();
}
 