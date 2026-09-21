package dev.cerez.titan.core.environment;

import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.utils.BaseManager;
import dev.cerez.titan.utils.Manager;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class EnvironmentManager extends BaseManager<EnvironmentManager.EnvironmentManagerConfig, BinanceConnector> {

    private Map<UUID, Manager<?>> mamanger = new HashMap<>();

    public EnvironmentManager(@NotNull EnvironmentManager.EnvironmentManagerConfig config, @NotNull BinanceConnector connector) {
        super(config, connector);
    }

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

    public void addManager(@NotNull Manager<?> manager) {
        mamanger.put(manager.getId(), manager);
    }

    public void removeManager(@NotNull Manager<?> manager) {
        mamanger.remove(manager.getId());
    }

    public Manager<?> getManager(@NotNull UUID id) {
        return mamanger.get(id);
    }

    public Set<Manager<?>> getManagers() {
        return new HashSet<>(mamanger.values());
    }

    @Data
    @Builder
    public static class EnvironmentManagerConfig {

    }
}
