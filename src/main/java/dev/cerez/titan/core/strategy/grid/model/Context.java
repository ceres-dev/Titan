package dev.cerez.titan.core.strategy.grid.model;

import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.connector.model.BookTick;
import dev.cerez.titan.connector.model.StatusOrder;
import dev.cerez.titan.connector.model.order.OrderFuture;
import dev.cerez.titan.connector.model.order.OrderPreview;
import dev.cerez.titan.core.strategy.grid.GridManager;
import dev.cerez.titan.utils.FinalRefence;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

@Data
public final class Context {
    private final @NotNull BigDecimal balanceUsdt;
    private final @NotNull BigDecimal currentPrice;
    private final @NotNull BookTick currentBookTicker;
    private final GridManager.@NotNull GridManagerConfig config;
    private final BinanceConnector.@Nullable FuturePosition position;
    private final @NotNull List<OrderFuture> allOrders;
    private final @NotNull List<OrderPreview> previewOrders;
    private final HashMap<String, Object> attributes = new HashMap<>();
    @Getter(AccessLevel.NONE)
    private transient final FinalRefence<List<OrderFuture>> ordersActive = new FinalRefence<>();

    @Contract(pure = true)
    public @NotNull BigDecimal positionBaseAsset() {
        return position == null
                ? BigDecimal.ZERO
                : position.quantity();
    }

    public @NotNull @Unmodifiable List<OrderFuture> getActiveOrders(){
        return ordersActive.getIfAbsent(allOrders.stream().filter(o -> o.getStatusOrder() == StatusOrder.NEW).toList());
    }
    @Contract(pure = true, value = "_ -> new")
    public @NotNull Context appendOrderPreview(@NotNull List<OrderPreview> orderPreview){
        return new Context(
                balanceUsdt,
                currentPrice,
                currentBookTicker,
                config,
                position,
                allOrders,
                orderPreview
        );
    }
}
