package dev.cerez.titan.utils;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class TemporalRefence<R>  {

    private final TimeUnit unit;
    private final long time;

    private R reference;
    private long dateSetting;
    @Setter
    private Provider<R> provider;

    public void set(@NotNull R r){
        this.reference = r;
        this.dateSetting = System.currentTimeMillis();
    }

    public @Nullable R get(){
        return reference != null && System.currentTimeMillis() > unit.toMillis(time) + dateSetting ? null : reference;
    }

    public @NotNull R getOrDefault(@NotNull R r){
        return get() == null ? r : this.reference;
    }

    public @NotNull R getOrCompute(){
        if (get() == null) {
            this.dateSetting = System.currentTimeMillis();
            return this.reference = provider.apply();
        }
        return this.reference;
    }

    public @NotNull R getOrCompute(Provider<R> provider){
        if (get() == null) {
            this.dateSetting = System.currentTimeMillis();
            return this.reference = provider.apply();
        }
        return this.reference;
    }
}
