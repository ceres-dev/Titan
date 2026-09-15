package dev.cerez.titan.connector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.cerez.titan.Log;
import dev.cerez.titan.connector.exception.ApiException;
import dev.cerez.titan.connector.exception.DefaultApiException;
import dev.cerez.titan.connector.exception.NotSetApiKeysException;
import dev.cerez.titan.connector.model.BookTickDouble;
import dev.cerez.titan.connector.model.Symbol;
import dev.cerez.titan.io.IOdata;
import dev.cerez.titan.utils.telemtry.TelemetryConnector;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public abstract class BaseConnector implements Connector {

    protected static final int MAX_STREAMS_PER_SUBSCRIBE = 100;
//    protected static final int TIMEOUT = 30;
    protected static final int COOLDOWN_MS = 5_000;

    @NotNull  protected final ObjectMapper mapper = new ObjectMapper();
    @NotNull  protected final HttpClient clientHttp = HttpClient.newHttpClient();
    @NotNull  protected final HashMap<String, Symbol> cachedSymbols = new HashMap<>();
    @NotNull  protected final ExecutorService executor = Executors.newFixedThreadPool(8);
    @NotNull  protected final Map<String, WebSocketContainer> webSockets = new HashMap<>();
    @Getter
    @NotNull  protected final ConnectorConfig config;

    @Nullable protected       Keys apiKey;
    @Setter   protected       TelemetryConnector telemetry;

    @NotNull  private final Object streamIncomingLock = new Object();
    @NotNull  private final StringBuilder streamIncomingMessage = new StringBuilder();
    @NotNull  protected final HashMap<String, Consumer<JsonNode>> consumerStreamsMap = new HashMap<>();

    protected volatile boolean waitingForPong = false;
    protected volatile long delayPingPongNanoTime = -1;
    protected volatile long deltaClienteToServer = 0;
    protected volatile boolean runLoopers = false;

    @Setter
    protected Consumer<BookTickDouble> consumerBookTicker;

    public BaseConnector(@NotNull ConnectorConfig config) {
        this.config = config;
    }

    public void invalidateCache() {
        cachedSymbols.clear();
    }

    @Override
    public void subscribeBookTicker(@NotNull Collection<String> symbols) {
        List<String> streams = new ArrayList<>(symbols);
        for (int i = 0; i < streams.size(); i += MAX_STREAMS_PER_SUBSCRIBE) {
            int end = Math.min(i + MAX_STREAMS_PER_SUBSCRIBE, streams.size());
            subscribeBookTickerBatch(streams.subList(i, end));
            if (webSockets.get(sGetWWS()) != null) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(COOLDOWN_MS));
        }
    }

    @Override
    public void start(){
        loadApikey();
        initWebSocket(sGetWWS());
        runLoopers();
    }

    @Override
    public void stop() {
        for (Map.Entry<String, WebSocketContainer> entry : webSockets.entrySet()) {
            entry.getValue().getWebSocket().sendClose(0, "The program has ended.");;
        }
        webSockets.clear();
        stopLoopers();
    }

    public void loadApikey(){
        apiKey = IOdata.loadApiKeysBinance();
    }

    public void syncTimeServer(){
        deltaClienteToServer = getTimeSever() - System.currentTimeMillis();
    }

    public void runLoopers(){
        runLoopers = true;
        executor.execute(() -> {
            while (runLoopers) {
                syncTimeServer();
                LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(60));
            }
        });
        executor.execute(() -> {
            while (runLoopers) {
                LockSupport.parkNanos(TimeUnit.MINUTES.toNanos(30));
                invalidateCache();
            }
        });
    }

    public void stopLoopers(){
        this.runLoopers = false;
    }
    private String lastRequestWebSocker = null;

    public void initWebSocket(String wwsURL) {
        WebSocketContainer container = webSockets.computeIfAbsent(wwsURL, WebSocketContainer::new);
        if (container.isOpen()) return;
        container.setWebSocket(clientHttp.newWebSocketBuilder()
                .buildAsync(URI.create(wwsURL), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        Log.info("WebSocket@%s open", wwsURL);
                        webSocket.request(1);
                        WebSocket.Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        String contentToParse = accumulateMessage(data, last, streamIncomingLock, streamIncomingMessage);
                        if (contentToParse != null) {
                            handleStreamRawExpress(wwsURL, contentToParse);
                            try {
                                handleStreamRaw(wwsURL, mapper.readTree(contentToParse));
                            } catch (JsonProcessingException ignored) {
                                Log.warning("JSON Parsing failed: " + contentToParse);
                            }
                        }
                        webSocket.request(1);
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }

                    @Override
                    @SuppressWarnings("CallToPrintStackTrace")
                    public void onError(WebSocket webSocket, Throwable error) {
                        Log.error("WebSocket@%s error: reason=%s cause=%s".formatted(wwsURL, error.getMessage(), error.getCause()));
                        error.printStackTrace();
                        WebSocket.Listener.super.onError(webSocket, error);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        Log.error("WebSocket@%s closed: Code=%d Reason=%s LastRequest=%s".formatted(wwsURL, statusCode, reason, lastRequestWebSocker));
                        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                    }
                }).join());
        for (String request : container.getPendingRequest()) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(COOLDOWN_MS));
            sendWebSocket(request);
        }
        executor.execute(() -> {
            while (webSockets.containsKey(wwsURL) && webSockets.get(wwsURL).isOpen()) {
                String ping = getPingPayload(wwsURL);
                if (ping == null){
                    return;
                }
                if (webSockets.containsKey(wwsURL)) {
                    sendWebSocket(wwsURL, ping);
                    waitingForPong = true;
                    delayPingPongNanoTime = System.nanoTime();
                }
                LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(15));
            }
        });
    }

    public void sendWebSocket(String content) {
        sendWebSocket(sGetWWS(), content);
    }

    @SuppressWarnings("DataFlowIssue")
    public void sendWebSocket(String wwsURL, String content) {
        if (savePendingRequest(wwsURL, content)) return;
        if (config.isLogsRequest()) Log.info("wws=%s@%s", wwsURL, content);
        lastRequestWebSocker = content;
        WebSocketContainer container = webSockets.get(wwsURL);
        container.setLastRequest(content);
        container.getWebSocket().sendText(content.replaceAll("\\s", ""), true);
    }

    protected @NotNull JsonNode sendSignedRequest(@NotNull Method method,
                                                  @NotNull String endpoint
    ) {
        return sendSignedRequest(sGetHTTPS(), method, endpoint, new HashMap<>());
    }

    protected @NotNull JsonNode sendSignedRequest(@NotNull Method method,
                                                  @NotNull String endpoint,
                                                  @NotNull Map<String, Object> params
    ) {
        return sendSignedRequest(sGetHTTPS(), method, endpoint, params);
    }

    protected @NotNull JsonNode sendSignedRequest(@NotNull String baseUrl,
                                                  @NotNull Method method,
                                                  @NotNull String endpoint
    ) {
        return sendSignedRequest(baseUrl, method, endpoint, new HashMap<>());
    }

    protected @NotNull JsonNode sendSignedRequest(@NotNull String baseUrl,
                                                  @NotNull Method method,
                                                  @NotNull String endpoint,
                                                  @NotNull Map<String, Object> params
    ) {
        if (apiKey == null) {
            throw new NotSetApiKeysException("API Key not set");
        }
        params.put("timestamp", System.currentTimeMillis() + deltaClienteToServer);
        String queryString = buildQueryString(params);
        try {
            String signature = hmacSha256(queryString, apiKey.secret);
            String finalUrl = baseUrl + endpoint + "?" + queryString + "&signature=" + signature;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(finalUrl))
                    .header("X-MBX-APIKEY", apiKey.key)
                    .method(method.name(), HttpRequest.BodyPublishers.noBody())
                    .build();
            if (config.isLogsRequest() && !getBlackListEndpointLog().contains(endpoint)) Log.info("https=%s %s", method, finalUrl);
            JsonNode jsonRaw = null;
            try {
                HttpResponse<String> response = clientHttp.send(request, HttpResponse.BodyHandlers.ofString());
                checkResponse(jsonRaw = mapper.readTree(response.body()), request);
                return jsonRaw;
            } catch (IOException | InterruptedException e) {
                Log.error("Error de IO o Interrupción: %s", e.getMessage());
                throw new RuntimeException(e);
            }catch (DefaultApiException e) {
                Log.error("%s %s -> %s", method.name(), finalUrl, jsonRaw);
                throw e;
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }

    }

    @SuppressWarnings("SameParameterValue")
    protected @NotNull JsonNode sendPublicRequest(@NotNull Method method,
                                                  @NotNull String endpoint
    ) {
        return sendPublicRequest(sGetHTTPS(), method, endpoint, new HashMap<>());
    }

    @SuppressWarnings("SameParameterValue")
    protected @NotNull JsonNode sendPublicRequest(@NotNull Method method,
                                                  @NotNull String endpoint,
                                                  @NotNull Map<String, Object> params
    ) {
        return sendPublicRequest(sGetHTTPS(), method, endpoint, params);
    }

    @SuppressWarnings("SameParameterValue")
    protected @NotNull JsonNode sendPublicRequest(@NotNull String baseUrl,
                                                  @NotNull Method method,
                                                  @NotNull String endpoint
    ) {
        return sendPublicRequest(baseUrl, method, endpoint, new HashMap<>());
    }

    protected @NotNull JsonNode sendPublicRequest(@NotNull String baseUrl,
                                                  @NotNull Method method,
                                                  @NotNull String endpoint,
                                                  @NotNull Map<String, Object> params
    ) {
        String queryString = buildQueryString(params);
        String finalUrl = baseUrl + (
                queryString.isBlank()
                        ? endpoint
                        : endpoint + "?" + queryString
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(finalUrl))
                .method(method.toString(), HttpRequest.BodyPublishers.noBody())
                .build();
        if (config.isLogsRequest() && !getBlackListEndpointLog().contains(endpoint)) Log.info("https=%s@%s", method, finalUrl);
        String jsonRaw = null;
        if (telemetry != null) telemetry.addRequestConnector(method, finalUrl);
        try {
            jsonRaw = clientHttp.send(request, HttpResponse.BodyHandlers.ofString()).body();
            JsonNode node = mapper.readTree(jsonRaw);
            checkResponse(node, request);
            return node;
        } catch (IOException | InterruptedException | ApiException e) {
            Log.error(finalUrl + " @ " + jsonRaw);
            throw new RuntimeException(e);
        }
    }

    protected String buildQueryString(@NotNull Map<String, Object> params) {
        StringJoiner sj = new StringJoiner("&");
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            sj.add(
                    URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                            + "="
                            + URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8)
            );
        }
        return sj.toString();
    }

    protected String hmacSha256(@NotNull String data, @NotNull String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] raw = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(2 * raw.length);
        for (byte b : raw) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    protected @Nullable String accumulateMessage(
            @NotNull CharSequence data,
            boolean last,
            @NotNull Object lock,
            @NotNull StringBuilder buffer
    ) {
        synchronized (lock) {
            buffer.append(data);
            if (!last) {
                return null;
            }
            String content = buffer.toString();
            buffer.setLength(0);
            return content;
        }
    }

    @Contract(pure = true)
    protected static @NotNull Double fastParseDouble(@NotNull String s) {
        long integerPart = 0;
        long decimalPart = 0;
        long divisor = 1;

        boolean decimal = false;

        for (char c : s.toCharArray()) {

            if (c == '.') {
                decimal = true;
                continue;
            }

            int digit = c - '0';

            if (!decimal) {
                integerPart = integerPart * 10 + digit;
            } else {
                decimalPart = decimalPart * 10 + digit;
                divisor *= 10;
            }
        }

        return integerPart + (double) decimalPart / divisor;
    }

    protected boolean savePendingRequest(@NotNull String wwsURL, @NotNull String content) {
        WebSocketContainer container = webSockets.computeIfAbsent(wwsURL, WebSocketContainer::new);
        if (container.isOpen()) {
            return false;
        } else {
            container.getPendingRequest().add(content);
            return true;
        }
    }

    protected void checkResponse(@NotNull JsonNode response, @NotNull HttpRequest request) throws ApiException {
        if (response.has("code")) {
            int code = response.get("code").asInt();
            if (code != 200) throw new DefaultApiException("Error: Code=%d Message=%s Request=%s Method=%s".formatted(code, response.get("msg").asText(), request.uri().toString(), request.method()), request);
        }
    }

    protected void removeConsumerStreams(@NotNull String key) {
        consumerStreamsMap.remove(key);
    }

    protected void addConsumerStreams(@NotNull String key, @NotNull Consumer<JsonNode> consumer, boolean muliThreading) {
        if (muliThreading) {
            consumerStreamsMap.put(key, (json) -> executor.execute(() -> consumer.accept(json)));
        }else {
            consumerStreamsMap.put(key, consumer);
        }
    }

    protected abstract void handleStreamRawExpress(@NotNull String wwsURL, @NotNull String contentToParse);

    protected abstract void handleStreamRaw(@NotNull String wwsURL, @NotNull JsonNode node);

    @Deprecated // No es un método muy genérico para estar aqui
    protected abstract void subscribeBookTickerBatch(@NotNull List<String> symbols);

    protected abstract @Nullable String getPingPayload(@NotNull String wwsURL);

    protected abstract @NotNull Set<String> getBlackListEndpointLog();

    public abstract @NotNull String sGetHTTPS();

    public abstract @NotNull String sGetWWS();

    public enum Method {
        GET,
        POST,
        PUT,
        DELETE
    }

    @Data
    public abstract static class Keys{
        @NotNull private final String key;
        @NotNull private final String secret;
    }

    @Data
    public static class WebSocketContainer{
        private final String wwsURL;
        @Nullable
        private WebSocket webSocket = null;
        @Nullable
        private String lastRequest = null;
        private final List<String> pendingRequest = Collections.synchronizedList(new LinkedList<>());

        public boolean isClosed(){
            return webSocket == null || webSocket.isInputClosed() || webSocket.isOutputClosed();
        }

        public boolean isOpen(){
            return !isClosed();
        }
    }

    @Builder
    @Data
    public static class ConnectorConfig{
        @Builder.Default private int maxStreamsPerSubscribe = 200;
        @Builder.Default private long cooldownMsPerRequest = 1_000;
        @Builder.Default private boolean isTestNet = true;
        @Builder.Default private boolean logsRequest = false;
    }
}
