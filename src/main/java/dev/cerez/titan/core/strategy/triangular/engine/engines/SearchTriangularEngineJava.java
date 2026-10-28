package dev.cerez.titan.core.strategy.triangular.engine.engines;

import dev.cerez.titan.connector.model.BookTickDouble;
import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.connector.model.Symbol;
import dev.cerez.titan.core.strategy.triangular.engine.SearchTriangularEngine;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * Implementación Java del motor triangular.
 *
 * <h3>Diseño</h3>
 * <ol>
 *   <li><b>Todo precalculado en configure</b>: ids enteros para assets y símbolos, y la lista completa de triángulos
 *       (ya rotados para empezar en {@code preferredStartAsset}) indexada por símbolo. Por tick no hay DFS:
 *       solo se re-evalúan los triángulos que contienen el símbolo actualizado.</li>
 *   <li><b>Estado inmutable por símbolo</b> ({@link Quote}): bid, ask, cantidades y tasas ya netas de comisión viven en
 *       un único objeto que se reemplaza por CAS con control de versión. Un lector nunca ve un bid nuevo con un ask
 *       viejo, y un tick atrasado nunca pisa a uno más nuevo.</li>
 *   <li><b>Evaluación por triángulo, exclusiva y sin bloqueo</b>: cada triángulo se evalúa a lo sumo por un hilo a la
 *       vez (cerrojo CAS + flag {@code dirty}). Si otro hilo ya lo está evaluando, solo marca {@code dirty} y sigue:
 *       el dueño re-evalúa con datos frescos. Nadie espera y no hay lecturas "viejas" que sobrescriban a las nuevas,
 *       así que el estado final siempre equivale al de evaluar secuencialmente con los últimos precios.</li>
 *   <li><b>Fase 1 barata, fase 2 solo si hace falta</b>: producto de tasas (3 multiplicaciones). Solo si &gt; 1 se
 *       simula con redondeo, mínimos y liquidez. Los triángulos inactivos que siguen sin ser rentables se descartan
 *       sin escribir en memoria compartida.</li>
 * </ol>
 */
public class SearchTriangularEngineJava extends SearchTriangularEngine {

    private static final System.Logger LOG = System.getLogger(SearchTriangularEngineJava.class.getName());

    /** Tolerancia relativa para el ruido de coma flotante al dividir por el step. */
    private static final double STEP_REL_EPS = 1e-12;

    private final AtomicLong versionSeq = new AtomicLong();
    private final LongAdder ticksProcessed = new LongAdder();
    private final LongAdder ticksIgnored = new LongAdder();
    private final LongAdder ticksStale = new LongAdder();
    private final LongAdder opportunitiesOpened = new LongAdder();
    private final CopyOnWriteArrayList<OpportunityListener> listeners = new CopyOnWriteArrayList<>();

    /** Grafo inmutable, publicado de forma segura por este campo volatile. */
    private volatile Graph graph;

    public SearchTriangularEngineJava(@NotNull SearchTriangularEngine.EngineConfiguration engineConfig) {
        super(engineConfig);
    }

    // ==========================================================================================
    // configure
    // ==========================================================================================

