package dev.cerez.titan.core.event;

import dev.cerez.titan.core.event.events.Event;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.utils.Revocable;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventManager {

    private final Map<Class<? extends Event>, CopyOnWriteArrayList<RegisteredHandler>> handlers = new ConcurrentHashMap<>();

    public Revocable registerEvents(@NotNull Listener listener) {
        for (Method method : listener.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(EventHandler.class)) continue;

            Class<? extends Event> eventClass = getEventFromMethod(method);

            method.setAccessible(true);

            EventPriority priority = method.getAnnotation(EventHandler.class).priority();
            RegisteredHandler handler = new RegisteredHandler(listener, method, priority);
            List<RegisteredHandler> handlerList = handlers.computeIfAbsent(eventClass, key -> new CopyOnWriteArrayList<>());
            handlerList.add(handler);
            handlerList.sort(Comparator.comparing(RegisteredHandler::priority));
        }
        return () -> unregisterEvents(listener);
    }

    private static @NotNull Class<? extends Event> getEventFromMethod(@NotNull Method method) {
        if (method.getParameterCount() != 1)
            throw new IllegalArgumentException(
                    "Event handler must have exactly one parameter: " + method
            );

        Class<?> parameter = method.getParameterTypes()[0];

        if (!Event.class.isAssignableFrom(parameter))
            throw new IllegalArgumentException("Event handler parameter must extend Event: " + method);

        if (method.getReturnType() != void.class)
            throw new IllegalArgumentException("Event handler must return void: " + method);

        @SuppressWarnings("unchecked")
        Class<? extends Event> eventClass = (Class<? extends Event>) parameter;
        return eventClass;
    }

    public void callEvent(Event event) {
        List<RegisteredHandler> executionList = new ArrayList<>();

        handlers.forEach((eventClass, registeredHandlers) -> {
            if (eventClass.isAssignableFrom(event.getClass())) {
                executionList.addAll(registeredHandlers);
            }
        });

        executionList.sort(Comparator.comparing(RegisteredHandler::priority));
        for (RegisteredHandler handler : executionList) {
            try {
                handler.method().invoke(handler.listener(), event);
            } catch (ReflectiveOperationException e) {
                Log.exception("Mala implementación del evento: %s en %s".formatted(event.getClass().getSimpleName(), handler.method().getName()), e);
            }catch (Exception e) {
                Log.exception("Error al llamar al evento: %s en %s".formatted(event.getClass().getSimpleName(), handler.method().getName()), e);
            }
        }
    }

    public void unregisterEvents(Listener listener) {
        handlers.values().forEach(list ->
                list.removeIf(handler -> handler.listener() == listener)
        );
        handlers.entrySet().removeIf(entry ->
                entry.getValue().isEmpty()
        );
    }

    private record RegisteredHandler(
            Listener listener,
            Method method,
            EventPriority priority
    ) {}
}