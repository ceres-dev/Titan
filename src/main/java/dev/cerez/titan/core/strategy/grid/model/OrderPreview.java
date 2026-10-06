package dev.cerez.titan.core.strategy.grid.model;

import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.utils.Order;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
@ToString(callSuper = true)
public final class OrderPreview extends Order {

    public OrderPreview(String symbol, String nameOrder, BigDecimal price, BigDecimal amountBaseAsset, SideOrder sideOrder, boolean reduceOnly) {
        super(symbol, nameOrder, price, amountBaseAsset, sideOrder, reduceOnly);
    }
}