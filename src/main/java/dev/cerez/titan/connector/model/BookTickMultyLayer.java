package dev.cerez.titan.connector.model;

import java.math.BigDecimal;
import java.util.List;

public record BookTickMultyLayer(String symbol, List<Layer> bids, List<Layer> asks) {

    public BigDecimal totalAmountBaseBid(){
        BigDecimal totalAmount = BigDecimal.ZERO;
        for(Layer layer : bids) totalAmount = totalAmount.add(layer.amount());
        return totalAmount;
    }

    public BigDecimal totalAmountBaseAsk(){
        BigDecimal totalAmount = BigDecimal.ZERO;
        for(Layer layer : asks) totalAmount = totalAmount.add(layer.amount());
        return totalAmount;
    }

    public BigDecimal totalAmountQuoteBid(){
        BigDecimal totalAmount = BigDecimal.ZERO;
        for(Layer layer : bids) totalAmount = totalAmount.add(layer.price().multiply(layer.amount()));
        return totalAmount;
    }

    public BigDecimal totalAmountQuoteAsk(){
        BigDecimal totalAmount = BigDecimal.ZERO;
        for(Layer layer : asks) totalAmount = totalAmount.add(layer.price().multiply(layer.amount()));
        return totalAmount;
    }


    public record Layer(BigDecimal price, BigDecimal amount){}
}
