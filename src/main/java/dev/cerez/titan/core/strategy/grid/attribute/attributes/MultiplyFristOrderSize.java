package dev.cerez.titan.core.strategy.grid.attribute.attributes;

import dev.cerez.titan.core.strategy.grid.attribute.AttributeContext;
import dev.cerez.titan.core.strategy.grid.attribute.AttributeMultiplySize;
import dev.cerez.titan.core.strategy.grid.attribute.SideAffected;
import dev.cerez.titan.core.strategy.grid.model.Context;
import dev.cerez.titan.core.strategy.grid.model.OrderPreview;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class MultiplyFristOrderSize extends AttributeMultiplySize {

    private final BiFunction<List<OrderPreview>, Context, Integer> multiply;
    public MultiplyFristOrderSize(SideAffected sideAffected, BiFunction<List<OrderPreview>, Context, Integer> multiplier) {
        super(sideAffected);
        this.multiply = multiplier;
    }

    @Override
    public @NotNull List<OrderPreview> apply(@NotNull AttributeContext context) {
        if (!context.orderUse().isEmpty()) {
            OrderPreview order = context.orderUse().getFirst();
            order.setAmountBaseAsset(order.getAmountBaseAsset().multiply(new BigDecimal(multiply.apply(context.ordersResult(), context.context()))));
        }
        return context.ordersResult();

    }
}
