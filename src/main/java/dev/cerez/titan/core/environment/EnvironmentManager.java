package dev.cerez.titan.core.environment;

import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.BaseManager;
import dev.cerez.titan.core.environment.exception.AssetNotExitsException;
import dev.cerez.titan.core.environment.exception.ConfigMalformatException;
import dev.cerez.titan.core.environment.exception.ManagerIsNotFoundException;
import dev.cerez.titan.core.event.events.EnvironmentManagerEvent;
import dev.cerez.titan.utils.Config;
import dev.cerez.titan.utils.Manager;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.*;

@Getter
public class EnvironmentManager extends BaseManager<EnvironmentManager.EnvironmentManagerConfig, BinanceConnector, EnvironmentManagerEvent> {

    private Map<UUID, Manager<?>> mamanger = new HashMap<>();
    private EventManagerPort port = new EventManagerPort();

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

    @Data
    @Builder
    public static class EnvironmentManagerConfig implements Config {

    }

    public class EventManagerPort {

        public void stopManager(@NotNull UUID id) throws ManagerIsNotFoundException {
            try {
                mamanger.get(id).stop();
            }catch (NullPointerException e) {
                throw new ManagerIsNotFoundException();
            }
        }

        public void startManager(@NotNull UUID id) throws ManagerIsNotFoundException {
            try {
                mamanger.get(id).start();
            }catch (NullPointerException e) {
                throw new ManagerIsNotFoundException();
            }
        }

        public UUID createManager(@NotNull ManagerBuilder managerBuilder) throws ManagerIsNotFoundException  {
            UUID uuid = UUID.randomUUID();
            try {
                Manager<?> manager = managerBuilder.manager.getConstructor(Object.class, BinanceConnector.class).newInstance(managerBuilder.config, connector);
                manager.setName(managerBuilder.name);
                mamanger.put(uuid, manager);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
                throw new ConfigMalformatException();
            }
            return uuid;
        }

        public @NotNull Set<UUID> getManagerIds() {
            return new HashSet<>(mamanger.keySet());
        }

        public long getLocalTimestamp() {
            return System.currentTimeMillis();
        }

        public long getRemoteTimestamp() {
            return connector.getTimeSever();
        }

        public long getPing(){
            return connector.fPing();
        }

        public BigDecimal getFutureBalance(@NotNull String asset) throws AssetNotExitsException {
            try {
                return connector.fGetBalance().get(asset);
            }catch (NullPointerException e) {
                throw new AssetNotExitsException();
            }
        }

        public List<BinanceConnector.FuturePosition> getPositions() throws AssetNotExitsException {
            return connector.fGetPositions();
        }

        public record ManagerBuilder(Class<? extends Manager<?>> manager, Object config, String name) {}
    }
}
