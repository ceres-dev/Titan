package dev.cerez.titan.core.strategy.grid.model;

import dev.cerez.titan.utils.Side;

public enum SidePosition implements Side<SidePosition> {
    LONG,
    SHORT,
    NOTHING;

    @Override
    public SidePosition inverse() {
        return this == LONG ? SHORT : LONG;
    }
}
