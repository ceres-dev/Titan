package dev.cerez.titan.core.event;

import dev.cerez.titan.core.event.events.Event;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.utils.Revocable;
import dev.cerez.titan.utils.Utils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class EventManager {

    private final Map<Class<? extends Event>, CopyOnWriteArrayList<RegisteredHandler<? extends Event>>> handlers = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(6, Utils.getThreadFactory(this));

    public Revocable registerListener(@NotNull Listener listener) {
        List<? extends RegisteredListenerHandler<? extends Event>> handlerList = Arrays.stream(listener.getClass().getDeclaredMethods()).map((method) -> {
            if (!method.isAnnotationPresent(EventHandler.class)) return null;
            Class<? extends Event> eventClass = getEventFromMethod(method);
            method.setAccessible(true);

            EventPriority priority = method.getAnnotation(EventHandler.class).priority();
            RegisteredListenerHandler<? extends Event> handler = new RegisteredListenerHandler<>(priority, eventClass, listener, method);
            addHandler(eventClass, handler);
            return handler;
        }).filter(Objects::nonNull).toList();
        return () -> handlerList.forEach(this::unregisterHandler);
    }

    public <E extends Event> Revocable registerListener(@NotNull Class<E> eventClass, @NotNull Consumer<E> consumer) {
        return registerListener(eventClass, consumer, EventPriority.NORMAL);
    }

    public <E extends Event> Revocable registerListener(@NotNull Class<E> eventClass, @NotNull Consumer<E> consumer, @NotNull EventPriority priority) {
        RegisteredHandler<? extends Event> handler = new RegisteredConsumerHandler<>(priority, eventClass, consumer);
        addHandler(eventClass, handler);
        return () -> unregisterHandler(handler);
    }

    public void unregisterEvents(Listener listener) {
        removeHandlers(handler -> handler instanceof RegisteredListenerHandler<?> h && h.getListener() == listener);
    }

    public void unregisterEvents(Consumer<? extends Event> consumer) {
        removeHandlers(handler ->  handler instanceof RegisteredConsumerHandler<?> h && h.getConsumer() == consumer);
    }

    private void addHandler(Class<? extends Event> eventClass, RegisteredHandler<? extends Event> handler) {
        handlers.compute(eventClass, (key, list) -> {
            if (list == null) list = new CopyOnWriteArrayList<>();
            list.add(handler);
            list.sort(Comparator.comparing(RegisteredHandler::getPriority));
            return list;
        });
    }

    private void removeHandlers(Predicate<RegisteredHandler<? extends Event>> predicate) {
        handlers.forEach((eventClass, ignored) ->
                handlers.computeIfPresent(eventClass, (key, list) -> {
                    list.removeIf(predicate);
                    return list.isEmpty() ? null : list;
                })
        );
    }

    private void unregisterHandler(@NotNull RegisteredHandler<? extends Event> handler) {
        handlers.computeIfPresent(handler.getEventClass(), (key, list) -> {
            list.removeIf(current -> current == handler);
            return list.isEmpty() ? null : list;
        });
    }

    public void callEvent(Event event) {
        List<RegisteredHandler<? extends Event>> executionList = new ArrayList<>();

        handlers.forEach((eventClass, registeredHandlers) -> {
            if (eventClass.isAssignableFrom(event.getClass())) {
                executionList.addAll(registeredHandlers);
            }
        });
        executionList.sort(Comparator.comparing(RegisteredHandler::getPriority));
        for (RegisteredHandler<? extends Event> handler : executionList) {
            switch (handler){
                case RegisteredListenerHandler<? extends Event> listenerHandler -> {
                    try {
                        listenerHandler.getMethod().invoke(listenerHandler.getListener(), event);
                    } catch (InvocationTargetException e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        Log.exception(
                                "Event handler error: event=%s method=%s".formatted(
                                        event.getClass().getSimpleName(),
                                        listenerHandler.getMethod().getName()
                                ), cause);
                    } catch (ReflectiveOperationException e) {
                        Log.exception("Error invoking event handler", e);
                    }catch (Exception e) {
                        Log.exception("El método arrojo una exception event=%s method=%s".formatted(
                                event.getClass().getSimpleName(),
                                listenerHandler.getMethod().getName()
                        ), e);
                    }
                }
                case RegisteredConsumerHandler<? extends Event> listenerHandler -> {
                    try {
                        listenerHandler.accept(event);
                    }catch (ClassCastException e){
                        Log.exception("Evento incorrecto event=%s consumer=%s".formatted(
                                event.getClass().getSimpleName(),
                                listenerHandler.getEventClass().getSimpleName()
                        ) , e);
                    }catch (Exception e) {
                        Log.exception("El consumer arrojo una exception event=%s comsumer=%s".formatted(
                                event.getClass().getSimpleName(),
                                listenerHandler.getEventClass().getName()
                        ), e);
                    }
                }
                default -> throw new IllegalArgumentException("Event handler must implement RegisteredConsumerHandler: " + handler);
            }
        }
    }

    private static @NotNull Class<? extends Event> getEventFromMethod(@NotNull Method method) {
        if (method.getParameterCount() != 1) throw new IllegalArgumentException("Event handler must have exactly one parameter: " + method);
        Class<?> parameter = method.getParameterTypes()[0];
        if (!Event.class.isAssignableFrom(parameter)) throw new IllegalArgumentException("Event handler parameter must extend Event: " + method);
        if (method.getReturnType() != void.class) throw new IllegalArgumentException("Event handler must return void: " + method);

        @SuppressWarnings("unchecked")
        Class<? extends Event> eventClass = (Class<? extends Event>) parameter;
        return eventClass;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    private static class RegisteredListenerHandler<E extends Event> extends RegisteredHandler<E>{
        private final Listener listener;
        private final Method method;

        public RegisteredListenerHandler(EventPriority priority, Class<E> eventClass, Listener listener, Method method) {
            super(priority, eventClass);
            this.listener = listener;
            this.method = method;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    private static class RegisteredConsumerHandler<E extends Event> extends RegisteredHandler<E>{
        private final Consumer<E> consumer;

        public RegisteredConsumerHandler(EventPriority priority, Class<E> eventClass, Consumer<E> consumer) {
            super(priority, eventClass);
            this.consumer = consumer;
        }

        public void accept(Event event) throws ClassCastException {
            consumer.accept(eventClass.cast(event));
        }
    }

    @Data
    @RequiredArgsConstructor
    private abstract static class RegisteredHandler<E> {
        protected final EventPriority priority;
        protected final Class<E> eventClass;
    }
}