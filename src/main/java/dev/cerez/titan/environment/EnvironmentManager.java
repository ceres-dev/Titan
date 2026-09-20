package dev.cerez.titan.environment;

import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.utils.Manager;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor
public class EnvironmentManager implements Manager<EnvironmentManager.EnvironmentManagerConfig> {

    @Getter
    private final EnvironmentManagerConfig config;
    @Getter
    private boolean running = false;
    @Getter
    private final BinanceConnector connector;

    private Set<Manager<?>> mamanger = new HashSet<>();

    @Override
    public void start() {
        if (running) return;
        running = true;
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
    }

    @Data
    @Builder
    public static class EnvironmentManagerConfig {

    }
}
