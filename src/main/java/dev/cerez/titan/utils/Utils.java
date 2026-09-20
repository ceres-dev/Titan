package dev.cerez.titan.utils;

import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.strategy.grid.GridManager;
import dev.cerez.titan.strategy.grid.model.SideGrid;
import dev.cerez.titan.strategy.grid.model.SidePosition;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;

@UtilityClass
public class Utils {


    public @NotNull String uuidToBase36(UUID uuid) {
        BigInteger value = uuidToBigInteger(uuid);
        return value.toString(36);
    }

    private @NotNull BigInteger uuidToBigInteger(@NotNull UUID uuid) {
        return BigInteger.valueOf(uuid.getMostSignificantBits())
                .shiftLeft(64)
                .or(BigInteger.valueOf(uuid.getLeastSignificantBits())
                        .and(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)));
    }

    @Contract("_ -> new")
    public static @NotNull BigDecimal toDecimal(int value) {
        return BigDecimal.valueOf(Math.pow(10, -value));
    }

    public boolean isEqualSide(SideOrder side1, SidePosition side2){
        return (side1 == SideOrder.BUY && side2 == SidePosition.LONG) || (side1 == SideOrder.SELL && side2 == SidePosition.SHORT);
    }

    public SidePosition toPosition(BigDecimal value) {
        if (value.signum() == 1){
            return SidePosition.LONG;
        }
        if (value.signum() == -1){
            return SidePosition.SHORT;
        }
        return SidePosition.NOTHING;
    }

    public SideOrder toSide(BigDecimal value) {
        if (value.signum() == 1){
            return SideOrder.BUY;
        }
        if (value.signum() == -1){
            return SideOrder.SELL;
        }
        throw new IllegalArgumentException();
    }

    public SideOrder toSide(SideGrid sideGrid) {
        if (sideGrid == SideGrid.LONG) {
            return SideOrder.BUY;
        }
        if (sideGrid == SideGrid.SHORT) {
            return SideOrder.SELL;
        }
        throw new IllegalArgumentException();
    }

    public SideOrder toSide(SidePosition  sidePosition) {
        if (sidePosition == SidePosition.LONG) {
            return SideOrder.BUY;
        }
        if (sidePosition == SidePosition.SHORT) {
            return SideOrder.SELL;
        }
        throw new IllegalArgumentException();
    }

    public ThreadFactory getThreadFactory() {
        String className = StackWalker.getInstance()
                .walk(stack -> stack
                        .skip(1)
                        .findFirst()
                        .map(StackWalker.StackFrame::getClassName)
                        .orElse("Unknown"));

        return Thread.ofVirtual()
                .name(className + "-", 0)
                .factory();
    }

}
