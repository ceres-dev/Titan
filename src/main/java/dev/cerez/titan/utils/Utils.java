package dev.cerez.titan.utils;

import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.connector.model.StatusOrder;
import dev.cerez.titan.core.Manager;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.core.strategy.grid.model.SideGrid;
import dev.cerez.titan.core.strategy.grid.model.SidePosition;
import dev.cerez.titan.infrastructure.user.custom.TitanUserDetails;
import dev.cerez.titan.log.Log;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.WebSocketSession;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ThreadFactory;
import java.util.function.BiFunction;
import java.util.function.Consumer;

@UtilityClass
public class Utils {


    public @NotNull String uuidToBase36(UUID uuid) {
        BigInteger value = uuidToBigInteger(uuid);
        return value.toString(36);
    }

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz/+";
    private static final BigInteger BASE = BigInteger.valueOf(64);

    public @NotNull String uuidToBase64(@NotNull UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        BigInteger number = new BigInteger(1, bb.array());

        StringBuilder sb = new StringBuilder();
        while (number.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divideAndRemainder = number.divideAndRemainder(BASE);
            int remainder = divideAndRemainder[1].intValue();
            sb.append(ALPHABET.charAt(remainder));
            number = divideAndRemainder[0];
        }
        return sb.reverse().toString();
    }

    @Contract("_ -> new")
    public static @NonNull UUID base64ToUuid(@NotNull String base64Str) {
        byte[] bytes = getBytes(base64Str);
        byte[] uuidBytes = new byte[16];

        // Ajustar el array de bytes si es más corto o más largo debido al signo
        if (bytes.length <= 16) {
            System.arraycopy(bytes, 0, uuidBytes, 16 - bytes.length, bytes.length);
        } else {
            // Si trae un byte extra de signo (0x00) al inicio, lo ignoramos
            System.arraycopy(bytes, bytes.length - 16, uuidBytes, 0, 16);
        }

        // Reconstruir los longs para el UUID
        ByteBuffer byteBuffer = ByteBuffer.wrap(uuidBytes);
        long mostSigBits = byteBuffer.getLong();
        long leastSigBits = byteBuffer.getLong();

        return new UUID(mostSigBits, leastSigBits);
    }

    private static byte @NonNull [] getBytes(String base64Str) {
        BigInteger number = BigInteger.ZERO;

        // Reconstruir el BigInteger multiplicando por la base (64)
        for (int i = 0; i < base64Str.length(); i++) {
            char c = base64Str.charAt(i);
            int digit = ALPHABET.indexOf(c);
            if (digit == -1) {
                throw new IllegalArgumentException("Carácter inválido en la cadena: " + c);
            }
            number = number.multiply(BASE).add(BigInteger.valueOf(digit));
        }

        // Convertir el BigInteger de vuelta a un array de 16 bytes
        byte[] bytes = number.toByteArray();
        return bytes;
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

    public @NotNull ThreadFactory getThreadFactory(@NotNull Object o, @Nullable EnvironmentManager environment) {
        String id = Utils.uuidToBase64(UUID.randomUUID());
        if (environment != null){
            return Thread.ofVirtual()
                    .name(id + "-" + Utils.uuidToBase64(environment.getIdOwner()) + "-" + o.getClass().getSimpleName() + "-", 0)
                    .factory();
        }else {
            return Thread.ofVirtual()
                    .name(id + "-" + o.getClass().getSimpleName() + "-", 0)
                    .factory();
        }
    }

    public @NotNull ThreadFactory getThreadFactory(@NotNull Object o) {
        return getThreadFactory(o, null);
    }

    public String getClassNameCallPrevious(){
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(stack -> stack
                        .filter(frame -> frame.getDeclaringClass() != Utils.class)
                        .findFirst()
                        .map(StackWalker.StackFrame::getDeclaringClass)
                        .map(Class::getName)
                        .orElse("Unknown")
                        .replaceFirst("^dev\\.cerez\\.titan\\.", "")
                );
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

    public <M extends Map<K, V>, K, V> M removeIf(@NotNull M map, @NotNull BiFunction<K, V, Boolean> filter) {
        map.entrySet().removeIf(entry -> filter.apply(entry.getKey(), entry.getValue()));
        return map;
    }

    @Getter
    private static final UUID rootId = UUID.fromString("00000000-0000-0000-0000-000000000000");
    
    public static boolean isValidUUID(String input) {
        if (input == null) return false;
        try {
            UUID.fromString(input);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String cleanAnsi(String input) {
        return input.replaceAll("\u001B\\[[;\\d]*m", "");
    }

    public void printTraceStack(){
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement stackTraceElement : stackTraceElements){
            sb.append("\t").append(stackTraceElement.toString()).append("\n");
        }
        Log.info(sb.toString());
    }

    public @Nullable TitanUserDetails toTitanUser(@NotNull WebSocketSession webSocketSession){
        if (webSocketSession.getPrincipal() instanceof Authentication authentication) {
            Object user = authentication.getPrincipal();

            if (user instanceof TitanUserDetails customUser) {
                return customUser;
            }
        }
        return null;
    }
}
