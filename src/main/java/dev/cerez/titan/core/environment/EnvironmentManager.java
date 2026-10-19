package dev.cerez.titan.core.environment;

import dev.cerez.titan.Titan;
import dev.cerez.titan.connector.BaseConnector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.ManagerStrategy;
import dev.cerez.titan.core.ConfigNope;
import dev.cerez.titan.core.PersistentNope;
import dev.cerez.titan.core.exception.ConfigMalformatException;
import dev.cerez.titan.core.exception.ManagerIsNotFoundException;
import dev.cerez.titan.core.event.events.EnvironmentManagerListener;
import dev.cerez.titan.core.strategy.TypeManager;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.storage.StorageManagerJsonLocal;
import dev.cerez.titan.core.Manager;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.*;

@Setter
@Getter
public final class EnvironmentManager extends ManagerStrategy<ConfigNope, PersistentNope, BinanceConnector, EnvironmentManagerListener> {

    private UUID idOwner;
    private final Map<UUID, Manager<?, ?>> manager = new HashMap<>();
    private final EnvironmentPort port = new EnvironmentPort();

    private EnvironmentManager(@NotNull StorageManager storageManager, @NotNull BinanceConnector binanceConnector, @NotNull UUID idOwner) {
        super(storageManager.createProfilerManager(ConfigNope.class, PersistentNope.class), binanceConnector, TypeManager.ENVIRONMENT);
        this.idOwner = idOwner;
    }

    @Contract(pure = true)
    public static @NotNull EnvironmentManager create(@NotNull UUID idUser) {
        StorageManager storageManager = new StorageManagerJsonLocal(idUser);
        BinanceConnector binanceConnector = new BinanceConnector(
                storageManager.loadOrSaveDefault(
                        BaseConnector.ConnectorConfig.builder()
                                .isTestNet(Titan.IS_TESTNET)
                                .maxStreamsPerSubscribe(100)
                                .build()
                )
        );
        var environment = new EnvironmentManager(
                storageManager,
                binanceConnector,
                idUser
        );
        environment.start();
        return environment;
    }

    @Override
    protected void internalStart() {
        connector.start();
    }

    @Override
    protected void internalStop() {
    }

    @Override
    public @NotNull TypeManager getTypeManager() {
        return TypeManager.FUNDING_NEUTRAL;
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
                Manager<?, ?> manager = managerBuilder.manager.getConstructor(Object.class, BinanceConnector.class).newInstance(managerBuilder.config, connector);
                manager.setName(managerBuilder.name);
                EnvironmentManager.this.manager.put(uuid, manager);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
                throw new ConfigMalformatException();
            }
            return uuid;
        }

        public @NotNull Set<Manager<?, ?>> getStrategy() {
            return new HashSet<>(manager.values().stream().filter(m -> m instanceof ManagerStrategy<?,?,?,?>).toList());
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

        public record ManagerBuilder(Class<? extends Manager<?, ?>> manager, Object config, String name) {}
    }
}
