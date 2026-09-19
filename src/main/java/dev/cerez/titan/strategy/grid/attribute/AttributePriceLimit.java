package dev.cerez.titan.strategy.grid.attribute;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class AttributePriceLimit extends BaseAttribute {

    private final BigDecimal price;

    public AttributePriceLimit(SideAffected sideAffected, BigDecimal price) {
        super(sideAffected);
        this.price = price;
    }
}
