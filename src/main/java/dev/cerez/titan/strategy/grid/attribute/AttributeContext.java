package dev.cerez.titan.strategy.grid.attribute;

import dev.cerez.titan.strategy.grid.model.Context;
import dev.cerez.titan.strategy.grid.model.OrderPreview;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public record AttributeContext(@NotNull List<OrderPreview> allOrderUse,
                               @NotNull @Unmodifiable List<OrderPreview> buys,
                               @NotNull @Unmodifiable List<OrderPreview> sells,
                               @NotNull Context context) {

    public List<OrderPreview> ordersResult() {
        return allOrderUse;
    }

}
