package dev.cerez.titan;

import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.utils.Switch;
import dev.cerez.titan.utils.Utils;
import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Getter
@SpringBootApplication
public class TitanApplication implements Switch {

    private static final TitanApplication INSTANCE = new TitanApplication();

    @Getter(AccessLevel.NONE)
    private ConfigurableApplicationContext context = null;
    private boolean running = false;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(16, Utils.getThreadFactory(this));
    private final Map<UUID, EnvironmentManager> environmentManagers = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        INSTANCE.start();
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        Log.info("""
                                                          ___           ___    \s
                      ___       ___           ___        /  /\\         /__/\\   \s
                     /  /\\     /  /\\         /  /\\      /  /::\\        \\  \\:\\  \s
                    /  /:/    /  /:/        /  /:/     /  /:/\\:\\        \\  \\:\\ \s
                   /  /:/    /__/::\\       /  /:/     /  /:/~/::\\   _____\\__\\:\\\s
                  /  /::\\    \\__\\/\\:\\__   /  /::\\    /__/:/ /:/\\:\\ /__/::::::::\\
                 /__/:/\\:\\      \\  \\:\\/\\ /__/:/\\:\\   \\  \\:\\/:/__\\/ \\  \\:\\~~\\~~\\/
                 \\__\\/  \\:\\      \\__\\::/ \\__\\/  \\:\\   \\  \\::/       \\  \\:\\  ~~~\s
                      \\  \\:\\     /__/:/       \\  \\:\\   \\  \\:\\        \\  \\:\\    \s
                       \\__\\/     \\__\\/         \\__\\/    \\  \\:\\        \\  \\:\\   \s
                                                         \\__\\/         \\__\\/   \s
                """);

        context = SpringApplication.run(TitanApplication.class, "");
        Log.configure();
        getEnvironmentManager(Utils.getRootId());
    }

    public EnvironmentManager getEnvironmentManager(UUID idUser) {
        return this.environmentManagers.computeIfAbsent(idUser, EnvironmentManager::create);
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        this.environmentManagers.values().forEach(EnvironmentManager::stop);
        SpringApplication.exit(context);
        System.exit(0);
    }

    @Contract(pure = true)
    @NotNull
    @Bean
    @Primary
    public static TitanApplication getInstance(){
        return INSTANCE;
    }

