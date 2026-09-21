package dev.cerez.titan.core.strategy.grid.attribute.attributes;

import dev.cerez.titan.core.strategy.grid.attribute.AttributeContext;
import dev.cerez.titan.core.strategy.grid.attribute.AttributePriceLimit;
import dev.cerez.titan.core.strategy.grid.attribute.SideAffected;
import dev.cerez.titan.core.strategy.grid.model.Context;
import dev.cerez.titan.core.strategy.grid.model.OrderPreview;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

public class DontSendOrder extends AttributePriceLimit {

    private final boolean inProfit;

    public DontSendOrder(SideAffected sideAffected, Function<Context, BigDecimal> price, boolean inProfit) {
        super(sideAffected, price);
        this.inProfit = inProfit;
    }

    @Override
    public @NotNull List<OrderPreview> apply(@NotNull AttributeContext context) {
        context.orderUse().removeIf((o) -> {
            BigDecimal price = getPrice().apply(context.context());
            if (o.isBuy()){
                if (inProfit) return o.getPrice().compareTo(price) < 0;
                else return o.getPrice().compareTo(price) > 0;
            }else {
                if (inProfit) return o.getPrice().compareTo(price) > 0;
                else return o.getPrice().compareTo(price) < 0;
            }
        });
        return context.ordersResult();
    }
}
