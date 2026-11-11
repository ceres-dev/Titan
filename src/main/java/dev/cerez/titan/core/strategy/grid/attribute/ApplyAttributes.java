package dev.cerez.titan.core.strategy.grid.attribute;

import dev.cerez.titan.connector.model.order.OrderPreview;
import dev.cerez.titan.core.strategy.grid.GridBuilder;
import dev.cerez.titan.core.strategy.grid.attribute.attributes.CallOnUpdate;
import dev.cerez.titan.core.strategy.grid.model.Context;
import dev.cerez.titan.core.strategy.grid.model.SideGrid;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.utils.Utils;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

@RequiredArgsConstructor
public class ApplyAttributes {

    @NotNull private final List<BaseAttribute> attributes = new LinkedList<>();
    @NotNull private final GridBuilder gridBuilder;

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

    public @NotNull List<OrderPreview> apply(@NotNull Context context, boolean computeOrderOnly) {
        List<OrderPreview> orders = new LinkedList<>(context.getPreviewOrders());
        for (BaseAttribute attribute : attributes) {
            if (attribute instanceof CallOnUpdate && computeOrderOnly){
                continue;
            }
            Function<AttributeContext, Boolean> condition = attribute.getCondition();
            List<OrderPreview> buys = orders.stream().filter(OrderPreview::isBuy).sorted(Comparator.comparing(OrderPreview::getPrice).reversed()).toList();
            List<OrderPreview> sells = orders.stream().filter(OrderPreview::isSell).sorted(Comparator.comparing(OrderPreview::getPrice)).toList();
            AttributeContext attributeContext;

            List<OrderPreview> use = new ArrayList<>(orders.size());
            List<OrderPreview> unuse = new ArrayList<>(orders.size());
            SideGrid sideGrid = context.getConfig().getSideGrid();
            SideAffected sideAffected = attribute.getSideAffected();

            if (sideAffected == SideAffected.BOTH || sideGrid == SideGrid.BOTH){
                use.addAll(buys);
                use.addAll(sells);
            }else {
                if (sideAffected == SideAffected.FAVOR) {
                    if (sideGrid == SideGrid.LONG) {
                        use.addAll(buys);
                        unuse.addAll(sells);
                    }else {
                        use.addAll(sells);
                        unuse.addAll(buys);
                    }
                }else {
                    if (sideGrid == SideGrid.LONG) {
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
            try {
                Set<OrderPreview> result = new HashSet<>(attribute.apply(attributeContext));
                orders = gridBuilder.validateGrid(context, result.stream().toList());
            }catch (Exception e){
                Log.exception(e);
                return List.of();
            }
        }
        return orders;
    }

    public void clear() {
        attributes.clear();
    }
}
