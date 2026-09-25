package dev.cerez.titan.core.strategy.grid.attribute.attributes;

import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.core.strategy.grid.GridManager;
import dev.cerez.titan.core.strategy.grid.attribute.AttributeContext;
import dev.cerez.titan.core.strategy.grid.attribute.BaseAttribute;
import dev.cerez.titan.core.strategy.grid.attribute.SideAffected;
import dev.cerez.titan.core.strategy.grid.model.OrderPreview;
import dev.cerez.titan.utils.Utils;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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
        GridManager.GridManagerConfiguration config = context.context().config();
        Utils.getMin(context.sells()).ifPresent(order -> {
            if (order.getPrice().subtract(config.getStepSize()).compareTo(context.context().currentPrice()) >= 0) {
                context.orderUse().add(new OrderPreview(Utils.uuidToBase36(UUID.randomUUID()),
                        order.getPrice().subtract(config.getStepSize()),
                        config.getSizePerOrderBaseAsset(),
                        SideOrder.SELL,
                        order.isReduceOnly()
                ));
            }
        });
        return context.ordersResult();
    }
}
