package dev.cerez.titan.strategy.grid.model;

import dev.cerez.titan.connector.model.SideOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.Contract;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public final class OrderPreview {
    private BigDecimal price;
    private SideOrder sideOrder;
    private BigDecimal amountBaseAsset;
    private boolean reduceOnly;
    private final String nameOrder;

    @Contract(pure = true)
    public boolean isBuy() {
        return sideOrder == SideOrder.BUY;
    }

    @Contract(pure = true)
    public boolean isSell() {
        return sideOrder == SideOrder.SELL;
    }
}