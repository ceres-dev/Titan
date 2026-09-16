package dev.cerez.titan.grid.model;

import dev.cerez.titan.connector.model.SideOrder;
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
