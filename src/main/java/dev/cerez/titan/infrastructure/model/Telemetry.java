package dev.cerez.titan.infrastructure.model;

import org.jetbrains.annotations.NotNull;

public record Telemetry(
        @NotNull Connector connector,
        @NotNull System system
) {
    public record Connector(int requestLastHour,
                            long useWeight,
                            long maxWeight,
                            long sPing
    ){}
    public record System(
            long totalMemory,
            long usedMemory,
            long uptime
    ){}
}
