package dev.cerez.titan.connector.connectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.cerez.titan.connector.BaseConnector;
import dev.cerez.titan.connector.exception.ApiException;
import dev.cerez.titan.connector.exception.DefaultApiException;
import dev.cerez.titan.connector.exception.NotSetApiKeysException;
import dev.cerez.titan.connector.exception.exchange.*;
import dev.cerez.titan.connector.model.*;
import dev.cerez.titan.connector.model.order.OrderFuture;
import dev.cerez.titan.connector.model.order.OrderSpotResult;
import dev.cerez.titan.connector.model.order.SpotOrder;
import dev.cerez.titan.core.strategy.grid.model.SidePosition;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.utils.Provider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class BinanceConnector extends BaseConnector {

    private static final String BASE_SPOT_HTTPS = "https://api.binance.com";
    private static final String BASE_SPOT_HTTPS_TESTNET = "https://testnet.binance.vision";

    private static final String BASE_FUTURE_HTTPS = "https://fapi.binance.com";
    private static final String BASE_FUTURE_HTTPS_TESTNET = "https://testnet.binancefuture.com";

    private static final String BASE_SPOT_WWS = "wss://ws-api.binance.com:443/ws-api/v3";
    private static final String BASE_SPOT_WWS_TESTNET = "wss://demo-ws-api.binance.com:443/ws-api/v3";

    private static final String BASE_FUTURE_WWS = "wss://ws-fapi.binance.com/ws-fapi/v1";
    private static final String BASE_FUTURE_WWS_TESTNET = "wss://testnet.binancefuture.com/ws-fapi/v1";

    private static final String BASE_SPOT_WWS_STREAM = "wss://stream.binance.com:9443/stream";
    private static final String BASE_SPOT_WWS_STREAM_TESTNET = "wss://demo-stream.binance.com:9443/stream";

    private static final String BASE_FUTURE_WWS_STREAM = "wss://fstream.binance.com/stream";
    private static final String BASE_FUTURE_WWS_USERDATA = "wss://fstream.binance.com/private/ws/%s";

    private String listenKey = null;

    public BinanceConnector(@NotNull StorageManager storageManager) {
        super(storageManager.getConfig(ConnectorConfig.class).getProvider());
    }

    public BinanceConnector(ConnectorConfig config) {
        super(Provider.from(config));
    }

    public BinanceConnector() {
        super(Provider.from(
                BaseConnector.ConnectorConfig.builder()
                        .maxStreamsPerSubscribe(100)
                        .build()
        ));
    }

    @Override
    public void invalidateCache(){
        fCachedSymbols.clear();
        super.invalidateCache();
    }

    @SneakyThrows
    @Override
    protected void handleStreamRawExpress(@NotNull String wwsURL, @NotNull String contentToParse) {
        String[] split = contentToParse.split("\"");
        // Parsing express del book
        if (split.length == 29) {
            BookTickDouble bookTickDouble = new BookTickDouble(
                    split[11],
                    fastParseDouble(split[15]),
                    fastParseDouble(split[19]),
                    fastParseDouble(split[23]),
                    fastParseDouble(split[27])
            );
            if (this.consumerBookTicker != null) this.consumerBookTicker.accept(bookTickDouble);
            return;
        }

        // Longitud del Pong
        if (25 == split.length && telemetry != null) {
            waitingForPong = false;
            telemetry.setCurrentDeltaDelayPingPongNanoTime(System.nanoTime() - delayPingPongNanoTime);
        }
    }

    @Override
    protected void handleStreamRaw(@NotNull String wwsURL, @NotNull JsonNode node) {
        // Es un Streams
        {
            String stream;
            if (node.has("stream")) {
                stream = node.get("stream").asText();
            } else {
                stream = "";
            }
            String key = wwsURL + (stream.isEmpty() ? "" : "/") + stream;

            Collection<Consumer<JsonNode>> consumerSet = consumerStreamsMap.computeIfAbsent(key, k -> new HashMap<>()).values();
            if (!consumerSet.isEmpty()) {
                synchronized (consumerSet) {
                    if (node.has("data")) {
                        consumerSet.forEach(c -> c.accept(node.get("data")));
                    } else {
                        consumerSet.forEach(c -> c.accept(node));
                    }
                }
                return;
            }
        }
        // Es una repuesta de una solicitud
        {
            JsonNode nodeId = node.get("id");
            if (nodeId != null) {
                Consumer<JsonNode> consumer = webSockets.get(wwsURL).getPendingResponse().remove(nodeId.asText());
                if (consumer != null) consumer.accept(node);
            }
        }
    }

    @Override
    protected void internalStart(){
        initWebSocket(config.isTestNet() ? BASE_SPOT_WWS_TESTNET : BASE_SPOT_WWS);
        initWebSocket(this.fGetWWStream());
        initWebSocket(this.fGetWWS());
        initWebSocket(this.sGetWWS());
        super.internalStart();
        if (!config.isTestNet()) fStartUserData();
        initWebSocket(this.uGetWWS());
    }

    @Override
    protected void internalStop(){
        if (listenKey != null) {
            fCloseUserData();
        }
        super.internalStop();
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public @NotNull Map<String, Symbol> sGetAllSymbols() {
        JsonNode raw = sendPublicRequest(Method.GET, "/api/v1/exchangeInfo", new HashMap<>());
        HashMap<String, Symbol> symbols = new HashMap<>();
        for (JsonNode node : raw.get("rateLimits")) {
            if (node.get("rateLimitType").asText().equals("REQUEST_WEIGHT")) {
                rateLimitSpot.setMax(node.get("limit").asInt());
            }
        }
        for (JsonNode node : raw.get("symbols")) {
            BigDecimal stepsize = BigDecimal.ZERO;
            BigDecimal minNotionalQuote = BigDecimal.ZERO;
            BigDecimal minNotionalBase = BigDecimal.ZERO;
            BigDecimal stepPrice = BigDecimal.ZERO;
            for (JsonNode filters : node.get("filters")) {
                JsonNode type = filters.get("filterType");
                if ("LOT_SIZE".equals(type.asText())) {
                    stepsize = new BigDecimal(filters.get("stepSize").asText());
                }
                if ("NOTIONAL".equals(type.asText())) {
                    minNotionalQuote = new BigDecimal(filters.get("minNotional").asText());
                }
                if ("PRICE_FILTER".equals(type.asText())) {
                    stepPrice = new  BigDecimal(filters.get("tickSize").asText());
                }
            }

            symbols.put(node.get("symbol").asText(), new Symbol(
                    node.get("symbol").asText(),
                    node.get("quotePrecision").asInt(),
                    node.get("baseAsset").asText(),
                    node.get("quoteAsset").asText(),
                    "TRADING".equals(node.get("status").asText()),
                    stepPrice,
                    stepsize,
                    minNotionalQuote,
                    minNotionalBase,
                    ContractType.SPOT
            ));
        }
        cachedSymbols.set(symbols);
        return symbols;
    }

    @Override
    public @NotNull Map<String, BookTickDouble> sGetAllBooks() {
        Map<String, BookTickDouble> result = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("symbolStatus", "TRADING");
        JsonNode raw = sendPublicRequest(Method.GET, "/api/v3/ticker/bookTicker", params);
        for (JsonNode node : raw) {
            String symbol = node.get("symbol").asText();
            result.put(symbol,
                    new BookTickDouble(
                            symbol,
                            node.get("bidPrice").asDouble(),
                            node.get("bidQty").asDouble(),
                            node.get("askPrice").asDouble(),
                            node.get("askQty").asDouble()
                    )
            );
        }
        return result;
    }

    @Override
    public @NotNull Map<String, Volume> sGetAllVolume() {
        Map<String, Volume> result = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("type", "MINI");
        params.put("symbolStatus", "TRADING");
        JsonNode raw = sendPublicRequest(Method.GET, "/api/v3/ticker/24hr", params);
        for (JsonNode node : raw) {
            String s = node.get("symbol").asText();
            result.put(s, new Volume(
                    s,
                    new BigDecimal(node.get("quoteVolume").asText()),
                    new BigDecimal(node.get("volume").asText())
            ));
        }
        return result;
    }

    @Override
    public @NotNull Map<String, BigDecimal> sGetBalance() {
        JsonNode raw = sendSignedRequest(Method.GET, "/api/v3/account", new TreeMap<>());
        Map<String, BigDecimal> result = new HashMap<>();
        for (JsonNode node : raw.get("balances")) {
            BigDecimal balance =  new BigDecimal(node.get("free").asText());
            result.put(node.get("asset").asText(), balance);
        }
        return result;
    }

    @Override
    public OrderSpotResult sSendOrderToMkt(
            @NotNull String symbol,
            @NotNull SideOrder sideOrder,
            @NotNull BigDecimal amount,
            @Nullable String nameOrder,
            boolean amountInBaseAsset
    ) {
        symbol = symbol.toUpperCase(Locale.US);
        var symbolInfo = cachedSymbols.getOrCompute().get(symbol);

        Map<String, Object> params = new HashMap<>();
        params.put("symbol", symbol);
        params.put("side", sideOrder.name());
        params.put("type", "MARKET");
        params.put("newOrderRespType", "FULL");

        if (amountInBaseAsset) params.put("quantity", symbolInfo.roundBaseQuantity(amount));
        else params.put("quoteOrderQty", symbolInfo.roundQuoteQuantity(amount));

        JsonNode node = sendSignedRequest(Method.POST, "/api/v3/order", params);
        String orderId = node.get("orderId").asText();
        BigDecimal executedQty = new BigDecimal(node.get("executedQty").asText());
        BigDecimal cumulativeQuoteQty = new BigDecimal(node.get("cummulativeQuoteQty").asText());

        // BUY recibe el activo base; SELL recibe el activo cotizado.
        BigDecimal receivedQty = sideOrder == SideOrder.BUY ? executedQty : cumulativeQuoteQty;
        String receivedAsset = sideOrder == SideOrder.BUY ? symbolInfo.getBaseAsset() : symbolInfo.getQuoteAsset();
        BigDecimal totalCommission = BigDecimal.ZERO;

        for (JsonNode fill : node.get("fills")) {
            String commissionAsset = fill.get("commissionAsset").asText();
            if (receivedAsset.equals(commissionAsset)) {
                totalCommission = totalCommission.add(
                        new BigDecimal(fill.get("commission").asText())
                );
            }
        }

        receivedQty = receivedQty.subtract(totalCommission);

        return new OrderSpotResult(
                orderId,
                executedQty,
                cumulativeQuoteQty,
                receivedQty.max(BigDecimal.ZERO)
        );
    }
    @Override
    public @NotNull Long getTimeSever() {
        return sendPublicRequest(Method.GET, "/api/v3/time").get("serverTime").asLong();
    }

    @Override
    public void wsSubscribeBookTickerLegacy(@NotNull Consumer<BookTickDouble> consumer, @NotNull Collection<String> symbols) {
        consumerBookTicker = consumer;
        splitStream(symbols, symbol -> {
            if (symbol.isEmpty()) {
                return;
            }
            UUID uuid = UUID.randomUUID();
            String params = symbol.stream()
                    .map(s -> "\"" + s.toLowerCase(Locale.US) + "@bookTicker\"")
                    .collect(Collectors.joining(","));
            String json = """
                {"method": "SUBSCRIBE","params": [%s],"id": "%s"}
                """.formatted(params, uuid.toString().replace("-", ""));

            sendWebSocket(json);
        });
    }

    @Override
    public void wsUnsubscribeBookTickerLegacy(@NotNull Consumer<BookTickDouble> listener) {

    }

    @Override
    public @NotNull String sGetHTTPS() {
        return config.isTestNet() ? BASE_SPOT_HTTPS_TESTNET : BASE_SPOT_HTTPS;
    }

    @Override
    public @NotNull String sGetWWS() {
        return config.isTestNet() ? BASE_SPOT_WWS_STREAM_TESTNET : BASE_SPOT_WWS_STREAM;
    }

    @Override
    protected void checkResponse(@NotNull JsonNode response, @NotNull HttpRequest request) throws ApiException {
        if (response.has("code")) {
            int code = response.get("code").asInt();
            String msg = "Error: Code=%d Message=%s Request=%s Method=%s".formatted(code, response.get("msg").asText(), request.uri().toString(), request.method());
            if (code != 200) switch (code) {
                case -2011 -> throw new UnknownOrderException(code, msg, request);
                case -2019 -> throw new MarginNotSufficienException(code, msg, request);
                case -2022 -> throw new ReduceOnlyRejectException(code, msg, request);
                case -5022 -> throw new PostOnlyRejectException(code, msg, request);
                case -3045 -> throw new SystemNotEnoughAssetException(code, msg, request);
                default -> throw new ExchangeDefaultApiException(code, msg, request);
            }
        }
    }

    @Override
    @Contract("_, _, _ -> new")
    protected synchronized @NotNull WebSocketSubscribe subscribeStream(String url, Consumer<JsonNode> consumer, String stream) {
        UUID id = UUID.randomUUID();
        streams.put(id, stream);
        if (addConsumerStreams(createKey(url, stream), id, consumer))
            sendWebSocket(url, """
                    {"method":"SUBSCRIBE","params":["%s"],"id":"%s"}
                    """.formatted(stream, id.toString().replace("-", "")));
        return new WebSocketSubscribe(id, url, this::unsubscribeStream);
    }

    @Override
    protected synchronized void unsubscribeStream(@NotNull String url, @NotNull UUID id) {
        String stream = streams.get(id);
        if (removeConsumerStreams(createKey(url, stream), id))
            sendWebSocket(url, """
                    {"method":"UNSUBSCRIBE","params":["%s"],"id":"%s"}
                    """.formatted(stream, UUID.randomUUID().toString().replace("-", "")));
    }

    /////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////

    public void wTransfer(@Nullable String symbol, @NotNull Transfer transfer, @NotNull String asset, @NotNull BigDecimal amount) {
        Map<String, Object> params0 = new HashMap<>();
        params0.put("asset", asset.toUpperCase(Locale.US));
        params0.put("amount", amount);
        params0.put("type", transfer.getMethod());
        if (transfer == Transfer.MARGIN_TO_ISOLATED){
            Objects.requireNonNull(symbol);
            params0.put("toSymbol", symbol);
        }
        if (transfer == Transfer.ISOLATED_TO_MARGIN){
            Objects.requireNonNull(symbol);
            params0.put("fromSymbol", symbol);
        }
        BigDecimal prevBalance = switch (transfer){
            case FUTURE_TO_SPOT, MARGIN_TO_SPOT -> sGetBalance().get(asset);
            case ISOLATED_TO_MARGIN, SPOT_TO_MARGIN -> mcGetBalance(asset).free();
            case MARGIN_TO_ISOLATED -> miGetBalance(symbol).asset(asset).free();
            case SPOT_TO_FUTURE -> fGetBalanceAvailable().get(asset);
        };
        sendSignedRequest(Method.POST, "/sapi/v1/asset/transfer", params0);
//        int attempts = 0;
//        while (attempts++ < 10){
//            BigDecimal postBalance = switch (transfer){
//                case FUTURE_TO_SPOT, MARGIN_TO_SPOT -> sGetBalance().get(asset);
//                case MARGIN_TO_ISOLATED -> miGetBalance(symbol).asset(asset).free();
//                case ISOLATED_TO_MARGIN, SPOT_TO_MARGIN -> mcGetBalance(asset).free();
//                case SPOT_TO_FUTURE -> fGetBalance().get(asset);
//            };
//            if ((postBalance.subtract(prevBalance)).compareTo(amount) == 0){
//                break;
//            }
//            // ¿Sirve tener esto aquí???
//            prevBalance = postBalance;
//        }
    }

    @Getter
    @RequiredArgsConstructor
    public enum Transfer {
        SPOT_TO_FUTURE("MAIN_UMFUTURE"),
        FUTURE_TO_SPOT("UMFUTURE_MAIN"),
        SPOT_TO_MARGIN("MAIN_MARGIN"),
        MARGIN_TO_SPOT("MARGIN_MAIN"),
        MARGIN_TO_ISOLATED("MARGIN_ISOLATEDMARGIN"),
        ISOLATED_TO_MARGIN("ISOLATEDMARGIN_MARGIN");
        private final String method;
    }

    public BigDecimal sGetPrice(@NotNull String symbol) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("symbol", symbol.toUpperCase(Locale.US));
        return new BigDecimal(sendPublicRequest(Method.GET, "/api/v3/ticker/price", params).get("price").textValue());
    }

    public @Nullable SpotOrder sGetOrder(@NotNull String symbol, @NotNull String nameOrder) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("symbol", symbol.toUpperCase(Locale.US));
        JsonNode raw =  sendSignedRequest(Method.GET, "/api/v3/allOrders", params);
        for (JsonNode node : raw){
            if (node.get("clientOrderId").asText().equals(nameOrder)) {
                return new SpotOrder(
                        node.get("symbol").asText(),
                        Objects.requireNonNullElse(node.get("clientOrderId"), node.get("orderId")).asText(),
                        new BigDecimal(node.get("price").asText()),
                        new BigDecimal(node.get("baseAssetQty").asText()),
                        new BigDecimal(node.get("cummulativeQuoteQty").asText()),
                        SideOrder.valueOf(node.get("side").asText()),
                        false,
                        StatusOrder.parse(node.get("status").asText()),
                        node.get("time").asLong(),
                        node.get("updateTime").asLong()
                );
            }
        }
        return null;
    }

    @Contract("_ -> new")
    public @NotNull BookTick sGetBookTick(@NotNull String symbol) {
        Map<String, Object> params = new HashMap<>();
        params.put("symbol", symbol.toUpperCase(Locale.US));
        JsonNode node = sendPublicRequest(Method.GET, "/api/v3/depth", params);
        JsonNode bid = node.get("bids").iterator().next();
        JsonNode ask = node.get("asks").iterator().next();
        BigDecimal bidPrice = new BigDecimal(bid.iterator().next().asText());
        BigDecimal askPrice = new BigDecimal(ask.iterator().next().asText());
        return new BookTick(symbol.toUpperCase(Locale.US), bidPrice, BigDecimal.ZERO, askPrice, BigDecimal.ZERO);
    }

    public long sPing(){
        long start = System.nanoTime();
        sendPublicRequest(Method.GET, "/api/v3/ping");
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    public long wsPing(){
        long start = System.nanoTime();
        sendPublicWebSocketRequest(BASE_SPOT_WWS, "ping").join();
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    @Contract("_, _ -> new")
    public @NotNull BaseConnector.WebSocketSubscribe wsSubscribeBookTicker(@NotNull Consumer<BookTick> consumer, @NotNull String symbol){
        String stream = symbol.toLowerCase(Locale.US) + "@bookTicker";
        return subscribeStream(sGetWWS(), (payload) -> {
            String[] split = payload.toString().split("\"");
            consumer.accept(new BookTick(symbol.toLowerCase(Locale.US), new BigDecimal(split[9]), new BigDecimal(split[13]), new BigDecimal(split[17]), new BigDecimal(split[21])));
        }, stream);
    }

    public void wsUnsubscribeBookTicker(@NotNull UUID id) {
        String stream = streams.get(id);
        if (removeConsumerStreams(createKey(sGetWWS(), stream), id))
            sendWebSocket(sGetWWS(), """
                    {"method": "UNSUBSCRIBE","params": ["%s"],"id": "%s"}
                    """.formatted(stream, UUID.randomUUID().toString().replace("-", "")));
    }

    private @NotNull String fGetHTTPS(){
        return config.isTestNet() ? BASE_FUTURE_HTTPS_TESTNET : BASE_FUTURE_HTTPS;
    }

    public @NotNull String fGetWWStream(){
        return BASE_FUTURE_WWS_STREAM;
    }

    private @NotNull String fGetWWS() {
        return config.isTestNet() ? BASE_FUTURE_WWS_TESTNET : BASE_FUTURE_WWS;
    }

    public @NotNull String uGetWWS(){
        return BASE_FUTURE_WWS_USERDATA.formatted(listenKey);
    }

    public @NotNull Map<String, BigDecimal> fGetBalanceAvailable() {
        JsonNode raw = sendSignedRequest(fGetHTTPS(), Method.GET, "/fapi/v3/balance");
        Map<String, BigDecimal> result = new HashMap<>();
        for (JsonNode node : raw){
            BigDecimal balance =  new BigDecimal(node.get("maxWithdrawAmount").asText());
            result.put(node.get("asset").asText(), balance);
        }
        return result;
    }

    public @NotNull Map<String, BigDecimal> fGetBalanceValued() {
        JsonNode raw = sendSignedRequest(fGetHTTPS(), Method.GET, "/fapi/v3/balance");
        Map<String, BigDecimal> result = new HashMap<>();
        for (JsonNode node : raw){
            result.put(node.get("asset").asText(), new BigDecimal(node.get("balance").asText()));
        }
        return result;
    }

    @Contract(pure = true)
    public @NotNull BalanceFuture fGetBalance() {
        JsonNode raw = sendSignedRequest(fGetHTTPS(), Method.GET, "/fapi/v3/balance");
        Map<String, BigDecimal> valued = new HashMap<>();
        Map<String, BigDecimal> available = new HashMap<>();
        for (JsonNode node : raw){
            String asset = node.get("asset").asText();
            valued.put(asset, new BigDecimal(node.get("balance").asText()));
            available.put(asset, new BigDecimal(node.get("maxWithdrawAmount").asText()));
        }
        return new BalanceFuture(Map.copyOf(available), Map.copyOf(valued));
    }

    public @NotNull @Unmodifiable Map<String, BigDecimal> fGetUnPNL() {
        JsonNode raw = sendSignedRequest(fGetHTTPS(), Method.GET, "/fapi/v3/balance");
        Map<String, BigDecimal> result = new HashMap<>();
        for (JsonNode node : raw){
            result.put(node.get("asset").asText(), new BigDecimal(node.get("crossUnPnl").asText()));
        }
        return result;
    }

    public void fSendOrderToMkt(@NotNull String symbol, @NotNull SideOrder sideOrder, BigDecimal amountBase, @Nullable String nameOrder, boolean reduceOnly) throws ReduceOnlyRejectException {
        Map<String, Object> params = new HashMap<>();
        params.put("symbol", symbol.toUpperCase(Locale.US));
        params.put("side", sideOrder);
        params.put("type", "MARKET");
        params.put("quantity", fGetAllSymbols().get(symbol).roundBaseQuantity(amountBase));
        params.put("newOrderRespType", "RESULT");
        params.put("reduceOnly", reduceOnly);
        if (nameOrder != null) params.put("newClientOrderId", nameOrder);
        sendSignedRequest(fGetHTTPS(), Method.POST, "/fapi/v1/order", params);
    }

    public void fSendOrderToLimit(@NotNull String symbol,
                                  @NotNull SideOrder sideOrder, @NotNull BigDecimal amountBase,
                                  @Nullable String nameOrder,
                                  @NotNull BigDecimal price,
                                  boolean reduceOnly
    ) throws PostOnlyRejectException, ReduceOnlyRejectException, MarginNotSufficienException {
        Map<String, Object> params = new HashMap<>();
        Symbol s = fGetAllSymbols().get(symbol);
        params.put("symbol", symbol.toUpperCase(Locale.US));
        params.put("side", sideOrder);
        params.put("type", "LIMIT");
        params.put("quantity", s.roundBaseQuantity(amountBase));
        params.put("newOrderRespType", "RESULT");
        params.put("reduceOnly", reduceOnly);
        params.put("timeInForce", "GTX");
        params.put("price", s.roundPrice(price));
        if (nameOrder != null) params.put("newClientOrderId", nameOrder);
        sendSignedRequest(fGetHTTPS(), Method.POST, "/fapi/v1/order", params);
    }

    public void fCancelOrderAll(@NotNull String symbol) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("symbol", symbol.toUpperCase(Locale.US));
        sendSignedRequest(fGetHTTPS(), Method.DELETE, "/fapi/v1/allOpenOrders", params);
    }

    public void fCancelOrder(@NotNull String symbol, @NotNull String nameOrder) throws UnknownOrderException {
        HashMap<String, Object> params = new HashMap<>();
        params.put("symbol", symbol.toUpperCase(Locale.US));
        params.put("origClientOrderId", nameOrder);
        sendSignedRequest(fGetHTTPS(), Method.DELETE, "/fapi/v1/order", params);
    }

    public @Nullable BinanceConnector.FuturePosition fGetPosition(@NotNull String symbol) {
        Map<String, Object> params = new HashMap<>();
        params.put("symbol", symbol.toUpperCase(Locale.US));
        JsonNode raw = sendSignedRequest(fGetHTTPS(), Method.GET, "/fapi/v3/positionRisk", params);
        for (JsonNode node : raw) {
            String s = node.get("symbol").asText();
            if (s.equals(symbol.toUpperCase(Locale.US))) {
                return new FuturePosition(
                        s,
                        new BigDecimal(node.get("positionAmt").asText()),
                        new BigDecimal(node.get("entryPrice").asText()),
                        new BigDecimal(node.get("unRealizedProfit").asText())
                );
            }
        }
        return null;
    }

    public @NotNull List<BinanceConnector.FuturePosition> fGetPositions() {
        List<BinanceConnector.FuturePosition> result = new ArrayList<>();
        JsonNode raw = sendSignedRequest(fGetHTTPS(), Method.GET, "/fapi/v3/positionRisk");
        for (JsonNode node : raw) {
            result.add(new FuturePosition(
                    node.get("symbol").asText(),
                    new BigDecimal(node.get("positionAmt").asText()),
                    new BigDecimal(node.get("entryPrice").asText()),
                    new BigDecimal(node.get("unRealizedProfit").asText())
            ));
        }
        return result;
    }

    private final ConcurrentHashMap<String, Symbol> fCachedSymbols = new ConcurrentHashMap<>();

    @SuppressWarnings("DuplicatedCode")
    public @NotNull @Unmodifiable Map<String, Symbol> fGetAllSymbols() {
        if (!fCachedSymbols.isEmpty()){
            return Map.copyOf(fCachedSymbols);
        }
        Map<String, Symbol> symbols = new HashMap<>();
        JsonNode raw = sendPublicRequest(fGetHTTPS(), Method.GET, "/fapi/v1/exchangeInfo");
        for (JsonNode node : raw.get("rateLimits")) {
            if (node.get("rateLimitType").asText().equals("REQUEST_WEIGHT")) {
                rateLimitFuture.setMax(node.get("limit").asInt());
            }
        }
        for (JsonNode node : raw.get("symbols")) {
            BigDecimal stepsize = BigDecimal.ZERO;
            BigDecimal minNotionalQuote = BigDecimal.ZERO;
            BigDecimal minNotionalBase = BigDecimal.ZERO;
            BigDecimal stepPrice = BigDecimal.ZERO;
            for (JsonNode filters : node.get("filters")) {
                JsonNode type = filters.get("filterType");
                if ("LOT_SIZE".equals(type.asText())) {
                    stepsize = new BigDecimal(filters.get("stepSize").asText());
                    minNotionalBase = new BigDecimal(filters.get("minQty").asText());
                }
                if ("MIN_NOTIONAL".equals(type.asText())) {
                    minNotionalQuote = new BigDecimal(filters.get("notional").asText());
                }
                if ("PRICE_FILTER".equals(type.asText())) {
                    stepPrice = new  BigDecimal(filters.get("tickSize").asText());
                }

            }
            symbols.put(node.get("symbol").asText(), new Symbol(
                    node.get("symbol").asText(),
                    node.get("quotePrecision").asInt(),
                    node.get("baseAsset").asText(),
                    node.get("quoteAsset").asText(),
                    "TRADING".equals(node.get("status").asText()),
                    stepPrice,
                    stepsize,
                    minNotionalQuote,
                    minNotionalBase,
                    ContractType.valueOf(node.get("contractType").asText())
            ));
        }
        fCachedSymbols.clear();
        fCachedSymbols.putAll(symbols);
        return Map.copyOf(symbols);
    }

    public @NotNull List<OrderFuture> fGetAllOrder(@NotNull String symbol) {
        Map<String, Object> params = new HashMap<>();
        params.put("symbol", symbol.toUpperCase(Locale.US));
        JsonNode raw = sendSignedRequest(fGetHTTPS(), Method.GET, "/fapi/v1/allOrders", params);
        List<OrderFuture> orders = new LinkedList<>();
        for (JsonNode node : raw) {
            orders.add(new OrderFuture(
                    node.get("symbol").asText(),
                    node.get("clientOrderId").asText(),
                    new BigDecimal(node.get("price").asText()),
                    new BigDecimal(node.get("origQty").asText()),
                    SideOrder.valueOf(node.get("side").asText()),
                    node.get("reduceOnly").asBoolean(),
                    StatusOrder.parse(node.get("status").asText()),
                    node.get("time").asLong(),
                    node.get("updateTime").asLong()
            ));
        }
        return orders;
    }

    public long fPing(){
        long start = System.nanoTime();
        sendPublicRequest(fGetHTTPS(), Method.GET, "/fapi/v1/ping");
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    public void fSetLeverage(@NotNull String symbol, int leverage) {
        Map<String, Object> params = new HashMap<>();
        params.put("leverage", leverage);
        params.put("symbol", symbol.toUpperCase(Locale.US));
        sendSignedRequest(Method.POST, "/fapi/v1/leverage", params);
    }

    @Contract("_ -> new")
    public @NotNull BigDecimal fGetPrice(@NotNull String symbol) {
        Map<String, Object> params = new HashMap<>();
        params.put("symbol", symbol.toUpperCase(Locale.US));
        return new BigDecimal(sendPublicRequest(fGetHTTPS(), Method.GET, "/fapi/v1/premiumIndex", params).get("markPrice").asText());
    }

    @Contract("_, _ -> new")
    public @NotNull Volume fGetVolumen(@NotNull String symbol, @NotNull TimeUnitMarket timeUnit) {
        Map<String, Object> params = new HashMap<>();
        params.put("pair", symbol.toUpperCase(Locale.US));
        params.put("limit", 1);
        params.put("interval", timeUnit.getShortName());
        params.put("contractType", fGetAllSymbols().get(symbol).getContractType());
        JsonNode node = sendSignedRequest(fGetHTTPS(), Method.GET, "/fapi/v1/continuousKlines", params).iterator().next();
        return new Volume(
                symbol.toUpperCase(Locale.US),
                new BigDecimal(node.get(7).asText()),
                new BigDecimal(node.get(5).asText())
        );
    }

    @Contract(" -> new")
    public @NotNull Map<String, BigDecimal> fGetAllPrices() {
        JsonNode raw = sendPublicRequest(fGetHTTPS(), Method.GET, "/fapi/v1/premiumIndex");
        HashMap<String, BigDecimal> priceMap = new HashMap<>();
        for (JsonNode node : raw) {
            priceMap.put(node.get("symbol").asText(), new BigDecimal(node.get("markPrice").asText()));
        }
        return priceMap;
    }

    @Contract("_ -> new")
    public @NotNull BookTick fGetBookTick(@NotNull String symbol) {
        Map<String, Object> params = new HashMap<>();
        params.put("symbol", symbol.toUpperCase(Locale.US));
        params.put("limit", 5);
        JsonNode node = sendPublicRequest(fGetHTTPS(), Method.GET, "/fapi/v1/depth", params);
        JsonNode bid = node.get("bids").iterator().next();
        JsonNode ask = node.get("asks").iterator().next();
        BigDecimal bidPrice = new BigDecimal(bid.get(0).asText());
        BigDecimal askPrice = new BigDecimal(ask.get(0).asText());
        BigDecimal bidAmount = new BigDecimal(bid.get(1).asText());
        BigDecimal askAmount = new BigDecimal(ask.get(1).asText());
        return new BookTick(symbol.toUpperCase(Locale.US), bidPrice, bidAmount, askPrice, askAmount);
    }

    @Contract("_, _ -> new")
    public @NotNull BookTickMultyLayer fGetBookTick(@NotNull String symbol, int layer) {
        Map<String, Object> params = new HashMap<>();
        params.put("symbol", symbol.toUpperCase(Locale.US));
        int limit;
        if (layer <= 5) {
            limit = 5;
        }else if (layer <= 10) {
            limit = 10;
        }else if (layer <= 20) {
            limit = 20;
        }else if (layer <= 50) {
            limit = 50;
        }else if (layer <= 100) {
            limit = 100;
        }else if (layer <= 500) {
            limit = 500;
        }else  {
            limit = 1000;
        }
        params.put("limit", limit);
        JsonNode node = sendPublicRequest(fGetHTTPS(), Method.GET, "/fapi/v1/depth", params);
        ArrayList<BookTickMultyLayer.Layer> bids = new ArrayList<>(layer);
        ArrayList<BookTickMultyLayer.Layer> asks = new ArrayList<>(layer);
        for (int i = 0; i < layer; i++) {
            JsonNode bidsNode = node.get("bids").get(i);
            bids.add(new BookTickMultyLayer.Layer(
                    new BigDecimal(bidsNode.get(0).asText()),
                    new BigDecimal(bidsNode.get(1).asText())
            ));
            JsonNode asksNode = node.get("asks").get(i);
            asks.add(new BookTickMultyLayer.Layer(
                    new BigDecimal(asksNode.get(0).asText()),
                    new BigDecimal(asksNode.get(1).asText())
            ));
        }

        return new BookTickMultyLayer(symbol.toUpperCase(Locale.US), bids, asks);
    }


    public @NotNull Map<String, FundingRate> fGetFundingRate(){
        JsonNode raw0 = sendPublicRequest(fGetHTTPS(), Method.GET, "/fapi/v1/fundingInfo");
        Map<String, FundingConfig> result0 = new HashMap<>();
        for (JsonNode node : raw0) {
            String symbol = node.get("symbol").asText();
            result0.put(symbol,
                    new FundingConfig(node.get("adjustedFundingRateFloor").asDouble(),
                            node.get("adjustedFundingRateCap").asDouble(),
                            node.get("fundingIntervalHours").asInt()
                    )
            );
        }
        JsonNode raw1 = sendPublicRequest(fGetHTTPS(), Method.GET, "/fapi/v1/premiumIndex");
        Map<String, FundingRate> result1 = new HashMap<>();
        for (JsonNode node : raw1) {
            String symbol = node.get("symbol").asText();
            FundingConfig fundingConfig = result0.get(symbol);
            if (fundingConfig == null) {
                continue;
            }
            result1.put(symbol, new FundingRate(
                    symbol,
                    fundingConfig,
                    new BigDecimal(node.get("lastFundingRate").asText()),
                    node.get("nextFundingTime").asLong())
            );
        }
        return result1;
    }

    public @NotNull List<Transaction> fGetTransaction(long start, long end){
        Map<String, Object> params = new HashMap<>();
        params.put("startTime", start);
        params.put("endTime", end);
        params.put("limit", 500);
        JsonNode raw = sendSignedRequest(fGetHTTPS(), Method.GET, "/fapi/v1/income", params);
        List<Transaction> transactions = new LinkedList<>();
        for (JsonNode node : raw) {
            transactions.add(new Transaction(
                    node.get("symbol").asText(),
                    IncomeType.valueOf(node.get("incomeType").asText()),
                    new BigDecimal(node.get("income").asText()),
                    node.get("asset").asText(),
                    node.get("time").longValue()
            ));
        }
        return transactions;
    }

    public @NotNull Map<String, BigDecimal> fGetAllFundingRateHistory(){
        var mapAllSymbols = fGetAllSymbols();
        HashMap<String, BigDecimal> resultAllSymbols = new HashMap<>();
        HashMap<String, Object> params = new HashMap<>();
        params.put("limit", mapAllSymbols.size());
        JsonNode raw = sendPublicRequest(fGetHTTPS(), Method.GET, "/fapi/v1/fundingRate", params);
        for (JsonNode node : raw){
            resultAllSymbols.put(node.get("symbol").asText(), new BigDecimal(node.get("fundingRate").asText()));
        }
        return resultAllSymbols;
    }

    public @NotNull Map<String, BigDecimal> fGetAllFundingRateCurrent(){
        HashMap<String, BigDecimal> resultAllSymbols = new HashMap<>();
        JsonNode raw = sendPublicRequest(fGetHTTPS(), Method.GET, "/fapi/v1/premiumIndex");
        for (JsonNode node : raw){
            resultAllSymbols.put(node.get("symbol").asText(), new BigDecimal(node.get("lastFundingRate").asText()));
        }
        return resultAllSymbols;
    }

    public void fStartUserData(){
        listenKey = sendSignedRequest(fGetHTTPS(), Method.POST, "/fapi/v1/listenKey").get("listenKey").asText();
        executor.execute(() -> {
            while (listenKey != null) {
                fKeepLiveUserData();
                LockSupport.parkNanos(TimeUnit.MINUTES.toNanos(20));
            }
        });
    }

    public void fKeepLiveUserData(){
        sendSignedRequest(fGetHTTPS(), Method.PUT, "/fapi/v1/listenKey");
    }

    public void fCloseUserData(){
        this.listenKey = null;
        sendSignedRequest(fGetHTTPS(), Method.DELETE, "/fapi/v1/listenKey");
    }

    @Contract("_, _ -> new")
    public @NotNull BaseConnector.WebSocketSubscribe wfSubscribeBookTicker(@NotNull Consumer<BookTick> consumer, @NotNull String symbol) {
        String stream = symbol.toLowerCase(Locale.US) + "@bookTicker";
        return subscribeStream(fGetWWStream(), (payload -> {
            String[] split = payload.toString().split("\"");
            consumer.accept(new BookTick(symbol.toLowerCase(Locale.US), new BigDecimal(split[17]), new BigDecimal(split[21]), new BigDecimal(split[25]), new BigDecimal(split[29])));
        }), stream);
    }

    @Contract("_, _ -> new")
    public @NotNull BaseConnector.WebSocketSubscribe wfSubscribeCandle(@NotNull Consumer<Candle> consumer, @NotNull String symbol) {
        String stream = symbol.toLowerCase(Locale.US) + "@ticker";
        return subscribeStream(fGetWWStream(), (payload -> {
            consumer.accept(
                    new Candle(
                            payload.get("s").asText(),
                            new BigDecimal(payload.get("c").asText()),
                            new BigDecimal(payload.get("o").asText()),
                            new BigDecimal(payload.get("h").asText()),
                            new BigDecimal(payload.get("l").asText())
                    )
            );
        }), stream);
    }

    public @NotNull CompletableFuture<JsonNode> wfSendOrderToMkt(@NotNull String symbol, @NotNull SideOrder sideOrder, BigDecimal amountBase, @Nullable String nameOrder, boolean reduceOnly) throws ReduceOnlyRejectException {
        Map<String, Object> params = new HashMap<>();
        params.put("symbol", symbol.toLowerCase(Locale.US));
        params.put("side", sideOrder);
        params.put("quantity", fGetAllSymbols().get(symbol).roundBaseQuantity(amountBase));
        params.put("reduceOnly", reduceOnly);
        params.put("type", "MARKET");
        if (nameOrder != null) params.put("nameOrder", nameOrder);
        return sendSignedWebSocketRequest(fGetWWS(), "order.place", params);
    }

    public @NotNull BaseConnector.WebSocketSubscribe wuSubscribeEventOrderTradeUpdate(Consumer<OrderUpdate> consumer){
        String type = "ORDER_TRADE_UPDATE";
        UUID id = UUID.randomUUID();
        addConsumerStreams(uGetWWS(), id, (payload) -> {
            if (payload.get("e").asText().equals(type)) {
                JsonNode node = payload.get("o");
                StatusOrder statusOrder = StatusOrder.parse(node.get("x").asText());
                String nameOrder = node.get("c").asText();
                String symbol = node.get("s").asText();
                SideOrder sideOrder = SideOrder.valueOf(node.get("S").asText());

                consumer.accept(new OrderUpdate(nameOrder, symbol, sideOrder, statusOrder));
            }
        });
        return new WebSocketSubscribe(id, uGetWWS(), this::removeConsumerStreams);
    }

    public @NotNull BaseConnector.WebSocketSubscribe wuCreateEventAccountUpdate(Consumer<JsonNode> consumer){
        String type = "ACCOUNT_UPDATE";
        UUID id = UUID.randomUUID();
        addConsumerStreams(uGetWWS(), id, (payload) -> {
            if (payload.get("e").asText().equals(type)) {
                consumer.accept(payload);
            }
        });
        return new WebSocketSubscribe(id, uGetWWS(), this::removeConsumerStreams);
    }

    public boolean cPossibleConvert(@NotNull String fromAsset, @NotNull String toAsset) {
        Map<String, Object> params = new HashMap<>();
        params.put("fromAsset", fromAsset);
        params.put("toAsset", toAsset);
        JsonNode node = sendPublicRequest(Method.GET, "/sapi/v1/convert/exchangeInfo", params);
        return !node.isEmpty();
    }

    public @Nullable ConvertConfig cGetMinMaxConvert(@NotNull String fromAsset, @NotNull String toAsset) {
        Map<String, Object> params = new HashMap<>();
        params.put("fromAsset", fromAsset);
        params.put("toAsset", toAsset);
        JsonNode raw = sendPublicRequest(Method.GET, "/sapi/v1/convert/exchangeInfo", params);
        for (JsonNode node : raw){
            if (node.get("fromAsset").asText().equals(fromAsset) && node.get("toAsset").asText().equals(toAsset)){
                return new ConvertConfig(node.get("fromAssetMinAmount").asDouble(),
                        node.get("fromAssetMaxAmount").asDouble(),
                        node.get("toAssetMinAmount").asDouble(),
                        node.get("toAssetMaxAmount").asDouble()
                );
            }
        }
        return null;
    }

    public void cConvert(@NotNull String fromAsset, @NotNull String toAsset, BigDecimal amount, boolean fromAmount) {
        Map<String, Object> paramsRequest = new HashMap<>();
        paramsRequest.put("fromAsset", fromAsset);
        paramsRequest.put("toAsset", toAsset);
        paramsRequest.put("walletType", "SPOT");
        if (fromAmount) {
            paramsRequest.put("fromAmount", amount);
        }else {
            paramsRequest.put("toAmount", amount);
        }
        String id = sendSignedRequest(Method.POST, "/sapi/v1/convert/getQuote", paramsRequest).get("quoteId").asText();
        Map<String, Object> paramsAccept = new HashMap<>();
        paramsAccept.put("quoteId", id);
        sendSignedRequest(Method.POST, "/sapi/v1/convert/acceptQuote", paramsAccept);
    }

    public BigDecimal mGetInterest(@NotNull String asset) {
        Map<String, Object> params = new HashMap<>();
        params.put("assets", asset.toUpperCase(Locale.US));
        params.put("isIsolated", true);
        JsonNode raw = sendSignedRequest(Method.GET, "/sapi/v1/margin/next-hourly-interest-rate", params);
        for (JsonNode node : raw) {
            if (node.get("asset").asText().equals(asset.toUpperCase(Locale.US))) {
                return new BigDecimal(node.get("nextHourlyInterestRate").asText());
            }
        }
        return BigDecimal.ZERO;
    }

    public void mSetEnableInsolated(@NotNull String symbol, boolean enable) {
        Map<String, Object> params = new HashMap<>();
        params.put("symbol", symbol.toUpperCase(Locale.US));
        if (enable) {
            sendSignedRequest(Method.POST, "/sapi/v1/margin/isolated/account", params);
        } else {
            sendSignedRequest(Method.DELETE, "/sapi/v1/margin/isolated/account", params);
        }
    }

    public boolean mIsEnableInsolated(@NotNull String symbol) {
        Map<String, Object> params = new HashMap<>();
        params.put("symbols", symbol.toUpperCase(Locale.US));
        JsonNode raw = sendSignedRequest(Method.GET, "/sapi/v1/margin/isolated/account", params);
        for (JsonNode node : raw.get("assets")) {
            if (node.get("symbol").asText().equals(symbol.toUpperCase(Locale.US))) {
                return node.get("enabled").asBoolean();
            }
        }
        return false;
    }

    public boolean mIsAllowInsolated(@NotNull String symbol) {
        Map<String, Object> params = new HashMap<>();
        params.put("symbols", symbol.toUpperCase(Locale.US));
        JsonNode raw = sendSignedRequest(Method.GET, "/sapi/v1/margin/isolated/account", params);
        for (JsonNode node : raw.get("assets")) {
            if (node.get("symbol").asText().equals(symbol.toUpperCase(Locale.US))) {
                return node.get("tradeEnabled").asBoolean() && node.get("isolatedCreated").asBoolean();
            }
        }
        return false;
    }

    public BigDecimal mGetMaxLimitBorrowable(@Nullable String symbol, @NotNull String asset) {
        Map<String, Object> params = new HashMap<>();
        if (symbol != null) params.put("isolatedSymbol", symbol.toUpperCase(Locale.US));
        params.put("asset", asset.toUpperCase(Locale.US));

        try {
            JsonNode node = sendSignedRequest(Method.GET, "/sapi/v1/margin/maxBorrowable", params);
            double amount =  node.get("amount").asDouble();
            double limit = node.get("borrowLimit").asDouble();
            return new BigDecimal(Math.min(limit, amount)); // TODO: Cambiar esto
        } catch (ExchangeApiException e) {
            if (e.getCode() == -3045){
                return new BigDecimal("-1");
            }else{
                throw e;
            }
        }
    }

    @Contract("_, _ -> new")
    public @NotNull BigDecimal mGetMaxAmountBorrowable(@Nullable String symbol, @NotNull String asset) throws SystemNotEnoughAssetException {
        Map<String, Object> params = new HashMap<>();
        if (symbol != null) params.put("isolatedSymbol", symbol.toUpperCase(Locale.US));
        params.put("asset", asset.toUpperCase(Locale.US));
        JsonNode node = sendSignedRequest(Method.GET, "/sapi/v1/margin/maxBorrowable", params);
        return new BigDecimal(node.get("amount").asText());
    }


    public BigDecimal mGetBorrowed(@NotNull String symbol) {
        JsonNode raw = sendSignedRequest(Method.GET, "/sapi/v1/margin/isolated/account");
        for (JsonNode node : raw) {
            if (node.get("symbol").asText().equals(symbol.toUpperCase(Locale.US))) {
                JsonNode base = node.get("baseAsset");
                return new BigDecimal(base.get("borrowed").asText()).add(new BigDecimal(base.get("interest").asText()));
            }
        }
        return BigDecimal.ZERO;
    }

    public void mBorrow(@NotNull String symbol, @NotNull String asset, @NotNull BigDecimal amount) {
        Map<String, Object> params = new HashMap<>();
        params.put("symbol", symbol.toUpperCase(Locale.US));
        params.put("asset", asset.toUpperCase(Locale.US));
        params.put("amount", amount);
        params.put("isIsolated", true);
        params.put("type", "BORROW");
        sendSignedRequest(Method.POST, "/sapi/v1/margin/borrow-repay", params);
    }

    @Contract("_ -> new")
    public @NotNull BalanceInsolated miGetBalance(@NotNull String symbol) {
        Map<String, Object> params = new HashMap<>();
        params.put("symbols", symbol.toUpperCase(Locale.US));
        JsonNode raw = sendSignedRequest(Method.GET, "/sapi/v1/margin/isolated/account", params);
        for (JsonNode node : raw.get("assets")) {
            if (node.get("symbol").asText().equals(symbol.toUpperCase(Locale.US))) {
                JsonNode baseNode = node.get("baseAsset");

                AssetMargin base = new AssetMargin(
                        baseNode.get("asset").asText(),
                        new BigDecimal(baseNode.get("free").asText()),
                        new BigDecimal(baseNode.get("borrowed").asText()),
                        new BigDecimal(baseNode.get("interest").asText())
                );
                JsonNode quoteNode = node.get("quoteAsset");
                AssetMargin quote = new AssetMargin(
                        quoteNode.get("asset").asText(),
                        new BigDecimal(quoteNode.get("free").asText()),
                        new BigDecimal(quoteNode.get("borrowed").asText()),
                        new BigDecimal(quoteNode.get("interest").asText())
                );
                return new BalanceInsolated(base, quote);
            }
        }
        throw new NullPointerException("No such borrowed asset");
    }

    public @NotNull AssetMargin mcGetBalance(@NotNull String asset) {
        Map<String, Object> params = new HashMap<>();
        params.put("symbol", asset.toUpperCase(Locale.US));
        JsonNode raw = sendSignedRequest(Method.GET, "/sapi/v1/margin/account", params);
        for (JsonNode node : raw.get("userAssets")) {
            String a = node.get("asset").asText();
            if (a.equals(asset.toUpperCase(Locale.US))) {
                return new AssetMargin(
                        a,
                        new BigDecimal(node.get("free").asText()),
                        new BigDecimal(node.get("borrowed").asText()),
                        new BigDecimal(node.get("interest").asText())
                );
            }
        }
        return new AssetMargin(asset, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public void mSendOrderToMkt(@NotNull String symbol, @NotNull SideOrder sideOrder, BigDecimal amount, @NotNull String nameOrder, boolean amountInBaseAsset) {
        Map<String, Object> params = new HashMap<>();
        params.put("symbol", symbol.toUpperCase(Locale.US));
        params.put("side", sideOrder);
        params.put("newOrderRespType", "RESULT");
        params.put("type", "MARKET");
        if (amountInBaseAsset) {
            params.put("quantity", cachedSymbols.getOrCompute().get(symbol).roundBaseQuantity(amount));
        } else {
            params.put("quoteOrderQty", cachedSymbols.getOrCompute().get(symbol).roundQuoteQuantity(amount));
        }
        params.put("isIsolated", true);
        params.put("newClientOrderId", nameOrder);
        sendSignedRequest(Method.POST, "/sapi/v1/margin/order", params);
    }

    public void mRepay(@NotNull String symbol, @NotNull String asset, BigDecimal amount) {
        Map<String, Object> params = new HashMap<>();

        params.put("symbol", symbol.toUpperCase(Locale.US));
        params.put("asset", asset.toUpperCase(Locale.US));
        params.put("amount", amount);
        params.put("isIsolated", true);
        params.put("type", "REPAY");

        sendSignedRequest(
                Method.POST,
                "/sapi/v1/margin/borrow-repay",
                params
        );
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected @NotNull CompletableFuture<JsonNode> sendSignedWebSocketRequest(
            @NotNull String url,
            @NotNull String method,
            @NotNull Map<String, Object> params
    ) {
        if (config.getSecret() == null || config.getKey() == null || config.getSecret().isBlank() || config.getKey().isBlank()) {
            throw new NotSetApiKeysException("API Key not set");
        }

        // Parámetros obligatorios para una request firmada
        params.put("apiKey", config.getKey());
        params.put("timestamp", System.currentTimeMillis() + deltaClienteToServer);

        /*
         * Binance requiere que los parámetros utilizados para la firma
         * estén ordenados alfabéticamente por nombre.
         */
        TreeMap<String, Object> signingParams = new TreeMap<>(params);
        String queryString = buildQueryString(signingParams);

        try {
            String signature = hmacSha256(queryString, config.getSecret());

            UUID id = UUID.randomUUID();
            ObjectNode request = mapper.createObjectNode();
            request.put("id", id.toString());
            request.put("method", method);
            ObjectNode requestParams = mapper.createObjectNode();

            for (Map.Entry<String, Object> entry : params.entrySet()) {
                requestParams.set(
                        entry.getKey(),
                        mapper.valueToTree(entry.getValue())
                );
            }

            requestParams.put("signature", signature);
            request.set("params", requestParams);

            if (config.isLogsRequest()) {
                Log.info("Pr wws=%s", request);
            }

            sendWebSocket(url, request.toString());
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            webSockets.get(url).getPendingResponse().put(id.toString(), future::complete);
            return future;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected @NotNull CompletableFuture<JsonNode> sendPublicWebSocketRequest(
            @NotNull String url,
            @NotNull String method,
            @NotNull Map<String, Object> params
    ) {
        UUID id = UUID.randomUUID();
        ObjectNode request = mapper.createObjectNode();
        request.put("id", id.toString());
        request.put("method", method);
        ObjectNode requestParams = mapper.createObjectNode();

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            requestParams.set(
                    entry.getKey(),
                    mapper.valueToTree(entry.getValue())
            );
        }

        if (!params.isEmpty()) request.set("params", requestParams);
        if (config.isLogsRequest()) {
            Log.info("Pu wws=%s", request);
        }

        sendWebSocket(url, request.toString());
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        webSockets.get(url).getPendingResponse().put(id.toString(), future::complete);
        return future;
    }

    @Override
    protected @NotNull JsonNode sendPublicRequest(
            @NotNull String baseUrl,
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
        if (config.isLogsRequest()) Log.info( "Pu https=%s %s", method, finalUrl);
        String jsonRaw = null;
        if (telemetry != null) telemetry.addRequestConnector(method, finalUrl);
        try {
            HttpResponse<String> response = clientHttp.send(request, HttpResponse.BodyHandlers.ofString());
            onResponseHttps(finalUrl, response);
            jsonRaw = response.body();
            JsonNode node = mapper.readTree(jsonRaw);
            checkResponse(node, request);
            return node;
        } catch (IOException | InterruptedException | ApiException e) {
            Log.error(finalUrl + " @ " + jsonRaw);
            throw new RuntimeException(e);
        }
    }

    @Override
    protected @NotNull JsonNode sendSignedRequest(
            @NotNull String baseUrl,
            @NotNull Method method,
            @NotNull String endpoint,
            @NotNull Map<String, Object> params
    ) {
        if (config.getSecret() == null || config.getKey() == null || config.getSecret().isBlank() || config.getKey().isBlank()) {
            throw new NotSetApiKeysException("API Key not set");
        }
        params.put("timestamp", System.currentTimeMillis() + deltaClienteToServer);
        String queryString = buildQueryString(params);
        try {
            final String signature = hmacSha256(queryString, config.getSecret());
            final String finalUrl = baseUrl + endpoint + "?" + queryString + "&signature=" + signature;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(finalUrl))
                    .header("X-MBX-APIKEY", config.getKey())
                    .method(method.name(), HttpRequest.BodyPublishers.noBody())
                    .build();
            if (config.isLogsRequest()) Log.info("Pr https=%s %s", method, finalUrl);
            JsonNode jsonRaw = null;
            try {
                HttpResponse<String> response = clientHttp.send(request, HttpResponse.BodyHandlers.ofString());
                onResponseHttps(finalUrl, response);
                checkResponse(jsonRaw = mapper.readTree(response.body()), request);
                return jsonRaw;
            } catch (IOException | InterruptedException e) {
                Log.exception("Error de IO o Interrupción: %s".formatted(e.getMessage()), e);
                throw new RuntimeException(e);
            }catch (DefaultApiException e) {
                Log.error("%s %s -> %s", method.name(), finalUrl, jsonRaw);
                throw e;
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private void onResponseHttps(@NotNull String url, @NotNull HttpResponse<String> response) {
        if (url.startsWith(fGetHTTPS())) {
            response.headers().firstValueAsLong("X-MBX-USED-WEIGHT-1M").ifPresent(rateLimitFuture::setUse);
        }
        if (url.startsWith(sGetHTTPS())) {
            response.headers().firstValueAsLong("X-MBX-USED-WEIGHT-1M").ifPresent(rateLimitSpot::setUse);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public record OrderUpdate(String name, String symbol, SideOrder sideOrder, StatusOrder statusOrder) {}

    public record FuturePosition(@NotNull String symbol, @NotNull BigDecimal quantity, @NotNull BigDecimal entryPriceAvg, @NotNull BigDecimal pnlUnrealize) {
        @Contract(pure = true)
        public @NotNull SidePosition sidePosition() {
            if (quantity.signum() == 1) return SidePosition.LONG;
            else return SidePosition.SHORT;
        }
    }

    public record AssetMargin(String asset, BigDecimal free, BigDecimal borrowed, BigDecimal interest) {
        @Contract(pure = true)
        public @NotNull BigDecimal totalRepay(){
            return borrowed.add(interest);
        }
    }

    public record BalanceInsolated(AssetMargin base, AssetMargin quote) {
        @Contract(pure = true)
        public @NotNull String symbol() {
            return base.asset + quote.asset;
        }

        public AssetMargin asset(String asset) {
            if (base.asset.equals(asset)) {
                return base;
            }else return quote;
        }
    }

    public record BalanceFuture(@NotNull @Unmodifiable Map<String, BigDecimal> available, @NotNull @Unmodifiable Map<String, BigDecimal> valued) {}

    public record FundingRate(String symbol, FundingConfig config, BigDecimal nextFundingRate, long nextFundingTime) {
        public @NotNull BigDecimal rate24h(){
            return nextFundingRate.multiply(BigDecimal.valueOf(24d / config.interval()));
        }

        public @NotNull BigDecimal rate24hAbs(){
            return rate24h().abs();
        }

        public @NotNull BigDecimal nextFundingRateAbs() {
            return nextFundingRate.abs();
        }
    }

    public record Transaction(@NotNull String symbol, @NotNull IncomeType incomeType, BigDecimal amount, String asset, long date) {}

    public enum IncomeType{
        TRANSFER,
        WELCOME_BONUS,
        REALIZED_PNL,
        FUNDING_FEE,
        COMMISSION,
        INSURANCE_CLEAR,
        REFERRAL_KICKBACK,
        COMMISSION_REBATE,
        API_REBATE,
        CONTEST_REWARD,
        CROSS_COLLATERAL_TRANSFER,
        OPTIONS_PREMIUM_FEE,
        OPTIONS_SETTLE_PROFIT,
        INTERNAL_TRANSFER,
        AUTO_EXCHANGE,
        DELIVERED_SETTELMENT,
        COIN_SWAP_DEPOSIT,
        COIN_SWAP_WITHDRAW,
        POSITION_LIMIT_INCREASE_FEE,
        STRATEGY_UMFUTURES_TRANSFER,
        FEE_RETURN,
        BFUSD_REWARD,
        SPECIAL_FUNDING_FEE
    }
}
