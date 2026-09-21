package dev.cerez.titan.core.strategy.grid.attribute;

import dev.cerez.titan.core.strategy.grid.model.OrderPreview;
import lombok.Data;
import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

@Data
public abstract class BaseAttribute {

    private final SideAffected sideAffected;
    @Nullable @Getter
    private Function<AttributeContext, Boolean> condition;

    public abstract @NotNull List<OrderPreview> apply(@NotNull AttributeContext context);

    @Contract(value = "_ -> this")
    public BaseAttribute addCondicion(Function<AttributeContext, Boolean> condition) {
        this.condition = condition;
        return this;
    }
}
