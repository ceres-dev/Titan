package dev.cerez.titan.strategy.grid.attribute.attributes;

import dev.cerez.titan.strategy.grid.attribute.AttributeMultiplySize;
import dev.cerez.titan.strategy.grid.attribute.AttributeContext;
import dev.cerez.titan.strategy.grid.attribute.SideAffected;
import dev.cerez.titan.strategy.grid.model.OrderPreview;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MultiplyLastOrderSize extends AttributeMultiplySize {
    public MultiplyLastOrderSize(SideAffected sideAffected) {
        super(sideAffected);
    }

    @Override
    public @NotNull List<OrderPreview> apply(@NotNull AttributeContext context) {
        return List.of();
    }
}
