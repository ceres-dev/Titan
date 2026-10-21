package dev.cerez.titan.log;

import ch.qos.logback.core.AppenderBase;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class ConsumerAppender<E> extends AppenderBase<E> {

    private final Set<Consumer<E>> consumers = Collections.synchronizedSet(new HashSet<>());

    @Override
    protected void append(E eventObject) {
        for (Consumer<E> consumer : consumers) consumer.accept(eventObject);
    }

    public void addConsumer(Consumer<E> consumer) {
        consumers.add(consumer);
    }
}
