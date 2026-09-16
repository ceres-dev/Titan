package dev.cerez.titan.connector.model;

import dev.cerez.titan.utils.Side;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

@Getter
@RequiredArgsConstructor
public enum SideOrder implements Side<SideOrder> {
    BUY,
    SELL;

    @Override
    public SideOrder inverse() {
        return BUY == this ? SELL : BUY;
    }
}
