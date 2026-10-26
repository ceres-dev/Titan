package dev.cerez.titan.core.strategy.grid.attribute;

import dev.cerez.titan.connector.model.OrderPreview;

import java.util.List;

public record AttributeResult(List<OrderPreview> orders) {
}
