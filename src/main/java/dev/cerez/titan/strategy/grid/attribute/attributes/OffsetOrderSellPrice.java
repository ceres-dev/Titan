package dev.cerez.titan.strategy.grid.attribute.attributes;

import dev.cerez.titan.strategy.grid.attribute.AttributeContext;
import dev.cerez.titan.strategy.grid.attribute.BaseAttribute;
import dev.cerez.titan.strategy.grid.attribute.SideAffected;
import dev.cerez.titan.strategy.grid.model.OrderPreview;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;

public class OffsetOrderSellPrice extends BaseAttribute {

    private final BigDecimal offset;

    public OffsetOrderSellPrice(BigDecimal offsetPrice) {
        super(SideAffected.BOTH);
        this.offset = offsetPrice;
    }

    @Override
    public @NotNull List<OrderPreview> apply(@NotNull AttributeContext context) {
        for (OrderPreview order : context.sells()) {
            order.setPrice(order.getPrice().add(offset));
        }
        return context.ordersResult();
    }
}
