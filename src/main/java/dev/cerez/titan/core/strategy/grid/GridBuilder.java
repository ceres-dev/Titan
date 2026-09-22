package dev.cerez.titan.core.strategy.grid;

import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.core.strategy.grid.model.Context;
import dev.cerez.titan.core.strategy.grid.model.OrderPreview;
import dev.cerez.titan.core.strategy.grid.model.SideGrid;
import dev.cerez.titan.utils.Utils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * @author Chat GPT
 */

public class GridBuilder {

    private static final BigDecimal MARGEN = new BigDecimal("0.9");

    private final GridManager.GridManagerConfig config;
    private final SideGrid grid;
    private final BigDecimal stepSize;
    private final BigDecimal amountPerOrderBaseAsset;

    public GridBuilder(GridManager.@NotNull GridManagerConfig config) {
        this.config = config;
        this.grid = config.getSideGrid();
        this.stepSize = config.getStepSize();
        this.amountPerOrderBaseAsset = config.getSizePerOrderBaseAsset();
    }


    public @NotNull List<OrderPreview> buildGrid(@NotNull Context context) {
        List<OrderPreview> result = new ArrayList<>();

        BigDecimal balanceUsdt = context.balanceUsdt();
        BigDecimal positionBaseAsset = context.positionBaseAsset();
        BigDecimal currentPrice = context.currentPrice();

        BigDecimal buyBudget  = balanceUsdt.subtract(positionBaseAsset.multiply(currentPrice)).multiply(MARGEN);
        BigDecimal sellBudget = balanceUsdt.add(positionBaseAsset.multiply(currentPrice)).multiply(MARGEN);

        // BUY
        if (shouldPlaceSide(SideOrder.BUY, positionBaseAsset)) {
            boolean reduceOnly = isReduceOnly(grid, SideOrder.BUY, positionBaseAsset);
            if (reduceOnly) {
                int maxOrders = maxReduceOnlyOrders(positionBaseAsset);
                for (int level = 0; level < maxOrders; level++) {
                    BigDecimal price = buyGridPrice(currentPrice, level);

                    result.add(newOrder(price, SideOrder.BUY, amountPerOrderBaseAsset, true));
                }
            }else {
                BigDecimal spent = BigDecimal.ZERO;
                int level = 0;
                while (true) {
                    BigDecimal price = buyGridPrice(currentPrice, level);
                    BigDecimal orderCost = price.multiply(amountPerOrderBaseAsset);
                    if (spent.add(orderCost).compareTo(buyBudget) > 0) {
                        break;
                    }
                    result.add(newOrder(price, SideOrder.BUY, amountPerOrderBaseAsset, false));
                    spent = spent.add(orderCost);
                    level++;
                }
            }
        }

        // SELL
        if (shouldPlaceSide(SideOrder.SELL, positionBaseAsset)) {
            boolean reduceOnly = isReduceOnly(grid, SideOrder.SELL,  positionBaseAsset);
            if (reduceOnly) {
                int maxOrders = maxReduceOnlyOrders(positionBaseAsset);
                for (int level = 0; level < maxOrders; level++) {
                    BigDecimal price = sellGridPrice(currentPrice, level);
                    result.add(newOrder(price, SideOrder.SELL, amountPerOrderBaseAsset, true));
                }
            }else {
                BigDecimal spent = BigDecimal.ZERO;
                int level = 0;
                while (true) {
                    BigDecimal price = sellGridPrice(currentPrice, level);
                    BigDecimal orderCost = price.multiply(amountPerOrderBaseAsset);
                    if (spent.add(orderCost).compareTo(sellBudget) > 0) {
                        break;
                    }
                    result.add(newOrder(price, SideOrder.SELL, amountPerOrderBaseAsset, false));

                    spent = spent.add(orderCost);
                    level++;
                }
            }
        }

        return result;
    }

    private boolean shouldPlaceSide(SideOrder side, BigDecimal position) {
        return switch (grid) {
            case LONG -> side == SideOrder.BUY || position.signum() > 0;
            case SHORT -> side == SideOrder.SELL || position.signum() < 0;
            case BOTH -> true;
        };
    }

    private int maxReduceOnlyOrders(@NotNull BigDecimal position) {
        BigDecimal absolutePosition = position.abs();

        return absolutePosition
                .divide(config.getSizePerOrderBaseAsset(), 0, RoundingMode.FLOOR)
                .intValue();
    }

