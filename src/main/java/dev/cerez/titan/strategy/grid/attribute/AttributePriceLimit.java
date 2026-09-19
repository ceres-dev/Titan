package dev.cerez.titan.strategy.grid.attribute;

import dev.cerez.titan.strategy.grid.model.Context;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.function.Function;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class AttributePriceLimit extends BaseAttribute {

    private final Function<Context, BigDecimal> price;

    public AttributePriceLimit(SideAffected sideAffected, Function<Context, BigDecimal>  price) {
        super(sideAffected);
        this.price = price;
    }
}
