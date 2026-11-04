package dev.cerez.titan.connector.model.order;

import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.connector.model.StatusOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
public final class OrderFuture extends OrderExecute {

    public OrderFuture(String symbol,
                       String nameOrder,
                       BigDecimal price,
                       BigDecimal amountBaseAsset,
                       SideOrder sideOrder,
                       boolean reduceOnly,
                       StatusOrder statusOrder,
                       long dateCreate,
                       long dateFilled
    ) {
        super(symbol, nameOrder, price, amountBaseAsset, sideOrder, reduceOnly, statusOrder, dateCreate, dateFilled);
    }
}
