package dev.cerez.titan.connector.model.order;

import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.connector.model.StatusOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
public final class SpotOrder extends OrderExecute {
    private final @NotNull BigDecimal amountQuoteAsset;

    public SpotOrder(@NotNull String symbol,
                     @NotNull String nameOrder,
                     @NotNull BigDecimal price,
                     @NotNull BigDecimal amountBaseAsset,
                     @NotNull BigDecimal amountQuoteAsset,
                     @NotNull SideOrder sideOrder,
                     boolean reduceOnly,
                     StatusOrder statusOrder,
                     long dateCreate,
                     long dateFilled
    ) {
        super(symbol, nameOrder, price, amountBaseAsset, sideOrder, reduceOnly, statusOrder,  dateCreate, dateFilled);
        this.amountQuoteAsset = amountQuoteAsset;
    }
}