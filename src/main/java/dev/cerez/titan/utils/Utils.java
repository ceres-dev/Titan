package dev.cerez.titan.utils;

import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.connector.model.StatusOrder;
import dev.cerez.titan.core.strategy.grid.model.SideGrid;
import dev.cerez.titan.core.strategy.grid.model.SidePosition;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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

    public SideOrder toSide(SidePosition sidePosition) {
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

    public @NotNull @Unmodifiable List<BinanceConnector.OrderFuture> filterFilled(@NotNull List<BinanceConnector.OrderFuture> orders) {
        return orders.stream().filter(o -> o.getStatusOrder() == StatusOrder.FILLED).toList();
    }

    public @NotNull @Unmodifiable List<BinanceConnector.OrderFuture> filterNew(@NotNull List<BinanceConnector.OrderFuture> orders) {
        return orders.stream().filter(o -> o.getStatusOrder() == StatusOrder.NEW).toList();
    }

    public <T extends Order> @NotNull @Unmodifiable List<T> filterBuy(@NotNull List<T> orders) {
        return orders.stream().filter(Order::isBuy).toList();
    }

    public <T extends Order> @NotNull @Unmodifiable List<T> filterSell(@NotNull List<T> orders) {
        return orders.stream().filter(Order::isSell).toList();
    }

    public <T extends Order> @NotNull @Unmodifiable Optional<T> getMin(@NotNull List<T> orders) {
        return orders.stream().min(Comparator.comparing(Order::getPrice));
    }

    public <T extends Order> @NotNull @Unmodifiable Optional<T> getMax(@NotNull List<T> orders) {
        return orders.stream().max(Comparator.comparing(Order::getPrice));
    }

    @Getter
    private static final UUID rootId = UUID.fromString("00000000-0000-0000-0000-000000000000");
}
