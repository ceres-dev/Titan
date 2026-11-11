package dev.cerez.titan.connector.model.order;

import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.connector.model.StatusOrder;
import dev.cerez.titan.utils.Utils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@ToString(callSuper = true)
public final class OrderPreview extends Order {

    public OrderPreview(String symbol, BigDecimal price, BigDecimal amountBaseAsset, SideOrder sideOrder, boolean reduceOnly) {
        super(symbol, Utils.uuidToBase36(UUID.randomUUID()), price, amountBaseAsset, sideOrder, reduceOnly);
    }
    @Contract(pure = true)
    public @NotNull StatusOrder getStatusOrder(){
        return StatusOrder.NEW;
    }
}