package dev.cerez.titan.utils;

import dev.cerez.titan.connector.model.SideOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.Contract;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public abstract class Order {
    protected final String nameOrder;
    protected BigDecimal price;
    protected BigDecimal amountBaseAsset;
    protected SideOrder sideOrder;
    protected boolean reduceOnly;

    @Contract(pure = true)
    public boolean isBuy() {
        return sideOrder == SideOrder.BUY;
    }

    @Contract(pure = true)
    public boolean isSell() {
        return sideOrder == SideOrder.SELL;
    }
}
