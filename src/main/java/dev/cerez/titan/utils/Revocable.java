package dev.cerez.titan.utils;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.ScheduledFuture;

/**
 * Revoca la escucha de un evento, subscription o rutina
 *
 * @see Subscribable
 */
public interface Revocable {

    void revoke();

    @Contract(value = "_ -> new", pure = true)
    static @NonNull Revocable from(ScheduledFuture<?> scheduledFuture){
        return () -> scheduledFuture.cancel(false);
    }

}
