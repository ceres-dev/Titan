package dev.cerez.titan.log;

import ch.qos.logback.core.AppenderBase;
import dev.cerez.titan.utils.Revocable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public class ConsumerAppender<E> extends AppenderBase<E> {

    private final ConcurrentMap<UUID, Consumer<E>> consumers = new ConcurrentHashMap<>();

    @Override
    protected void append(E eventObject) {
        for (Consumer<E> consumer : consumers.values()) consumer.accept(eventObject);
    }

    public Revocable addConsumer(Consumer<E> consumer) {
        UUID uuid = UUID.randomUUID();
        consumers.put(uuid, consumer);
        return () -> consumers.remove(uuid);
    }
}
