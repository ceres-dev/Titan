package dev.cerez.titan.triangular.engine.model;

import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@Getter
@ToString
public class NameAsset {
    private final String name;
    private final int hashPrimitive;
    private final Integer hashObject;

    private static int i;

    @Contract(pure = true)
    public NameAsset(@NotNull String name) {
        this.name = name;
        this.hashPrimitive = name.hashCode();
        this.hashObject = hashPrimitive;
    }

    public Integer cacheInteger = -1;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof NameAsset nameAsset) {
            return nameAsset.hashCode() == this.hashCode();
        }
        return false;
    }

}
