package dev.cerez.titan.strategy.grid.attribute;

import dev.cerez.titan.strategy.grid.GridBuilder;
import dev.cerez.titan.strategy.grid.GridManager;
import dev.cerez.titan.strategy.grid.model.Context;
import dev.cerez.titan.strategy.grid.model.OrderPreview;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

@RequiredArgsConstructor
public class ApplyAttributes {

    private final List<BaseAttribute> attributes = new LinkedList<>();
    private final GridBuilder gridBuilder;

    @Contract(value = "_ -> this")
    public @NotNull ApplyAttributes add(@NotNull BaseAttribute attribute) {
        attributes.add(attribute);
        return this;
    }

    @Contract(value = "_ -> this")
    public @NotNull ApplyAttributes add(BaseAttribute @NotNull ... attributes) {
        for (BaseAttribute attribute : attributes) add(attribute);
        return this;
    }

    public @NotNull List<OrderPreview> apply(Context context, List<OrderPreview> originalOrderPreviews) {
        List<OrderPreview> orders = new LinkedList<>(originalOrderPreviews);
        for (BaseAttribute attribute : attributes) {
            Function<AttributeContext, Boolean> condition = attribute.getCondition();
            List<OrderPreview> buys = orders.stream().filter(OrderPreview::isBuy).sorted(Comparator.comparing(OrderPreview::getPrice).reversed()).toList();
            List<OrderPreview> sells = orders.stream().filter(OrderPreview::isSell).sorted(Comparator.comparing(OrderPreview::getPrice)).toList();
            AttributeContext attributeContext;

            List<OrderPreview> use = new ArrayList<>(orders.size());
            List<OrderPreview> unuse = new ArrayList<>(orders.size());
            GridManager.SideGrid sideGrid = context.config().getSideGrid();
            SideAffected sideAffected = attribute.getSideAffected();

            if (sideAffected == SideAffected.BOTH || sideGrid == GridManager.SideGrid.BOTH){
                use.addAll(buys);
                use.addAll(sells);
            }else {
                if (sideAffected == SideAffected.FAVOR) {
                    if (sideGrid == GridManager.SideGrid.LONG) {
                        use.addAll(buys);
                        unuse.addAll(sells);
                    }else {
                        use.addAll(sells);
                        unuse.addAll(buys);
                    }
                }else {
                    if (sideGrid == GridManager.SideGrid.LONG) {
                        use.addAll(sells);
                        unuse.addAll(buys);
                    }else {
                        use.addAll(buys);
                        unuse.addAll(sells);
                    }
                }
            }
            attributeContext = new AttributeContext(use, unuse, buys, sells, context);

            if (condition != null){
                if (!condition.apply(attributeContext)){
                    continue;
                }
            }
            // Set para evitar duplicado
            Set<OrderPreview> result = new HashSet<>(attribute.apply(attributeContext));
            orders = gridBuilder.validateGrid(context, result.stream().toList());
        }
        return orders;
    }

    public void clear() {
        attributes.clear();
    }
}
