package dev.cerez.titan.strategy.grid.attribute.attributes;

import dev.cerez.titan.strategy.grid.attribute.AttributeContext;
import dev.cerez.titan.strategy.grid.attribute.BaseAttribute;
import dev.cerez.titan.strategy.grid.attribute.SideAffected;
import dev.cerez.titan.strategy.grid.model.Context;
import dev.cerez.titan.strategy.grid.model.OrderPreview;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BiFunction;

public class RemoveIf extends BaseAttribute {

    private final BiFunction<OrderPreview, Context, Boolean> removeIf;

    public RemoveIf(BiFunction<OrderPreview, Context, Boolean> removeIf) {
        super(SideAffected.BOTH);
        this.removeIf = removeIf;
    }

    @Override
    public @NotNull List<OrderPreview> apply(@NotNull AttributeContext context) {
        context.orderUse().removeIf(o -> removeIf.apply(o, context.context()));
        return context.ordersResult();
    }
}
