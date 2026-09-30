package dev.cerez.titan.utils;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.*;

@RequiredArgsConstructor
public class TemporalRefence<R>  {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(Utils.getThreadFactory());
    private final TimeUnit unit;
    private final long time;

    private volatile R reference;
    private volatile boolean isUseble = true;
    private Future<Boolean> isUsebleFuture = CompletableFuture.completedFuture(true);

    @Setter
    private Provider<R> provider;

    public R set(@NotNull R r){
        this.reference = r;
        if (!isUsebleFuture.isDone()) isUsebleFuture.cancel(true);
        isUsebleFuture = executor.schedule(() -> isUseble = false, time, unit);
        return reference;
    }

    public @Nullable R get(){
        if (isUseble && reference != null) {
            return reference;
        }else {
            return null;
        }
    }

    public @NotNull R getOrDefault(@NotNull R r){
        return get() == null ? r : this.reference;
    }

    public @NotNull R getOrCompute(){
        if (get() == null) {
            return set(provider.get());
        }
        return this.reference;
    }

    public @NotNull R getOrCompute(Provider<R> provider){
        if (get() == null) {
            return set(provider.get());
        }
        return this.reference;
    }

    public void delete() {
        isUseble = false;
        isUsebleFuture.cancel(true);
    }
}
