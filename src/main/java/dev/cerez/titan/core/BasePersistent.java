package dev.cerez.titan.core;

import dev.cerez.titan.utils.Nameable;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class BasePersistent implements Nameable {

    private String name = "default";
}