//    @RequiredArgsConstructor
//    public static final class FundingTelemetryCollector implements AutoCloseable {
//
//        // 0.005% expresado como tasa decimal.
//        private static final BigDecimal MIN_FUNDING_RATE = new BigDecimal("0.00005");
//        private static final long PRE_FUNDING_MS = 10_000L;
//
//        // Se espera un poco después del funding para que cierre
//        // la vela de un segundo.
//        private static final long POST_FUNDING_MS = 1_200L;
//        private static final long FUNDING_REFRESH_SECONDS = 60L;
//
//        private static final System.Logger LOG = System.getLogger(FundingTelemetryCollector.class.getName());
//        private final Path jsonFile;
//
//        private final TimeUnitMarket secondUnit = TimeUnitMarket.ONE_SECOND;;
//        private final TimeUnitMarket minuteUnit = TimeUnitMarket.ONE_MINUTE;;
//        private final TimeUnitMarket hourUnit = TimeUnitMarket.ONE_HOUR;
//
//        private final Gson gson = new GsonBuilder()
//                .setPrettyPrinting()
//                .create();
//
//        private final Type listType =
//                new TypeToken<List<GridCommand.FundingTelemetry>>() {}.getType();
//
//        private final ScheduledExecutorService scheduler =
//                Executors.newScheduledThreadPool(4);
//
//        private final AtomicBoolean initialized = new AtomicBoolean(false);
//
//        private final Object fileLock = new Object();
//
//        private final List<GridCommand.FundingTelemetry> telemetry = new ArrayList<>();
//
//        // Evita programar el mismo funding varias veces.
//        private final Set<String> scheduledEvents = ConcurrentHashMap.newKeySet();
//
//        // Evita volver a almacenar eventos que ya están en el JSON.
//        private final Set<String> completedEvents = ConcurrentHashMap.newKeySet();
//        private volatile BinanceConnector connector;
//
//        /**
//         * Inicializa el recopilador, carga el JSON y comienza
//         * a detectar próximos eventos de funding.
//         */
//        public void initialize(BinanceConnector connector) throws IOException {
//
//            if (!initialized.compareAndSet(false, true)) {
//                throw new IllegalStateException(
//                        "FundingTelemetryCollector ya fue inicializado"
//                );
//            }
//
//            this.connector = Objects.requireNonNull(connector);
//
//            loadJson();
//
//            // Primera consulta inmediata.
//            refreshFundingRates();
//
//            // Detecta nuevos horarios de funding.
//            scheduler.scheduleAtFixedRate(
//                    this::refreshFundingSafely,
//                    FUNDING_REFRESH_SECONDS,
//                    FUNDING_REFRESH_SECONDS,
//                    TimeUnit.SECONDS
//            );
//
//            LOG.log(System.Logger.Level.INFO,
//                    "FundingTelemetryCollector iniciado. Registros cargados: "
//                            + telemetry.size());
//        }
//
//
//        private void refreshFundingSafely() {
//            try {
//                refreshFundingRates();
//            } catch (Exception e) {
//                LOG.log(System.Logger.Level.ERROR,
//                        "Error consultando FundingRate", e);
//            }
//        }
//
//
//        /**
//         * Consulta los funding disponibles y programa los eventos
//         * que cumplen los filtros.
//         */
//        private void refreshFundingRates() {
//
//            Map<String, FundingRate> fundingRates = connector.fGetFundingRate();
//
//            long now = System.currentTimeMillis();
//
//            for (FundingRate funding : fundingRates.values()) {
//                if (funding == null
//                        || funding.symbol() == null
//                        || funding.nextFundingRate() == null) {
//                    continue;
//                }
//
//                String symbol = funding.symbol().toUpperCase(Locale.ROOT);
//
//                // Solo contratos cuyo activo de cotización es USDT.
//                if (!symbol.endsWith("USDT")) {
//                    continue;
//                }
//
//                // Funding positivo o negativo, fuera del umbral.
//                if (funding.nextFundingRate()
//                        .abs()
//                        .compareTo(MIN_FUNDING_RATE) < 0) {
//                    continue;
//                }
//
//                long fundingTime = funding.nextFundingTime();
//
//                // No recopilar eventos que ya han ocurrido.
//                if (fundingTime <= now) {
//                    continue;
//                }
//
//                String eventKey = getEventKey(symbol, fundingTime);
//
//                if (completedEvents.contains(eventKey)) {
//                    continue;
//                }
//
//                if (!scheduledEvents.add(eventKey)) {
//                    continue;
//                }
//
//                long snapshotTime = fundingTime - PRE_FUNDING_MS;
//                long delay = Math.max(0L, snapshotTime - now);
//
//                LOG.log(System.Logger.Level.INFO,
//                        "Funding programado: " + symbol
//                                + " | rate=" + funding.nextFundingRate()
//                                + " | interval=" + funding.config().interval()
//                );
//
//                scheduler.schedule(
//                        () -> collectBeforeFunding(funding, eventKey, 0),
//                        delay,
//                        TimeUnit.MILLISECONDS
//                );
//            }
//        }
//
//
//        /**
//         * Recopila los datos 10 segundos antes del funding.
//         */
//        private void collectBeforeFunding(
//                FundingRate funding,
//                String eventKey,
//                int attempt
//        ) {
//
//            try {
//                String symbol = funding.symbol();
//
//                // Una sola petición para obtener los primeros 6 niveles.
//                BookTickMultyLayer book =
//                        connector.fGetBookTick(symbol, 6);
//
//                // Velumen de las velas justo antes del funding.
//                BigDecimal volume1Hour =
//                        getLatestQuoteVolume(symbol, hourUnit);
//
//                BigDecimal volume1Minute =
//                        getLatestQuoteVolume(symbol, minuteUnit);
//
//                Snapshot snapshot = new Snapshot(
//                        funding,
//                        getQuoteAmount(book, 2),
//                        getQuoteAmount(book, 4),
//                        getQuoteAmount(book, 6),
//                        volume1Hour,
//                        volume1Minute
//                );
//
//                long targetTime =
//                        funding.nextFundingTime() + POST_FUNDING_MS;
//
//                long delay = Math.max(
//                        0L,
//                        targetTime - System.currentTimeMillis()
//                );
//
//                LOG.log(System.Logger.Level.INFO,
//                        "Snapshot previo recopilado: " + symbol);
//
//                // Después del funding solamente solicitaremos
//                // la vela del segundo exacto.
//                scheduler.schedule(
//                        () -> collectFundingCandle(snapshot, eventKey, 0),
//                        delay,
//                        TimeUnit.MILLISECONDS
//                );
//
//            } catch (Exception e) {
//
//                LOG.log(System.Logger.Level.WARNING,
//                        "Error recopilando snapshot de "
//                                + funding.symbol(), e);
//
//                // Reintenta si todavía no ha ocurrido el funding.
//                if (attempt < 2
//                        && System.currentTimeMillis()
//                        < funding.nextFundingTime()) {
//
//                    scheduler.schedule(
//                            () -> collectBeforeFunding(
//                                    funding, eventKey, attempt + 1
//                            ),
//                            1,
//                            TimeUnit.SECONDS
//                    );
//                }
//            }
//        }
//
//
//        /**
//         * Consulta la vela de 1 segundo correspondiente al funding.
//         * Si todavía no está disponible, vuelve a intentarlo.
//         */
//        private void collectFundingCandle(Snapshot snapshot, String eventKey, int attempt) {
//            FundingRate funding = snapshot.funding();
//            try {
//                String symbol = funding.symbol();
//                // Pedimos unas pocas velas para localizar la exacta.
//                List<Candle> candles = connector.fGetCandles(symbol, 250, secondUnit);
//                long fundingTime = funding.nextFundingTime();
//
//                Candle fundingCandle = candles.stream()
//                        .filter(c -> c.openTime() == fundingTime)
//                        .findFirst()
//                        .orElse(null);
//
//                if (fundingCandle == null) {
//                    if (attempt < 10) {
//                        scheduler.schedule(
//                                () -> collectFundingCandle(snapshot, eventKey, attempt + 1),
//                                1,
//                                TimeUnit.SECONDS
//                        );
//                    } else {
//                        LOG.log(System.Logger.Level.WARNING, "No se encontró la vela exacta de %s para %d: %s"
//                                .formatted(symbol, fundingTime, candles.toString()));
//                    }
//                    return;
//                }
//                BigDecimal diffPercent = calculateCandleDiffPercent(fundingCandle);
//                GridCommand.FundingTelemetry record = new GridCommand.FundingTelemetry(
//                        symbol,
//                        funding.nextFundingRate(),
//                        snapshot.quoteAmount2Layer(),
//                        snapshot.quoteAmount4Layer(),
//                        snapshot.quoteAmount6Layer(),
//                        snapshot.quoteVolume1Hour(),
//                        snapshot.quoteVolume1Minute(),
//                        diffPercent,
//                        funding.config().interval(),
//                        fundingTime
//                );
//                saveRecord(record, eventKey);
//                LOG.log(System.Logger.Level.INFO,"FundingTelemetry guardado: " + symbol + " interval=" + funding.config().interval());
//            } catch (Exception e) {
//
//                if (attempt < 10) {
//                    scheduler.schedule(
//                            () -> collectFundingCandle(
//                                    snapshot, eventKey, attempt + 1
//                            ),
//                            500,
//                            TimeUnit.MILLISECONDS
//                    );
//                } else {
//                    LOG.log(System.Logger.Level.ERROR,
//                            "No se pudo recopilar la vela de "
//                                    + funding.symbol(), e);
//                }
//            }
//        }
//
//
//        /**
//         * Obtiene el volumen quote de la última vela disponible.
//         */
//        private BigDecimal getLatestQuoteVolume(String symbol, TimeUnitMarket timeUnit) {
//            List<Candle> candles = connector.fGetCandles(symbol, 1, timeUnit);
//            if (candles.isEmpty()) throw new IllegalStateException("No se encontraron velas para " + symbol);
//            return candles.getLast().volume().quoteVolumen();
//        }
//
//
//        /**
//         * Calcula el quote total de los primeros N niveles:
//         * bids + asks.
//         *
//         * Como el símbolo cotiza en USDT, el resultado está en USDT.
//         */
//        private BigInteger getQuoteAmount(
//                BookTickMultyLayer book,
//                int levels
//        ) {
//
//            BigDecimal bid = sumQuoteLevels(book.bids(), levels);
//            BigDecimal ask = sumQuoteLevels(book.asks(), levels);
//
//            return bid.add(ask).toBigInteger();
//        }
//
//
//        private BigDecimal sumQuoteLevels(
//                List<BookTickMultyLayer.Layer> layers,
//                int levels
//        ) {
//
//            BigDecimal total = BigDecimal.ZERO;
//
//            int limit = Math.min(levels, layers.size());
//
//            for (int i = 0; i < limit; i++) {
//
//                BookTickMultyLayer.Layer layer = layers.get(i);
//
//                total = total.add(
//                        layer.price().multiply(layer.amount())
//                );
//            }
//
//            return total;
//        }
//
//
//        /**
//         * Diferencia porcentual entre apertura y cierre:
//         * ((close - open) / open) * 100.
//         */
//        private BigDecimal calculateCandleDiffPercent(Candle candle) {
//
//            if (candle.openPrice().signum() == 0) {
//                return BigDecimal.ZERO;
//            }
//
//            return candle.closePrice()
//                    .subtract(candle.openPrice())
//                    .divide(candle.openPrice(), MathContext.DECIMAL128)
//                    .multiply(BigDecimal.valueOf(100));
//        }
//
//
//        /**
//         * Añade el registro y persiste la lista completa.
//         */
//        private void saveRecord(
//                GridCommand.FundingTelemetry record,
//                String eventKey
//        ) throws IOException {
//
//            synchronized (fileLock) {
//
//                if (completedEvents.contains(eventKey)) {
//                    return;
//                }
//
//                telemetry.add(record);
//
//                try {
//                    saveJson();
//                    completedEvents.add(eventKey);
//                } catch (IOException e) {
//                    telemetry.remove(telemetry.size() - 1);
//                    throw e;
//                }
//            }
//        }
//
//
//        /**
//         * Carga los registros anteriores.
//         */
//        private void loadJson() throws IOException {
//
//            synchronized (fileLock) {
//
//                if (!Files.exists(jsonFile)) {
//                    return;
//                }
//
//                String json = Files.readString(jsonFile);
//
//                if (json.isBlank()) {
//                    return;
//                }
//
//                List<GridCommand.FundingTelemetry> loaded =
//                        gson.fromJson(json, listType);
//
//                if (loaded == null) {
//                    return;
//                }
//
//                telemetry.addAll(loaded);
//
//                for (GridCommand.FundingTelemetry record : loaded) {
//                    completedEvents.add(
//                            getEventKey(record.symbol(), record.date())
//                    );
//                }
//            }
//        }
//
//
//        /**
//         * Guarda todo en un único JSON.
//         * Usa archivo temporal para evitar dejar un JSON incompleto.
//         */
//        private void saveJson() throws IOException {
//
//            Path parent = jsonFile.toAbsolutePath().getParent();
//
//            if (parent != null) {
//                Files.createDirectories(parent);
//            }
//
//            Path tempFile = jsonFile.resolveSibling(
//                    jsonFile.getFileName() + ".tmp"
//            );
//
//            String json = gson.toJson(telemetry, listType);
//
//            Files.writeString(tempFile, json);
//
//            try {
//                Files.move(
//                        tempFile,
//                        jsonFile,
//                        StandardCopyOption.REPLACE_EXISTING,
//                        StandardCopyOption.ATOMIC_MOVE
//                );
//            } catch (AtomicMoveNotSupportedException e) {
//                Files.move(
//                        tempFile,
//                        jsonFile,
//                        StandardCopyOption.REPLACE_EXISTING
//                );
//            }
//        }
//
//
//        private String getEventKey(String symbol, long fundingTime) {
//            return symbol + "@" + fundingTime;
//        }
//
//
//        public List<GridCommand.FundingTelemetry> getTelemetry() {
//
//            synchronized (fileLock) {
//                return List.copyOf(telemetry);
//            }
//        }
//
//
//        @Override
//        public void close() {
//            scheduler.shutdownNow();
//        }
//
//
//        private record Snapshot(
//                FundingRate funding,
//                BigInteger quoteAmount2Layer,
//                BigInteger quoteAmount4Layer,
//                BigInteger quoteAmount6Layer,
//                BigDecimal quoteVolume1Hour,
//                BigDecimal quoteVolume1Minute
//        ) {}
//    }
}
