package dev.cerez.titan.strategy.grid.attribute;

import dev.cerez.titan.strategy.grid.model.Context;
import dev.cerez.titan.strategy.grid.model.OrderPreview;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;

public record AttributeContext(@NotNull List<OrderPreview> orderUse,
                               @NotNull List<OrderPreview> orderAgainst,
                               @NotNull @Unmodifiable List<OrderPreview> buys,
                               @NotNull @Unmodifiable List<OrderPreview> sells,
                               @NotNull Context context) {

    public @NotNull List<OrderPreview> ordersResult() {
        List<OrderPreview> orders = new ArrayList<>(orderUse.size() + orderAgainst.size());
        orders.addAll(orderUse);
        orders.addAll(orderAgainst);
        return orders;
    }

}
