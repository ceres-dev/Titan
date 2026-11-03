package dev.cerez.titan.connector.model.order;

import java.math.BigDecimal;

public record OrderSpotResult(
        String orderId,
        BigDecimal baseAssetQty,
        BigDecimal quoteAssetQty,
        BigDecimal receivedQty
) {

}
