package dev.cerez.titan.core.event;

import com.google.errorprone.annotations.Keep;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHandler {

    EventPriority priority() default EventPriority.NORMAL;
}