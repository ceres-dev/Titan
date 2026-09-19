package dev.cerez.titan.strategy.grid.attribute.attributes;

import dev.cerez.titan.strategy.grid.attribute.AttributePriceLimit;
import dev.cerez.titan.strategy.grid.attribute.AttributeContext;
import dev.cerez.titan.strategy.grid.attribute.SideAffected;
import dev.cerez.titan.strategy.grid.model.OrderPreview;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;

public class DontSendOrderLosses extends AttributePriceLimit {
    public DontSendOrderLosses(SideAffected sideAffected, BigDecimal price) {
        super(sideAffected, price);
    }

    @Override
    public @NotNull List<OrderPreview> apply(@NotNull AttributeContext context) {
        return List.of();
    }
}