    @Override
    public void configure(@NotNull Map<String, Symbol> allSymbolMap, @NotNull Map<String, BookTickDouble> liveTickers) {
        validateConfig();
        final double fee = config.getDefaultFeeRate();

        // 1) Símbolos válidos, ordenados por nombre => ids deterministas entre ejecuciones.
        ArrayList<Symbol> symbols = new ArrayList<>(allSymbolMap.size());
        HashSet<String> seenNames = new HashSet<>();
        for (Symbol s : allSymbolMap.values()) {
            if (!Boolean.TRUE.equals(s.getIsAllowTrading())) continue;
            String base = s.getBaseAsset();
            String quote = s.getQuoteAsset();
            if (base.equals("?") || quote.equals("?") || base.equals(quote)) continue;
            if (!seenNames.add(s.name())) continue;
            symbols.add(s);
        }
        symbols.sort(Comparator.comparing(Symbol::name));
        final int symbolCount = symbols.size();

        // 2) Assets con id denso.
        TreeSet<String> assetNames = new TreeSet<>();
        for (Symbol s : symbols) {
            assetNames.add(s.getBaseAsset());
            assetNames.add(s.getQuoteAsset());
        }
        final Asset[] assets = new Asset[assetNames.size()];
        final HashMap<String, Integer> assetIdByName = new HashMap<>(assets.length * 2);
        int nextId = 0;
        for (String name : assetNames) {
            assets[nextId] = new Asset(nextId, name);
            assetIdByName.put(name, nextId);
            nextId++;
        }

        Integer preferred = assetIdByName.get(config.getPreferredStartAsset());
        if (preferred == null) {
            throw new IllegalStateException("preferredStartAsset '" + config.getPreferredStartAsset()
                    + "' no existe en los símbolos configurados");
        }
        final int preferredId = preferred;

        // 3) Aristas dirigidas por símbolo + conversión de cada asset al asset preferido.
        final String[] symbolNames = new String[symbolCount];
        final HashMap<String, Integer> symbolIds = new HashMap<>(symbolCount * 2);
        final HashMap<Long, ArrayList<LegDef>> legsByPair = new HashMap<>(symbolCount * 4);
        final int[] convSymbol = new int[assets.length];
        final boolean[] convAssetIsBase = new boolean[assets.length];
        Arrays.fill(convSymbol, -1);

        for (int id = 0; id < symbolCount; id++) {
            Symbol s = symbols.get(id);
            int baseId = assetIdByName.get(s.getBaseAsset());
            int quoteId = assetIdByName.get(s.getQuoteAsset());
            symbolNames[id] = s.name();
            symbolIds.put(s.name(), id);

            double step = s.getQuantityStepSize().doubleValue();
            double minQty = s.getMinNotionalBase().doubleValue();
            double minNotional = s.getMinNotionalQuote().doubleValue();

            LegDef sell = new LegDef(id, s.name(), true, SideOrder.SELL, assets[baseId], assets[quoteId], step, minQty, minNotional);
            LegDef buy = new LegDef(id, s.name(), false, SideOrder.BUY, assets[quoteId], assets[baseId], step, minQty, minNotional);
            legsByPair.computeIfAbsent(pairKey(baseId, quoteId), k -> new ArrayList<>(1)).add(sell);
            legsByPair.computeIfAbsent(pairKey(quoteId, baseId), k -> new ArrayList<>(1)).add(buy);

            if (quoteId == preferredId && convSymbol[baseId] < 0) {
                convSymbol[baseId] = id;
                convAssetIsBase[baseId] = true;
            } else if (baseId == preferredId && convSymbol[quoteId] < 0) {
                convSymbol[quoteId] = id;
                convAssetIsBase[quoteId] = false;
            }
        }

        // 4) Vecinos con id mayor (para generar cada ciclo dirigido exactamente una vez: a = id mínimo).
        final ArrayList<ArrayList<Integer>> higher = new ArrayList<>(assets.length);
        for (int i = 0; i < assets.length; i++) higher.add(new ArrayList<>());
        for (Long key : legsByPair.keySet()) {
            int from = (int) (key >>> 32);
            int to = (int) (long) key;
            if (to > from) higher.get(from).add(to);
        }
        for (ArrayList<Integer> l : higher) Collections.sort(l);

        // 5) Triángulos.
        final ArrayList<Triangle> triangles = new ArrayList<>();
        int skipped = 0;
        for (int a = 0; a < assets.length; a++) {
            ArrayList<Integer> nbs = higher.get(a);
            for (int bi = 0; bi < nbs.size(); bi++) {
                int b = nbs.get(bi);
                for (int ci = 0; ci < nbs.size(); ci++) {
                    int c = nbs.get(ci);
                    if (c == b) continue;
                    ArrayList<LegDef> bcLegs = legsByPair.get(pairKey(b, c));
                    if (bcLegs == null) continue;
                    for (LegDef ab : legsByPair.get(pairKey(a, b))) {
                        for (LegDef bc : bcLegs) {
                            for (LegDef ca : legsByPair.get(pairKey(c, a))) {
                                Triangle t = buildTriangle(triangles.size(), new LegDef[]{ab, bc, ca},
                                        preferredId, convSymbol, convAssetIsBase);
                                if (t == null) {
                                    skipped++;
                                } else {
                                    triangles.add(t);
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6) Índice símbolo -> triángulos que dependen de él: sus 3 patas y, si no arrancan en el asset preferido,
        //    también el par de conversión (su precio define cuántas unidades equivalen a defaultStartAmount).
        //    Así el estado de cada triángulo es función pura de los últimos precios, no del momento de evaluación.
        final int[] counts = new int[symbolCount];
        for (Triangle t : triangles) {
            counts[t.s0]++;
            counts[t.s1]++;
            counts[t.s2]++;
            if (t.convSymbol >= 0) counts[t.convSymbol]++;
        }
        final Triangle[][] bySymbol = new Triangle[symbolCount][];
        for (int i = 0; i < symbolCount; i++) bySymbol[i] = new Triangle[counts[i]];
        final int[] fill = new int[symbolCount];
        for (Triangle t : triangles) {
            bySymbol[t.s0][fill[t.s0]++] = t;
            bySymbol[t.s1][fill[t.s1]++] = t;
            bySymbol[t.s2][fill[t.s2]++] = t;
            if (t.convSymbol >= 0) bySymbol[t.convSymbol][fill[t.convSymbol]++] = t;
        }

        final Graph g = new Graph(
                assets, symbolNames, symbolIds, new AtomicReferenceArray<>(symbolCount),
                triangles.toArray(new Triangle[0]), bySymbol, skipped,
                1.0 - fee, config.getDefaultStartAmount()
        );

        // 7) Snapshot inicial de precios (antes de publicar el grafo: nadie más puede estar escribiendo).
        for (BookTickDouble tick : liveTickers.values()) {
            Integer id = symbolIds.get(tick.symbol());
            if (id == null) continue;
            g.quotes.set(id, new Quote(tick.bidPrice(), tick.askPrice(), tick.bidQty(), tick.askQty(),
                    g.feeMul, versionSeq.incrementAndGet()));
        }

        this.graph = g;

        // 8) Primera evaluación completa para que el conjunto activo refleje el snapshot inicial.
        for (Triangle t : g.triangles) {
            touch(g, t);
        }
    }

    private void validateConfig() {
        if (config.getMinCycleLength() > TRIANGLE_SIZE || config.getMaxCycleLength() < TRIANGLE_SIZE) {
            throw new IllegalArgumentException("Este motor solo genera ciclos de " + TRIANGLE_SIZE
                    + " aristas (min=" + config.getMinCycleLength() + ", max=" + config.getMaxCycleLength() + ")");
        }
        double fee = config.getDefaultFeeRate();
        if (!(fee >= 0.0 && fee < 1.0)) {
            throw new IllegalArgumentException("defaultFeeRate fuera de rango [0,1): " + fee);
        }
        double start = config.getDefaultStartAmount();
        if (!(start > 0.0) || !Double.isFinite(start)) {
            throw new IllegalArgumentException("defaultStartAmount debe ser > 0: " + start);
        }
    }

    /**
     * Rota las patas para que el ciclo empiece en el asset preferido. Si el triángulo no lo contiene, empieza en el
     * asset de menor id que tenga conversión directa al preferido (para poder expresar defaultStartAmount).
     * Devuelve null si no hay forma de dimensionar el monto inicial.
     */
    private static Triangle buildTriangle(int id, LegDef[] cycle, int preferredId, int[] convSymbol, boolean[] convAssetIsBase) {
        int k = -1;
        for (int i = 0; i < TRIANGLE_SIZE; i++) {
            if (cycle[i].from.id() == preferredId) {
                k = i;
                break;
            }
        }
        if (k < 0) {
            int bestAsset = Integer.MAX_VALUE;
            for (int i = 0; i < TRIANGLE_SIZE; i++) {
                int assetId = cycle[i].from.id();
                if (convSymbol[assetId] >= 0 && assetId < bestAsset) {
                    bestAsset = assetId;
                    k = i;
                }
            }
            if (k < 0) return null;
        }
        LegDef[] legs = {cycle[k], cycle[(k + 1) % 3], cycle[(k + 2) % 3]};
        Asset start = legs[0].from;
        String key = legs[0].from.name() + "->" + legs[1].from.name() + "->" + legs[2].from.name() + "->" + start.name();
        int conv = start.id() == preferredId ? -1 : convSymbol[start.id()];
        boolean convBase = conv >= 0 && convAssetIsBase[start.id()];
        return new Triangle(id, key, legs, start, conv, convBase);
    }

    private static long pairKey(int from, int to) {
        return ((long) from << 32) | (to & 0xFFFFFFFFL);
    }

    // ==========================================================================================
    // Ruta caliente
    // ==========================================================================================

    @Override
    public boolean isConfigured() {
        return graph != null;
    }

    @Override
    public int symbolIdOf(@NotNull String symbol) {
        Graph g = graph;
        if (g == null) return -1;
        Integer id = g.symbolIds.get(symbol);
        return id == null ? -1 : id;
    }

    @Override
    public @NotNull String symbolNameOf(int symbolId) {
        return requireGraph().symbolNames[symbolId];
    }

    @Override
    public void update(@NotNull BookTickDouble tick) {
        Graph g = requireGraph();
        Integer id = g.symbolIds.get(tick.symbol());
        if (id == null) {
            ticksIgnored.increment();
            return;
        }
        ingest(g, id, tick.bidPrice(), tick.askPrice(), tick.bidQty(), tick.askQty(), versionSeq.incrementAndGet());
    }

    @Override
    public void update(int symbolId, double bidPrice, double askPrice, double bidQty, double askQty) {
        Graph g = requireGraph();
        if (symbolId < 0 || symbolId >= g.symbolNames.length) {
            ticksIgnored.increment();
            return;
        }
        ingest(g, symbolId, bidPrice, askPrice, bidQty, askQty, versionSeq.incrementAndGet());
    }

    @Override
    public void update(int symbolId, double bidPrice, double askPrice, double bidQty, double askQty, long version) {
        Graph g = requireGraph();
        if (symbolId < 0 || symbolId >= g.symbolNames.length) {
            ticksIgnored.increment();
            return;
        }
        ingest(g, symbolId, bidPrice, askPrice, bidQty, askQty, version);
    }

    private Graph requireGraph() {
        Graph g = graph;
        if (g == null) throw new IllegalStateException("Call configure first");
        return g;
    }

    private void ingest(Graph g, int symbolId, double bid, double ask, double bidQty, double askQty, long version) {
        final Quote next = new Quote(bid, ask, bidQty, askQty, g.feeMul, version);
        final AtomicReferenceArray<Quote> quotes = g.quotes;
        for (; ; ) {
            Quote cur = quotes.get(symbolId);
            if (cur != null && cur.version >= version) {
                ticksStale.increment(); // llegó tarde: ya hay uno más nuevo y quien lo instaló se encarga de evaluar
                return;
            }
            if (quotes.compareAndSet(symbolId, cur, next)) break;
        }
        ticksProcessed.increment();

        for (Triangle t : g.bySymbol[symbolId]) {
            touch(g, t);
        }
    }

    // ==========================================================================================
    // Evaluación exclusiva por triángulo
    // ==========================================================================================

    /**
     * Pide re-evaluar un triángulo después de haber instalado un precio nuevo.
     * <p>
     * Camino rápido (sin escribir memoria compartida): si nadie lo está evaluando, está inactivo y ni siquiera el
     * producto de tasas supera 1, no hay nada que hacer. El orden de lectura importa: primero {@code lock}, luego
     * {@code current}, luego los precios. Si otro hilo empezó a evaluar con datos anteriores a nuestro precio, su
     * CAS sobre {@code lock} es anterior a nuestra lectura de {@code lock} (volatile = secuencialmente consistente) y
     * la vemos en 1, con lo cual caemos al camino lento.
     */
    private void touch(Graph g, Triangle t) {
        if (t.lock.get() == 0 && t.current == null && !grossProfitable(g, t)) {
            return;
        }

        t.dirty = true;
        for (; ; ) {
            if (!t.lock.compareAndSet(0, 1)) {
                return; // el dueño actual verá dirty=true y re-evaluará con los datos nuevos
            }
            try {
                while (t.dirty) {
                    t.dirty = false;
                    evaluate(g, t);
                }
            } finally {
                t.lock.set(0);
            }
            if (!t.dirty) return; // nadie marcó dirty entre el último while y la liberación
        }
    }

    private static boolean grossProfitable(Graph g, Triangle t) {
        Quote q0 = g.quotes.get(t.s0);
        Quote q1 = g.quotes.get(t.s1);
        Quote q2 = g.quotes.get(t.s2);
        if (q0 == null || q1 == null || q2 == null) return false;
        double p = (t.sell0 ? q0.sellRate : q0.buyRate)
                * (t.sell1 ? q1.sellRate : q1.buyRate)
                * (t.sell2 ? q2.sellRate : q2.buyRate);
        return p > 1.0 + PROFIT_EPSILON;
    }

    /** Solo se llama con el cerrojo del triángulo tomado. */
    private void evaluate(Graph g, Triangle t) {
        final long now = System.nanoTime();
        final Opportunity prev = t.current;
        final Opportunity next = compute(g, t, prev, now);

        if (next == null) {
            if (prev != null) {
                Opportunity closed = prev.withLifeTime(prev.lifeTime().closed(now));
                t.current = null;
                g.active.remove(t);
                notifyClose(closed);
            }
            return;
        }

        t.current = next;
        if (prev == null) {
            g.active.add(t);
            opportunitiesOpened.increment();
            notifyOpen(next);
        } else {
            notifyUpdate(next);
        }
    }

    /**
     * Devuelve la oportunidad simulada, o null si el triángulo no es rentable / ejecutable.
     * Fase 1: producto de tasas. Fase 2: simulación con step, mínimos, liquidez y descartando dust.
     */
    private Opportunity compute(Graph g, Triangle t, Opportunity prev, long now) {
        final Quote q0 = g.quotes.get(t.s0);
        final Quote q1 = g.quotes.get(t.s1);
        final Quote q2 = g.quotes.get(t.s2);
        if (q0 == null || q1 == null || q2 == null) return null;

        final double gross = (t.sell0 ? q0.sellRate : q0.buyRate)
                * (t.sell1 ? q1.sellRate : q1.buyRate)
                * (t.sell2 ? q2.sellRate : q2.buyRate);
        if (!(gross > 1.0 + PROFIT_EPSILON)) return null;

        final double startAmount = startAmount(g, t);
        if (!(startAmount > 0.0) || !Double.isFinite(startAmount)) return null;

        final Quote[] quotes = {q0, q1, q2};
        final Leg[] result = new Leg[TRIANGLE_SIZE];
        final double feeMul = g.feeMul;
        double amount = startAmount;

        for (int i = 0; i < TRIANGLE_SIZE; i++) {
            final LegDef leg = t.legs[i];
            final Quote q = quotes[i];
            final double price;
            final double qty;
            final double used;
            final double got;

            if (leg.sell) {
                // base -> quote: amount está en unidades de la base
                price = q.bid;
                qty = floorToStep(amount, leg.step);
                if (!(qty > 0.0) || qty < leg.minQty || qty > q.bidQty) return null;
                double notional = qty * price;
                if (notional < leg.minNotional) return null;
                used = qty;
                got = notional * feeMul;
            } else {
                // quote -> base: amount está en unidades de la quote
                price = q.ask;
                qty = floorToStep(amount / price, leg.step);
                if (!(qty > 0.0) || qty < leg.minQty || qty > q.askQty) return null;
                double notional = qty * price;
                if (notional < leg.minNotional) return null;
                used = notional;
                got = qty * feeMul;
            }

            result[i] = new Leg(leg.symbolId, leg.symbol, leg.side, leg.from, leg.to, price, qty, amount, used, got);
            amount = got; // el dust (amount - used) se descarta
        }

        if (!Double.isFinite(amount)) return null;
        final double net = amount / startAmount;
        if (!(net > 1.0 + PROFIT_EPSILON)) return null;

        final LifeTime life = prev == null
                ? new LifeTime(now, now, LifeTime.NOT_CLOSED, 1)
                : prev.lifeTime().touched(now);

        return new Opportunity(
                t.id, t.key, t.start, startAmount, amount, gross, net, (net - 1.0) * 100.0,
                List.of(result[0], result[1], result[2]), life
        );
    }

    /** defaultStartAmount está en preferredStartAsset; se convierte al asset de arranque con el mid de su par directo. */
    private static double startAmount(Graph g, Triangle t) {
        if (t.convSymbol < 0) return g.startAmount;
        Quote q = g.quotes.get(t.convSymbol);
        if (q == null) return Double.NaN;
        if (!(q.bid > 0.0) || !(q.ask > 0.0) || !Double.isFinite(q.bid) || !Double.isFinite(q.ask)) return Double.NaN;
        double mid = 0.5 * (q.bid + q.ask);
        return t.convBase ? g.startAmount / mid : g.startAmount * mid;
    }

    private static double floorToStep(double amount, double step) {
        if (!(step > 0.0)) return amount;
        double q = Math.floor(amount / step * (1.0 + STEP_REL_EPS)) * step;
        return Math.min(q, amount);
    }

    // ==========================================================================================
    // Consulta
    // ==========================================================================================

    @Override
    public @NotNull List<Opportunity> getActiveOpportunities() {
        Graph g = graph;
        if (g == null || g.active.isEmpty()) return new ArrayList<>(0);
        ArrayList<Opportunity> out = new ArrayList<>(g.active.size());
        for (Triangle t : g.active) {
            Opportunity o = t.current;
            if (o != null) out.add(o);
        }
        if (out.size() > 1) out.sort(ByProfitDesc.INSTANCE);
        return out;
    }

    @Override
    public void forEachActive(@NotNull Consumer<? super Opportunity> consumer) {
        Graph g = graph;
        if (g == null) return;
        for (Triangle t : g.active) {
            Opportunity o = t.current;
            if (o != null) consumer.accept(o);
        }
    }

    @Override
    public int getActiveCount() {
        Graph g = graph;
        return g == null ? 0 : g.active.size();
    }

    @Override
    public void addListener(@NotNull OpportunityListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    @Override
    public void removeListener(@NotNull OpportunityListener listener) {
        listeners.remove(listener);
    }

    @Override
    public @NotNull List<Asset> getAssets() {
        Graph g = graph;
        return g == null ? List.of() : List.of(g.assets);
    }

    @Override
    public @NotNull EngineStats getStats() {
        Graph g = graph;
        if (g == null) {
            return new EngineStats(0, 0, 0, 0, 0, ticksProcessed.sum(), ticksIgnored.sum(), ticksStale.sum(), opportunitiesOpened.sum());
        }
        return new EngineStats(g.assets.length, g.symbolNames.length, g.triangles.length, g.skippedTriangles,
                g.active.size(), ticksProcessed.sum(), ticksIgnored.sum(), ticksStale.sum(), opportunitiesOpened.sum());
    }

    // ==========================================================================================
    // Notificaciones
    // ==========================================================================================

    private void notifyOpen(Opportunity o) {
        for (OpportunityListener l : listeners) {
            try {
                l.onOpen(o);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "OpportunityListener.onOpen falló", e);
            }
        }
    }

    private void notifyUpdate(Opportunity o) {
        for (OpportunityListener l : listeners) {
            try {
                l.onUpdate(o);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "OpportunityListener.onUpdate falló", e);
            }
        }
    }

    private void notifyClose(Opportunity o) {
        for (OpportunityListener l : listeners) {
            try {
                l.onClose(o);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "OpportunityListener.onClose falló", e);
            }
        }
    }

    // ==========================================================================================
    // Estructuras internas
    // ==========================================================================================

    /** Estado inmutable de un símbolo. Las tasas ya incluyen la comisión; 0 = lado no operable. */
    private static final class Quote {
        final double bid, ask, bidQty, askQty;
        final double sellRate; // base -> quote
        final double buyRate;  // quote -> base
        final long version;

        Quote(double bid, double ask, double bidQty, double askQty, double feeMul, long version) {
            this.bid = bid;
            this.ask = ask;
            this.bidQty = bidQty;
            this.askQty = askQty;
            this.sellRate = (bid > 0.0 && Double.isFinite(bid) && bidQty > 0.0) ? bid * feeMul : 0.0;
            this.buyRate = (ask > 0.0 && Double.isFinite(ask) && askQty > 0.0) ? feeMul / ask : 0.0;
            this.version = version;
        }
    }

    /** Arista dirigida (una pata). Inmutable, creada en configure. */
    private static final class LegDef {
        final int symbolId;
        final String symbol;
        final boolean sell;
        final SideOrder side;
        final Asset from, to;
        final double step, minQty, minNotional;

        LegDef(int symbolId, String symbol, boolean sell, SideOrder side, Asset from, Asset to,
               double step, double minQty, double minNotional) {
            this.symbolId = symbolId;
            this.symbol = symbol;
            this.sell = sell;
            this.side = side;
            this.from = from;
            this.to = to;
            this.step = step;
            this.minQty = minQty;
            this.minNotional = minNotional;
        }
    }

    private static final class Triangle {
        final int id;
        final String key;
        final LegDef[] legs;
        final Asset start;
        final int s0, s1, s2;
        final boolean sell0, sell1, sell2;
        /** Símbolo para convertir defaultStartAmount al asset de arranque; -1 si el arranque ya es el preferido. */
        final int convSymbol;
        final boolean convBase;

        /** 0 = libre, 1 = alguien lo está evaluando. */
        final AtomicInteger lock = new AtomicInteger();
        volatile boolean dirty;
        /** null = inactivo. Solo se escribe con el cerrojo tomado. */
        volatile Opportunity current;

        Triangle(int id, String key, LegDef[] legs, Asset start, int convSymbol, boolean convBase) {
            this.id = id;
            this.key = key;
            this.legs = legs;
            this.start = start;
            this.s0 = legs[0].symbolId;
            this.s1 = legs[1].symbolId;
            this.s2 = legs[2].symbolId;
            this.sell0 = legs[0].sell;
            this.sell1 = legs[1].sell;
            this.sell2 = legs[2].sell;
            this.convSymbol = convSymbol;
            this.convBase = convBase;
        }
    }

    private static final class Graph {
        final Asset[] assets;
        final String[] symbolNames;
        final HashMap<String, Integer> symbolIds; // solo lectura tras publicarse
        final AtomicReferenceArray<Quote> quotes;
        final Triangle[] triangles;
        final Triangle[][] bySymbol;
        final int skippedTriangles;
        final double feeMul;
        final double startAmount;
        final Set<Triangle> active = ConcurrentHashMap.newKeySet();

        Graph(Asset[] assets, String[] symbolNames, HashMap<String, Integer> symbolIds,
              AtomicReferenceArray<Quote> quotes, Triangle[] triangles, Triangle[][] bySymbol,
              int skippedTriangles, double feeMul, double startAmount) {
            this.assets = assets;
            this.symbolNames = symbolNames;
            this.symbolIds = symbolIds;
            this.quotes = quotes;
            this.triangles = triangles;
            this.bySymbol = bySymbol;
            this.skippedTriangles = skippedTriangles;
            this.feeMul = feeMul;
            this.startAmount = startAmount;
        }
    }
}