package dev.cerez.titan.core.strategy.grid.model;

import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.utils.Order;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.Contract;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
@ToString(callSuper = true)
public final class OrderPreview extends Order {

    public OrderPreview(String nameOrder, BigDecimal price, BigDecimal amountBaseAsset, SideOrder sideOrder, boolean reduceOnly) {
        super(nameOrder, price, amountBaseAsset, sideOrder, reduceOnly);
    }
}