    private @NotNull BigDecimal buyGridPrice(@NotNull BigDecimal currentPrice, int level) {
        BigDecimal base = currentPrice
                .divide(config.getStepSize(), 0, RoundingMode.FLOOR)
                .multiply(config.getStepSize());

        return base.subtract(config.getStepSize().multiply(BigDecimal.valueOf(level)));
    }

    private @NotNull BigDecimal sellGridPrice(@NotNull BigDecimal currentPrice, int level) {
        BigDecimal base = currentPrice
                .divide(config.getStepSize(), 0, RoundingMode.CEILING)
                .multiply(config.getStepSize());

        return base.add(config.getStepSize().multiply(BigDecimal.valueOf(level)));
    }

    @Contract(pure = true, value = "_, _, _, _ -> new")
    private @NotNull OrderPreview newOrder(BigDecimal price, SideOrder side, BigDecimal amountPerOrder, boolean reduce){
        return new OrderPreview(Utils.uuidToBase36(UUID.randomUUID()), price, amountPerOrder, side, reduce);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public @NotNull List<OrderPreview> validateGrid(
            @NotNull Context context,
            @NotNull List<OrderPreview> currentOrders
    ) {
        BigDecimal balanceUsdt = context.balanceUsdt();
        BigDecimal positionBaseAsset = context.positionBaseAsset();
        BigDecimal currentPrice = context.currentPrice();

        List<OrderPreview> buys = new ArrayList<>();
        List<OrderPreview> sells = new ArrayList<>();

        /*
         * Conservamos exactamente las órdenes existentes.
         * No modificamos precio, cantidad, reduceOnly, etc.
         */
        for (OrderPreview order : currentOrders) {
            if (!isSideAllowed(grid, order.getSideOrder(), positionBaseAsset)) continue;
            buysOrSells(order, buys, sells);
        }

        /*
         * Ordenamos para que el extremo que queremos eliminar
         * sea siempre el primero:
         *
         * BUY  -> menor precio primero
         * SELL -> mayor precio primero
         */
        buys.sort(Comparator.comparing(OrderPreview::getPrice));
        sells.sort(Comparator.comparing(OrderPreview::getPrice).reversed());

        BigDecimal buyBudget = getSideBudget(SideOrder.BUY, balanceUsdt, positionBaseAsset, currentPrice).multiply(MARGEN);
        BigDecimal sellBudget = getSideBudget(SideOrder.SELL, balanceUsdt, positionBaseAsset, currentPrice).multiply(MARGEN);

        /*
         * Primero quitamos lo que sobra.
         *
         * Esto es importante para permitir modificaciones manuales
         * de amountBaseAsset en órdenes existentes.
         */
        trimSide(SideOrder.BUY, buys, buyBudget, positionBaseAsset);
        trimSide(SideOrder.SELL, sells, sellBudget, positionBaseAsset);

        /*
         * Después usamos el margen/cantidad que quedó libre
         * para extender el grid desde los extremos actuales.
         */
        addSide(SideOrder.BUY, buys, buyBudget, positionBaseAsset, currentPrice);
        addSide(SideOrder.SELL, sells, sellBudget, positionBaseAsset, currentPrice);

        /*
         * Resultado final:
         *
         * BUY  -> precio más cercano primero
         * SELL -> precio más cercano primero
         */
        buys.sort(Comparator.comparing(OrderPreview::getPrice).reversed());

        sells.sort(Comparator.comparing(OrderPreview::getPrice));

        List<OrderPreview> result = new ArrayList<>(buys.size() + sells.size());
        result.addAll(buys);
        result.addAll(sells);

        return result;
    }

    private void buysOrSells(@NotNull OrderPreview order, @NotNull List<OrderPreview> buys, @NotNull List<OrderPreview> sells) {
        if (order.isBuy()) buys.add(order);
        else sells.add(order);
    }

    private boolean isSideAllowed(@NotNull SideGrid grid, SideOrder side, BigDecimal position) {
        return switch (grid) {
            case LONG -> side == SideOrder.BUY
                    || (side == SideOrder.SELL && position.signum() > 0);
            case SHORT -> side == SideOrder.SELL
                    || (side == SideOrder.BUY && position.signum() < 0);
            case BOTH -> true;
        };
    }

    private boolean isReduceOnly(@NotNull SideGrid grid, @NotNull SideOrder side, @NotNull BigDecimal position) {
        return switch (grid) {
            case LONG -> side == SideOrder.SELL;
            case SHORT -> side == SideOrder.BUY;
            case BOTH -> {
                if (side == SideOrder.BUY) {
                    yield position.signum() < 0;
                } else {
                    yield position.signum() > 0;
                }
            }
        };
    }

    private BigDecimal getSideBudget(
            @NotNull SideOrder side,
            @NotNull BigDecimal balanceUsdt,
            @NotNull BigDecimal position,
            @NotNull BigDecimal currentPrice
    ) {
        if (isReduceOnly(grid, side, position)) return BigDecimal.ZERO;
        if (side == SideOrder.BUY) {
            return balanceUsdt.subtract(position.multiply(currentPrice));
        }else {
            return balanceUsdt.add(position.multiply(currentPrice));
        }
    }

    private void trimSide(
            @NotNull SideOrder side,
            @NotNull List<OrderPreview> orders,
            @NotNull BigDecimal budgetUsdt,
            @NotNull BigDecimal positionBaseAsset
    ) {
        boolean reduceOnly = isReduceOnly(grid, side,  positionBaseAsset);
        if (reduceOnly) {
            /*
             * Una reduceOnly no puede superar la posición existente.
             */
            BigDecimal allowedAmount = positionBaseAsset.abs();
            while (sumAmount(orders).compareTo(allowedAmount) > 0 && !orders.isEmpty()) {
                orders.removeFirst();
            }
        }else {
            /*
             * Para órdenes que abren posición,
             * limitamos el margen total.
             */
            while (sumMargin(orders).compareTo(budgetUsdt) > 0 && !orders.isEmpty()) {
                orders.removeFirst();
            }
        }
    }

    private BigDecimal sumAmount(@NotNull List<OrderPreview> orders) {
        return orders.stream()
                .map(OrderPreview::getAmountBaseAsset)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumMargin(@NotNull List<OrderPreview> orders) {
        return orders.stream()
                .map(o -> o.getPrice().multiply(o.getAmountBaseAsset()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void addSide(
            @NotNull SideOrder side,
            @NotNull List<OrderPreview> orders,
            @NotNull BigDecimal budgetUsdt,
            @NotNull BigDecimal positionBaseAsset,
            @NotNull BigDecimal currentPrice) {
        boolean reduceOnly = isReduceOnly(grid, side, positionBaseAsset);

        /*
         * Comenzamos desde el extremo existente.
         * Si no hay ninguna orden, usamos currentPrice.
         */
        BigDecimal nextPrice = getNextPrice(side, orders, currentPrice);

        if (reduceOnly) {
            BigDecimal allowedAmount = positionBaseAsset.abs();
            while (sumAmount(orders).add(amountPerOrderBaseAsset).compareTo(allowedAmount) <= 0) {
                if (nextPrice.signum() <= 0) break;
                orders.add(newOrder(nextPrice, side, amountPerOrderBaseAsset, true));
                nextPrice = moveToNextLevel(side, nextPrice);
            }
        } else {
            while (true) {
                if (nextPrice.signum() <= 0) break;
                BigDecimal newMargin = nextPrice.multiply(amountPerOrderBaseAsset);
                if (sumMargin(orders).add(newMargin).compareTo(budgetUsdt) > 0) break;
                orders.add(newOrder(nextPrice, side, amountPerOrderBaseAsset, false));
                nextPrice = moveToNextLevel(side, nextPrice);
            }
        }
    }

    private @NotNull BigDecimal getNextPrice(@NotNull SideOrder side, @NotNull List<OrderPreview> orders, @NotNull BigDecimal currentPrice) {
        if (orders.isEmpty())
            return side == SideOrder.BUY ? buyGridPrice(currentPrice, 0) : sellGridPrice(currentPrice, 0);
        if (side == SideOrder.BUY) {
            BigDecimal lowest = orders.stream()
                    .map(OrderPreview::getPrice)
                    .min(BigDecimal::compareTo)
                    .orElse(currentPrice);
            return lowest.subtract(stepSize);
        } else {
            BigDecimal highest = orders.stream()
                    .map(OrderPreview::getPrice)
                    .max(BigDecimal::compareTo)
                    .orElse(currentPrice);
            return highest.add(stepSize);
        }
    }

    private @NotNull BigDecimal moveToNextLevel(@NotNull SideOrder side,  @NotNull BigDecimal price) {
        return side == SideOrder.BUY ? price.subtract(stepSize) : price.add(stepSize);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////


}
