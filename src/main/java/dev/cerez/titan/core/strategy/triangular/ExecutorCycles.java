package dev.cerez.titan.core.strategy.triangular;

import dev.cerez.titan.connector.Connector;
import dev.cerez.titan.connector.exception.ApiException;
import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.connector.model.Symbol;
import dev.cerez.titan.connector.model.order.OrderSpotResult;
import dev.cerez.titan.core.strategy.triangular.engine.SearchTriangularEngine;
import dev.cerez.titan.core.strategy.triangular.engine.SearchTriangularEngine.Leg;
import dev.cerez.titan.core.strategy.triangular.engine.SearchTriangularEngine.Opportunity;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.utils.Utils;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * Ejecuta los ciclos triangulares que detecta el engine.
 *
 * <h3>Garantías</h3>
 * <ul>
 *   <li><b>Un ciclo a la vez.</b> La puerta de entrada es un CAS sobre {@link #active}: de N hilos de ticks que ven
 *       una oportunidad a la vez, solo uno la toma. Los demás salen sin bloquearse ni tocar la red.</li>
 *   <li><b>Una vuelta es indivisible.</b> Una vez enviada la primera pata, las 3 se ejecutan sí o sí aunque la
 *       oportunidad desaparezca del engine, y no se interrumpe ({@link #close()} espera a que termine).
 *       Solo se decide <i>entre</i> vueltas si repetir.</li>
 *   <li><b>Siempre USDT -&gt; ... -&gt; USDT</b> (el asset preferido del engine) con el monto del engine
 *       ({@code defaultStartAmount}), que es el que el engine validó con redondeos y mínimos.</li>
 *   <li><b>Repite</b> mientras la vuelta termine con MÁS USDT que el monto inicial. Se detiene al primer retorno
 *       menor o igual, si la ventana ya cerró (opcional), al llegar al tope de vueltas o al cerrar el ejecutor.</li>
 *   <li><b>Fallo a mitad de ciclo = ejecutor detenido.</b> Si queda dinero fuera de USDT y no se pudo completar, no
 *       se inventa nada: se detiene, se loguea la posición atascada y se espera {@link #resume()}.</li>
 * </ul>
 *
 * <h3>Uso</h3>
 * <pre>{@code
 * long t = System.nanoTime();
 * engine.update(tick);
 * executor.onOpportunities(engine.getActiveOpportunities(), t);
 * }</pre>
 * {@link #onOpportunities} es thread-safe, no usa {@code synchronized} y nunca hace I/O en el hilo llamador.
 */
public class ExecutorCycles implements AutoCloseable {

    private final @NotNull SearchTriangularEngine engine;
    private final @NotNull Connector connector;
    private final @NotNull ExecutorCyclesConfig config;
    private final @NotNull String preferredAsset;
    private final @NotNull BigDecimal startAmount;
    private final @NotNull Set<String> tradableSymbols;
    private final @NotNull ExecutorService executor;

    /** Puerta de "un ciclo a la vez": null = libre. */
    private final AtomicReference<ActiveRun> active = new AtomicReference<>();
    private final AtomicLong runIds = new AtomicLong();
    private final ConcurrentHashMap<Integer, Long> cooldownUntil = new ConcurrentHashMap<>();
    private final Set<Integer> invalidTriangles = ConcurrentHashMap.newKeySet();

    private final AtomicLong availableSignature = new AtomicLong(0);
    private final AtomicLong availableLoggedNano;
    private final long availableLogIntervalNanos;
    private final long cooldownNanos;

    private final AtomicLong cyclesStarted = new AtomicLong();
    private final AtomicLong lapsCompleted = new AtomicLong();
    private final AtomicLong lapsProfitable = new AtomicLong();

    @Setter
    private volatile boolean enabled = true;
    @Getter
    private volatile @Nullable String haltReason = null;
    /** Un solo escritor (el hilo de ejecución); lectura desde cualquier hilo. */
    @Getter
    private volatile @NotNull BigDecimal pnl = BigDecimal.ZERO;

    @SuppressWarnings("this-escape")
    public ExecutorCycles(@NotNull ExecutorCyclesConfig config,
                          @NotNull Connector connector,
                          @NotNull SearchTriangularEngine engine) {
        this.config = config;
        this.connector = connector;
        this.engine = engine;
        // Fuente única de verdad: lo que el engine validó es lo que se ejecuta.
        this.preferredAsset = engine.getConfig().getPreferredStartAsset();
        this.startAmount = BigDecimal.valueOf(engine.getConfig().getDefaultStartAmount());

        HashSet<String> tradable = new HashSet<>();
        for (Symbol s : connector.sGetAllSymbols().values()) {
            if (s.getIsAllowTrading()) tradable.add(s.name());
        }
        this.tradableSymbols = Collections.unmodifiableSet(tradable);

        this.executor = Executors.newSingleThreadExecutor(Utils.getThreadFactory(this));
        this.availableLogIntervalNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, config.getAvailableLogIntervalMillis()));
        this.cooldownNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, config.getCooldownMillis()));
        this.availableLoggedNano = new AtomicLong(System.nanoTime() - availableLogIntervalNanos);
    }

    // ==========================================================================================
    // Entrada: se llama después de cada tick, desde cualquier hilo
    // ==========================================================================================

    /**
     * @param opportunities oportunidades vigentes del engine (puede estar vacía)
     * @param tickNanoTime  {@code System.nanoTime()} del momento en que llegó el tick que las originó (para medir lag)
     */
    public void onOpportunities(@NotNull List<Opportunity> opportunities, long tickNanoTime) {
        final ActiveRun run = active.get();

        if (run != null) {
            // Hay un ciclo en ejecución: no se inicia nada. Solo se observa si su ventana sigue abierta.
            watchWindow(run);
            if (!opportunities.isEmpty()) logAvailableIfChanged(opportunities);
            return;
        }

        if (opportunities.isEmpty()) {
            if (availableSignature.get() != 0) availableSignature.set(0); // para que una reaparición se loguee
            return;
        }

        logAvailableIfChanged(opportunities);

        if (!enabled || haltReason != null) return;

        final Opportunity best = pickBest(opportunities, tickNanoTime);
        if (best != null) tryStart(best, tickNanoTime);
    }

    private @Nullable Opportunity pickBest(List<Opportunity> opportunities, long tickNanoTime) {
        final long now = System.nanoTime();
        final double maxLag = config.getMaxLagMillis();
        if (maxLag >= 0 && (now - tickNanoTime) / 1_000_000d > maxLag) return null; // tick ya viejo

        Opportunity best = null;
        for (Opportunity o : opportunities) {
            if (!o.startAsset().name().equals(preferredAsset)) continue;      // solo ciclos que arrancan en USDT
            if (o.profitPercent() < config.getMinProfit()) continue;
            if (invalidTriangles.contains(o.triangleId())) continue;
            Long until = cooldownUntil.get(o.triangleId());
            if (until != null) {
                if (until - now > 0) continue;
                cooldownUntil.remove(o.triangleId(), until);
            }
            if (best == null || o.profitPercent() > best.profitPercent()) best = o;
        }
        return best;
    }

    private void tryStart(Opportunity best, long tickNanoTime) {
        final String invalid = validate(best);
        if (invalid != null) {
            if (invalidTriangles.add(best.triangleId())) {
                Log.warning("Oportunidad descartada permanentemente (" + best.key() + "): " + invalid);
            }
            return;
        }

        final ActiveRun run = new ActiveRun(runIds.incrementAndGet(), best, tickNanoTime, System.nanoTime());
        if (!active.compareAndSet(null, run)) return; // otro hilo ganó: un solo ciclo a la vez

        if (!enabled || haltReason != null) { // cambió el estado entre la lectura y el CAS
            active.set(null);
            return;
        }
        try {
            executor.execute(() -> runCycle(run));
        } catch (RejectedExecutionException e) {
            active.set(null);
        }
    }

    /** Estructura del ciclo: 3 patas encadenadas, empieza y termina en el asset preferido, símbolos operables. */
    private @Nullable String validate(Opportunity o) {
        List<Leg> legs = o.legs();
        if (legs.size() != SearchTriangularEngine.TRIANGLE_SIZE) return "no tiene 3 patas";
        if (!legs.getFirst().from().name().equals(preferredAsset)) return "no empieza en " + preferredAsset;
        if (!legs.getLast().to().name().equals(preferredAsset)) return "no termina en " + preferredAsset;
        for (int i = 0; i < legs.size(); i++) {
            Leg leg = legs.get(i);
            if (i > 0 && !legs.get(i - 1).to().equals(leg.from())) return "patas no encadenadas en la pata " + (i + 1);
            if (!tradableSymbols.contains(leg.symbol())) return "símbolo no operable en este entorno: " + leg.symbol();
        }
        return null;
    }

    private void watchWindow(ActiveRun run) {
        if (run.windowClosed.get()) return;
        if (engine.getActiveOpportunity(run.triangleId) != null) return;
        if (run.windowClosed.compareAndSet(false, true)) {
            run.windowClosedNano = System.nanoTime();
            Log.info("<yellow>Ventana cerrada durante la ejecución #%d (%s): %.1fms desde el inicio, iba en vuelta %d pata %d/%d<reset>",
                    run.id, run.initial.key(), (run.windowClosedNano - run.startNano) / 1_000_000d,
                    run.lap, run.leg, SearchTriangularEngine.TRIANGLE_SIZE);
        }
    }

    // ==========================================================================================
    // Ejecución (siempre en el único hilo del executor)
    // ==========================================================================================

    private void runCycle(ActiveRun run) {
        int laps = 0;
        BigDecimal cycleProfit = BigDecimal.ZERO;
        String reason = "desconocido";
        boolean cooldown = false;
        try {
            cyclesStarted.incrementAndGet();
            logStart(run);

            if (config.isDryRun()) {
                logDryRun(run);
                reason = "dry-run (no se enviaron órdenes)";
                cooldown = true;
                return;
            }

            while (true) {
                laps++;
                run.lap = laps;
                final BigDecimal end = executeLap(run);

                final BigDecimal lapProfit = end.subtract(startAmount);
                cycleProfit = cycleProfit.add(lapProfit);
                pnl = pnl.add(lapProfit);
                lapsCompleted.incrementAndGet();
                logLap(run, laps, end, lapProfit);

                if (lapProfit.signum() <= 0) {
                    reason = "la vuelta no superó el monto inicial";
                    cooldown = true;
                    break;
                }
                lapsProfitable.incrementAndGet();

                if (laps >= config.getMaxLapsPerCycle()) {
                    reason = "tope de vueltas (" + config.getMaxLapsPerCycle() + ")";
                    break;
                }
                if (!enabled) {
                    reason = "ejecutor deshabilitado/cerrado";
                    break;
                }
                if (config.isRepeatOnlyIfStillActive() && engine.getActiveOpportunity(run.triangleId) == null) {
                    reason = "la ventana ya cerró; no se repite";
                    break;
                }
            }
        } catch (LegFailure f) {
            reason = f.getMessage();
            Log.info("<red>Fallo en pata %d/%d del ciclo #%d: %s<reset>", f.legIndex + 1, SearchTriangularEngine.TRIANGLE_SIZE, run.id, f.getMessage());
            if (f.getCause() instanceof Exception cause) Log.exception("Detalle del fallo", cause);
            cooldown = true;
            if (f.fundsAtRisk) {
                halt(run, f.getMessage());
            }
        } catch (Exception e) {
            reason = "error inesperado: " + e;
            Log.exception("Error inesperado ejecutando el ciclo #" + run.id, e);
            cooldown = true;
            Holding h = run.holding;
            if (h != null && !h.asset().equals(preferredAsset)) {
                halt(run, "error inesperado con fondos fuera de " + preferredAsset);
            }
        } finally {
            if (cooldown && cooldownNanos > 0) {
                cooldownUntil.put(run.triangleId, System.nanoTime() + cooldownNanos);
            }
            logEnd(run, laps, cycleProfit, reason);
            active.set(null); // última acción: libera la puerta
        }
    }

    /** Una vuelta completa: las 3 patas, en orden, con el resultado de cada una como entrada de la siguiente. */
    private BigDecimal executeLap(ActiveRun run) throws LegFailure {
        final List<Leg> legs = run.initial.legs();
        BigDecimal amount = startAmount;
        run.holding = new Holding(preferredAsset, amount);

        for (int i = 0; i < legs.size(); i++) {
            final Leg leg = legs.get(i);
            run.leg = i + 1;
            final BigDecimal in = amount;
            final long t0 = System.nanoTime();

            final BigDecimal out = sendLeg(i, leg, in);

            run.holding = new Holding(leg.to().name(), out);
            Log.info("   [#%d v%d] pata %d/%d %s %s: %s %s -> %s %s (%.1fms)",
                    run.id, run.lap, i + 1, legs.size(),
                    leg.side() == SideOrder.SELL ? "<red>SELL<reset>" : "<green>BUY<reset>", leg.symbol(),
                    plain(in), leg.from().name(), plain(out), leg.to().name(),
                    (System.nanoTime() - t0) / 1_000_000d);
            amount = out;
        }
        return amount;
    }

    /**
     * Envía una pata. La pata 1 se intenta una sola vez (todavía no hay dinero fuera de USDT). Las siguientes se
     * reintentan con el MISMO clientOrderId para que el exchange rechace un duplicado si el primer intento sí entró.
     */
    private BigDecimal sendLeg(int legIndex, Leg leg, BigDecimal amount) throws LegFailure {
        final String clientId = Utils.uuidToBase36(UUID.randomUUID());
        final boolean sell = leg.side() == SideOrder.SELL;
        final int attempts = legIndex == 0 ? 1 : Math.max(1, config.getMaxLegAttempts());
        ApiException last = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                final OrderSpotResult result = connector.sSendOrderToMkt(leg.symbol(), leg.side(), amount, clientId, sell);
                final BigDecimal received = result.receivedQty();
                if (received == null || received.signum() <= 0) {
                    // La orden pudo haberse ejecutado: estado desconocido => no se sigue.
                    throw new LegFailure(legIndex, true, null,
                            "la orden " + clientId + " no devolvió cantidad recibida (estado desconocido)");
                }
                return received;
            } catch (ApiException e) {
                last = e;
                Log.warning(String.format(Locale.ROOT, "Pata %d (%s %s) intento %d/%d rechazado: %s",
                        legIndex + 1, leg.side(), leg.symbol(), attempt, attempts, e.getMessage()));
                if (attempt < attempts && config.getRetryBackoffMillis() > 0) {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(config.getRetryBackoffMillis()));
                }
            } catch (RuntimeException e) {
                // Excepción que NO es un rechazo del exchange (parseo, NPE...): la orden pudo haber salido.
                throw new LegFailure(legIndex, true, e,
                        "excepción inesperada al enviar " + leg.side() + " " + leg.symbol() + ": estado de la orden desconocido");
            }
        }
        // Rechazos definitivos: en la pata 1 no hay nada en riesgo; en las demás sí.
        throw new LegFailure(legIndex, legIndex > 0, last,
                "pata rechazada tras " + attempts + " intento(s): " + (last == null ? "?" : last.getMessage()));
    }

    private void halt(ActiveRun run, String why) {
        this.haltReason = why;
        Holding h = run.holding;
        Log.warning(String.format(Locale.ROOT,
                "EJECUTOR DETENIDO: %s | ciclo #%d, pata %d/%d | posición actual: %s %s | resolver manualmente y llamar resume()",
                why, run.id, run.leg, SearchTriangularEngine.TRIANGLE_SIZE,
                h == null ? "?" : plain(h.amount()), h == null ? "?" : h.asset()));
    }

    // ==========================================================================================
    // Control
    // ==========================================================================================

    /** Quita el estado "detenido" (después de resolver manualmente cualquier posición atascada). */
    public void resume() {
        this.haltReason = null;
        Log.info("<green>Ejecutor reanudado<reset>");
    }

    public boolean isRunning() {
        return active.get() != null;
    }

    public @NotNull ExecutorStats getStats() {
        return new ExecutorStats(cyclesStarted.get(), lapsCompleted.get(), lapsProfitable.get(), pnl, isRunning(), haltReason);
    }

    /** No interrumpe un ciclo en curso: espera a que termine la vuelta (atomicidad). */
    @Override
    public void close() {
        enabled = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                Log.warning("ExecutorCycles: un ciclo sigue en curso tras 60s de espera");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==========================================================================================
    // Logs
    // ==========================================================================================

    private void logAvailableIfChanged(List<Opportunity> list) {
        long sig = list.size();
        for (Opportunity o : list) sig += mix(o.triangleId());
        final long prev = availableSignature.get();
        if (sig == prev) return;

        final long now = System.nanoTime();
        if (now - availableLoggedNano.get() < availableLogIntervalNanos) return; // antirruido
        if (!availableSignature.compareAndSet(prev, sig)) return;                  // solo un hilo loguea
        availableLoggedNano.set(now);

        Opportunity[] top = list.toArray(new Opportunity[0]);
        Arrays.sort(top, (a, b) -> Double.compare(b.profitPercent(), a.profitPercent()));
        Log.info("<yellow>Ciclos disponibles: %d<reset>", top.length);
        int shown = Math.min(top.length, Math.max(1, config.getAvailableLogTop()));
        for (int i = 0; i < shown; i++) {
            Opportunity o = top[i];
            boolean runnable = o.startAsset().name().equals(preferredAsset);
            Log.info("   %d) %s | %+.4f%% | edad %.1fms, %d ticks%s", i + 1, o.key(), o.profitPercent(),
                    o.lifeTime().durationMillis(), o.lifeTime().ticks(),
                    runnable ? "" : " | no ejecutable (no arranca en " + preferredAsset + ")");
        }
        if (top.length > shown) Log.info("   ... y %d más", top.length - shown);
    }

    private static long mix(int id) {
        long x = (id + 1L) * 0x9E3779B97F4A7C15L;
        return x ^ (x >>> 29);
    }

    private void logStart(ActiveRun run) {
        Opportunity o = run.initial;
        StringBuilder legs = new StringBuilder();
        for (Leg leg : o.legs()) {
            if (!legs.isEmpty()) legs.append(" -> ");
            legs.append(leg.side()).append(' ').append(leg.symbol());
        }
        Log.info("<green>INICIO ciclo #%d<reset> %s | esperado %+.4f%% | monto %s %s | lag %.2fms | ventana: %.1fms, %d ticks%s",
                run.id, o.key(), o.profitPercent(), plain(startAmount), preferredAsset,
                (run.startNano - run.tickNano) / 1_000_000d, o.lifeTime().durationMillis(), o.lifeTime().ticks(),
                config.isDryRun() ? " | DRY-RUN" : "");
        Log.info("   %s", legs);
    }

    private void logDryRun(ActiveRun run) {
        Opportunity o = run.initial;
        for (Leg leg : o.legs()) {
            Log.info("   [dry-run] %s %s @ %.10f | entra %s %s -> sale %s %s", leg.side(), leg.symbol(), leg.price(),
                    plain(BigDecimal.valueOf(leg.amountIn())), leg.from().name(),
                    plain(BigDecimal.valueOf(leg.amountOut())), leg.to().name());
        }
    }

    private void logLap(ActiveRun run, int lap, BigDecimal end, BigDecimal lapProfit) {
        boolean win = lapProfit.signum() > 0;
        Log.info("   Vuelta %d del ciclo #%d: %s -> %s %s | %s%s %s<reset> | esperado %+.4f%%",
                lap, run.id, plain(startAmount), plain(end), preferredAsset,
                win ? "<green>+" : "<red>", plain(lapProfit), preferredAsset, run.initial.profitPercent());
    }

    private void logEnd(ActiveRun run, int laps, BigDecimal cycleProfit, String reason) {
        long now = System.nanoTime();
        boolean win = cycleProfit.signum() > 0;
        Log.info("%s FIN ciclo #%d<reset> %s | motivo: %s | vueltas: %d | resultado: %s%s %s<reset> | duración: %.1fms | PNL acumulado: %s %s%s",
                win ? "<green>" : "<red>", run.id, run.initial.key(), reason, laps,
                win ? "<green>+" : "<red>", plain(cycleProfit), preferredAsset,
                (now - run.startNano) / 1_000_000d, plain(pnl), preferredAsset,
                run.windowClosed.get()
                        ? String.format(Locale.ROOT, " | la ventana cerró a los %.1fms", (run.windowClosedNano - run.startNano) / 1_000_000d)
                        : "");
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    // ==========================================================================================
    // Tipos internos
    // ==========================================================================================

    public record ExecutorStats(long cyclesStarted, long lapsCompleted, long lapsProfitable,
                                BigDecimal pnl, boolean running, @Nullable String haltReason) {}

    private record Holding(String asset, BigDecimal amount) {}

    private static final class ActiveRun {
        final long id;
        final Opportunity initial;
        final int triangleId;
        final long tickNano;
        final long startNano;
        final AtomicBoolean windowClosed = new AtomicBoolean();
        volatile long windowClosedNano;
        volatile int lap;
        volatile int leg;
        volatile Holding holding;

        ActiveRun(long id, Opportunity initial, long tickNano, long startNano) {
            this.id = id;
            this.initial = initial;
            this.triangleId = initial.triangleId();
            this.tickNano = tickNano;
            this.startNano = startNano;
        }
    }

    /** {@code fundsAtRisk}: true si quedó (o puede haber quedado) dinero fuera de USDT. */
    private static final class LegFailure extends Exception {
        @Serial
        private static final long serialVersionUID = 1L;
        final int legIndex;
        final boolean fundsAtRisk;

        LegFailure(int legIndex, boolean fundsAtRisk, @Nullable Throwable cause, String message) {
            super(message, cause, false, false);
            this.legIndex = legIndex;
            this.fundsAtRisk = fundsAtRisk;
        }
    }

    // <config-begin>
    @Builder
    @Data
    public static class ExecutorCyclesConfig {
        /** true = detecta y loguea pero NO envía órdenes. Por defecto true por seguridad. */
        @Builder.Default private boolean dryRun = true;
        /** Ganancia neta mínima (%) esperada por el engine para iniciar un ciclo. */
        @Builder.Default private double minProfit = 0.0;
        /** Edad máxima (ms) del tick respecto a cuando se decide iniciar; -1 = sin límite. */
        @Builder.Default private double maxLagMillis = -1;
        /** Tope de vueltas consecutivas por ciclo (freno de seguridad). */
        @Builder.Default private int maxLapsPerCycle = 50;
        /** Repetir solo si el engine sigue viendo el ciclo rentable. false = repetir mientras la vuelta anterior ganó. */
        @Builder.Default private boolean repeatOnlyIfStillActive = true;
        /** Intentos por pata (la pata 1 siempre es 1). */
        @Builder.Default private int maxLegAttempts = 3;
        @Builder.Default private long retryBackoffMillis = 50;
        /** Tiempo que se ignora un triángulo tras una vuelta sin ganancia, un aborto o un dry-run. */
        @Builder.Default private long cooldownMillis = 2000;
        @Builder.Default private long availableLogIntervalMillis = 500;
        @Builder.Default private int availableLogTop = 5;
    }
    // <config-end>
}