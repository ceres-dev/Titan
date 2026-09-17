package dev.cerez.titan.strategy.grid.model;

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
