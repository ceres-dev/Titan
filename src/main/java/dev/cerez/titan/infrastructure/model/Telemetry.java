package dev.cerez.titan.infrastructure.model;

import org.jetbrains.annotations.NotNull;

public record Telemetry(
        @NotNull Connector connector,
        @NotNull System system
) {
    public record Connector(int requestLastHour){}
    public record System(
            long totalMemory,
            long usedMemory
    ){}
}
