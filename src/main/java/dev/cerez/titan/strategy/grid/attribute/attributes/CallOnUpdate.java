package dev.cerez.titan.strategy.grid.attribute.attributes;

import dev.cerez.titan.strategy.grid.attribute.AttributeContext;
import dev.cerez.titan.strategy.grid.attribute.BaseAttribute;
import dev.cerez.titan.strategy.grid.attribute.SideAffected;
import dev.cerez.titan.strategy.grid.model.Context;
import dev.cerez.titan.strategy.grid.model.OrderPreview;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CallOnUpdate extends BaseAttribute {

    private final BiConsumer<List<OrderPreview>, Context> onUpdate;

    public CallOnUpdate(BiConsumer<List<OrderPreview>, Context> consumer) {
        super(SideAffected.BOTH);
        this.onUpdate = consumer;
    }

    @Override
    public @NotNull List<OrderPreview> apply(@NotNull AttributeContext context) {
        onUpdate.accept(context.ordersResult(), context.context());
        return context.ordersResult();
    }
}
