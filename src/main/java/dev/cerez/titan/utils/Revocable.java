package dev.cerez.titan.utils;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.ScheduledFuture;

public interface Revocable {

    void revoke();

    @Contract(value = "_ -> new", pure = true)
    static @NonNull Revocable from(ScheduledFuture<?> scheduledFuture){
        return new Revocable() {
            @Override
            public void revoke() {
                scheduledFuture.cancel(false);
            }
        };
    }

}
