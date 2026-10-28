package dev.cerez.titan.core.strategy.triangular.engine;

import dev.cerez.titan.connector.model.BookTickDouble;
import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.connector.model.Symbol;
import dev.cerez.titan.core.BaseConfig;
import dev.cerez.titan.utils.Configurable;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Motor de búsqueda de arbitraje triangular.
 *
 * <h3>Contrato</h3>
 * <ul>
 *   <li>{@link #configure} se llama UNA vez con TODOS los símbolos. El grafo y los triángulos se precalculan ahí;
 *       después el motor solo acepta ticks de esos símbolos (los desconocidos se ignoran).</li>
 *   <li>Toda la ruta caliente trabaja con {@code int} (id de símbolo / id de asset). El {@code String} del tick
 *       se resuelve a id una sola vez.</li>
 *   <li>Los métodos {@code update*}, {@code getActive*} y {@code forEachActive} son thread-safe.</li>
 *   <li>Todo lo que se devuelve ({@link Opportunity}, {@link Leg}, {@link LifeTime}) es inmutable.</li>
 * </ul>
 *
 * <h3>Qué es una oportunidad "válida"</h3>
 * Un triángulo cuyo producto de tasas (con comisión) es &gt; 1 <b>y además</b> sigue siendo rentable al simular
 * con {@code defaultStartAmount} (expresado en {@code preferredStartAsset}) aplicando redondeo por step,
 * cantidad mínima, notional mínimo y liquidez del top-of-book. El dust (lo que sobra al redondear) se descarta:
 * se cuenta como pérdida porque no se garantiza su valor.
 *
 * @author Claude
 */
public abstract class SearchTriangularEngine implements Configurable<SearchTriangularEngine.EngineConfiguration> {

    public static final double PROFIT_EPSILON = 1e-12;

    /** Este motor solo genera ciclos de 3 aristas. */
    public static final int TRIANGLE_SIZE = 3;

    protected final EngineConfiguration config;

    protected SearchTriangularEngine(@NotNull SearchTriangularEngine.EngineConfiguration config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public EngineConfiguration getConfig() {
        return config;
    }

    // ------------------------------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------------------------------

    /**
     * Construye el grafo completo y los triángulos. Debe llamarse antes de procesar ticks.
     *
     * @param allSymbolMap todos los símbolos con los que trabajará el motor (no se agregan después)
     * @param liveTickers  snapshot inicial de precios (puede estar incompleto; puede estar vacío)
     */
    public abstract void configure(
            @NotNull Map<String, Symbol> allSymbolMap,
            @NotNull Map<String, BookTickDouble> liveTickers
    );

    public abstract boolean isConfigured();

    // ------------------------------------------------------------------------------------------
    // Ruta caliente
    // ------------------------------------------------------------------------------------------

    /** Id interno del símbolo, o -1 si no pertenece al grafo. Resolverlo una vez y reutilizarlo. */
    public abstract int symbolIdOf(@NotNull String symbol);

    public abstract @NotNull String symbolNameOf(int symbolId);

    /** Ingiere un tick y re-evalúa solo los triángulos que contienen ese símbolo. */
    public abstract void update(@NotNull BookTickDouble tick);

    /** Igual que {@link #update(BookTickDouble)} pero sin ningún String ni objeto intermedio. */
    public abstract void update(int symbolId, double bidPrice, double askPrice, double bidQty, double askQty);

    /**
     * Variante con versión externa (p.ej. updateId del exchange). Un tick con versión &lt;= a la ya aplicada
     * para ese símbolo se descarta como obsoleto. No mezclar con las variantes sin versión.
     */
    public abstract void update(int symbolId, double bidPrice, double askPrice, double bidQty, double askQty, long version);

    /**
     * Compatibilidad con la API anterior: ingiere el tick y devuelve las oportunidades vigentes
     * (las nuevas y todas las anteriores que siguen siendo rentables), ordenadas por ganancia desc.
     * Si no necesitas la lista en cada tick, usa {@link #update(BookTickDouble)} + {@link #forEachActive}.
     */
    public @NotNull List<Opportunity> computeTriangularArbitrageOpportunities(@NotNull BookTickDouble updatedTicker) {
        update(updatedTicker);
        return getActiveOpportunities();
    }

    // ------------------------------------------------------------------------------------------
    // Consulta de oportunidades vigentes
    // ------------------------------------------------------------------------------------------

    /** Copia (propiedad del llamador) de las oportunidades vigentes, ordenada por ganancia neta desc. */
    public abstract @NotNull List<Opportunity> getActiveOpportunities();

    /** Recorre las oportunidades vigentes sin copiar ni ordenar. El consumer puede ser llamado desde varios hilos. */
    public abstract void forEachActive(@NotNull Consumer<? super Opportunity> consumer);

    public abstract int getActiveCount();

    /**
     * Notificaciones push: abre / actualiza / cierra. Se invocan en el hilo que procesó el tick,
     * por lo que deben ser rápidas. Una excepción del listener se registra y se ignora.
     */
    public abstract void addListener(@NotNull OpportunityListener listener);

    public abstract void removeListener(@NotNull OpportunityListener listener);

    public abstract @NotNull List<Asset> getAssets();

    public abstract @NotNull EngineStats getStats();

    // ------------------------------------------------------------------------------------------
    // Modelos (todos inmutables)
    // ------------------------------------------------------------------------------------------

    /** Reemplaza a NameAsset: id entero (índice denso, estable tras configure) + nombre. */
    public record Asset(int id, @NotNull String name) {
        @Override
        public int hashCode() {
            return id;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Detalle de una pata simulada.
     *
     * @param price        precio usado (bid si SELL, ask si BUY)
     * @param baseQuantity cantidad de la base ya redondeada al step
     * @param amountIn     cantidad disponible del asset de entrada al llegar a la pata
     * @param amountUsed   lo realmente consumido del asset de entrada (amountIn - amountUsed = dust)
     * @param amountOut    lo recibido del asset de salida, ya descontada la comisión
     */
    public record Leg(
            int symbolId,
            @NotNull String symbol,
            @NotNull SideOrder side,
            @NotNull Asset from,
            @NotNull Asset to,
            double price,
            double baseQuantity,
            double amountIn,
            double amountUsed,
            double amountOut
    ) {
        public double dust() {
            return amountIn - amountUsed;
        }
    }

    /** Telemetría de vida de una oportunidad. Tiempos en nanoTime. */
    public record LifeTime(long openNanoTime, long lastUpdateNanoTime, long closeNanoTime, int ticks) {

        public static final long NOT_CLOSED = Long.MIN_VALUE;

        public boolean isOpen() {
            return closeNanoTime == NOT_CLOSED;
        }

        /** Duración total; si sigue abierta, hasta ahora. */
        public long durationNanos() {
            long end = isOpen() ? System.nanoTime() : closeNanoTime;
            return end - openNanoTime;
        }

        public double durationMillis() {
            return durationNanos() / 1_000_000d;
        }

        public @NotNull LifeTime touched(long nowNano) {
            return new LifeTime(openNanoTime, nowNano, closeNanoTime, ticks + 1);
        }

        public @NotNull LifeTime closed(long nowNano) {
            return new LifeTime(openNanoTime, lastUpdateNanoTime, nowNano, ticks);
        }
    }

    /**
     * Oportunidad vigente (o cerrada, cuando llega a {@link OpportunityListener#onClose}).
     * Las 3 patas ya vienen rotadas para empezar en {@code startAsset}.
     *
     * @param grossRateProduct producto de tasas con comisión, SIN redondeo ni dust
     * @param netRateProduct   endAmount / startAmount, tras redondeo y descartando dust (lo ejecutable)
     * @param profitPercent    (netRateProduct - 1) * 100
     */
    public record Opportunity(
            int triangleId,
            @NotNull String key,
            @NotNull Asset startAsset,
            double startAmount,
            double endAmount,
            double grossRateProduct,
            double netRateProduct,
            double profitPercent,
            @NotNull List<Leg> legs,
            @NotNull LifeTime lifeTime
    ) {
        public @NotNull Opportunity withLifeTime(@NotNull LifeTime lifeTime) {
            return new Opportunity(triangleId, key, startAsset, startAmount, endAmount,
                    grossRateProduct, netRateProduct, profitPercent, legs, lifeTime);
        }
    }

    public interface OpportunityListener {
        default void onOpen(@NotNull Opportunity opportunity) {}

        default void onUpdate(@NotNull Opportunity opportunity) {}

        /** {@code last} trae el último estado conocido con {@code lifeTime.closeNanoTime} ya fijado. */
        default void onClose(@NotNull Opportunity last) {}
    }

    public record EngineStats(
            int assets,
            int symbols,
            int triangles,
            int skippedTriangles,
            int activeOpportunities,
            long ticksProcessed,
            long ticksIgnored,
            long ticksStale,
            long opportunitiesOpened
    ) {}

    /** Orden por ganancia neta descendente. Clase nombrada (no lambda) para no pagar bootstrap de invokedynamic. */
    protected static final class ByProfitDesc implements Comparator<Opportunity> {
        public static final ByProfitDesc INSTANCE = new ByProfitDesc();

        @Override
        public int compare(Opportunity a, Opportunity b) {
            return Double.compare(b.profitPercent(), a.profitPercent());
        }
    }

    // ------------------------------------------------------------------------------------------
    // Configuración (igual que la anterior)
    // ------------------------------------------------------------------------------------------

    // <config-begin>
    @Data
    @SuperBuilder
    @EqualsAndHashCode(callSuper = true)
    public static class EngineConfiguration extends BaseConfig {
        @Builder.Default public int maxSymbols = 1500;
        @Builder.Default public double defaultFeeRate = 0.001;
        /** Monto inicial expresado en {@link #preferredStartAsset}. */
        @Builder.Default public double defaultStartAmount = 10d;
        @Builder.Default public int minCycleLength = 3;
        @Builder.Default public int maxCycleLength = 3;
        @Builder.Default public String preferredStartAsset = "USDT";

        @SuppressWarnings("unused") // Se debe declarar el constructor por el @Data
        private EngineConfiguration() {
            super();
        }
    }
    // <config-end>
}