package dev.cerez.titan.core.environment;

import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.BaseManager;
import dev.cerez.titan.core.ConfigNope;
import dev.cerez.titan.core.PersistenceNope;
import dev.cerez.titan.core.exception.ConfigMalformatException;
import dev.cerez.titan.core.exception.ManagerIsNotFoundException;
import dev.cerez.titan.core.event.events.EnvironmentManagerListener;
import dev.cerez.titan.core.strategy.TypeManager;
import dev.cerez.titan.io.StorageManager;
import dev.cerez.titan.io.StorageManagerJsonLocal;
import dev.cerez.titan.core.strategy.Manager;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.*;

@Setter
@Getter
public class EnvironmentManager extends BaseManager<ConfigNope, PersistenceNope, BinanceConnector, EnvironmentManagerListener> {

    private UUID idOwner;
    private final Map<UUID, Manager<?>> manager = new HashMap<>();
    private final EnvironmentPort port = new EnvironmentPort();

    private EnvironmentManager(@NotNull ConfigNope config, @NotNull BinanceConnector binanceConnector, @NotNull StorageManager storageManager, @NotNull UUID idOwner) {
        super(config, PersistenceNope.class, binanceConnector, storageManager);
        this.idOwner = idOwner;
    }

    @Contract(pure = true)
    public static @NotNull EnvironmentManager create(@NotNull UUID idUser) {
        BinanceConnector binanceConnector = new BinanceConnector();
        StorageManager storageManager = new StorageManagerJsonLocal(idUser);
        var environment = new EnvironmentManager(
                new ConfigNope(),
                binanceConnector,
                storageManager,
                idUser
        );
        environment.start();
        return environment;
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        connector.start();
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
    }

    @Override
    public @NotNull TypeManager getTypeManager() {
        return TypeManager.FUNDING;
    }

    public class EnvironmentPort {

        public void stopManager(@NotNull UUID id) throws ManagerIsNotFoundException {
            try {
                manager.get(id).stop();
            }catch (NullPointerException e) {
                throw new ManagerIsNotFoundException();
            }
        }

        public void startManager(@NotNull UUID id) throws ManagerIsNotFoundException {
            try {
                manager.get(id).start();
            }catch (NullPointerException e) {
                throw new ManagerIsNotFoundException();
            }
        }

        public UUID createManager(@NotNull ManagerBuilder managerBuilder) throws ManagerIsNotFoundException  {
            UUID uuid = UUID.randomUUID();
            try {
                Manager<?> manager = managerBuilder.manager.getConstructor(Object.class, BinanceConnector.class).newInstance(managerBuilder.config, connector);
                manager.setName(managerBuilder.name);
                EnvironmentManager.this.manager.put(uuid, manager);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
                throw new ConfigMalformatException();
            }
            return uuid;
        }

        public @NotNull Set<Manager<?>> getManagers() {
            return new HashSet<>(manager.values());
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

        public Map<String, BigDecimal> getFutureBalance() {
            return connector.fGetBalance();
        }

        public Map<String, BigDecimal> getSpotBalance() {
            return connector.sGetBalance();
        }

        public List<BinanceConnector.FuturePosition> getPositions() {
            return connector.fGetPositions();
        }

        public record ManagerBuilder(Class<? extends Manager<?>> manager, Object config, String name) {}
    }
}
