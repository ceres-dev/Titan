package dev.cerez.titan.core;

import dev.cerez.titan.connector.Connector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.event.Listener;
import dev.cerez.titan.core.strategy.BalanceRiskManager;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.core.strategy.Manager;
import dev.cerez.titan.core.event.SupplierEvent;
import dev.cerez.titan.utils.Provider;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

public abstract class BaseManager<C extends BaseConfig, P, O extends Connector, L extends Listener> implements Manager<C>, SupplierEvent<L> {

    @NotNull private final Provider<C> configProvider;
    @NotNull private final Provider<P> persistenceProvider;
    @Getter @NotNull protected final O connector;
    @Getter @NotNull protected final UUID id = UUID.randomUUID();
    @Getter @NotNull protected final StorageManager storageManager;
    @Getter @NotNull protected final Set<L> listeners = new HashSet<>();

    @Getter @Setter @NotNull protected String name = id.toString();
    @Getter @Setter @Nullable protected BalanceRiskManager balanceRiskManager;
    @Getter @Setter(value = AccessLevel.NONE) protected boolean running = false;

    public BaseManager(@NotNull Provider<C> config,
                       @NotNull Class<P> persistenceClazz,
                       @NotNull O connector,
                       @NotNull StorageManager storageManager
    ) {
        this.configProvider = config; // storageManager.getProviderOrSaveConfig(configDefault);
        this.persistenceProvider = storageManager.getPersistenceProvider(persistenceClazz);
        this.connector = connector;
        this.storageManager = storageManager;
    }

    public void registerListener(L listener){
        listeners.add(listener);
    }

    protected void callEvent(Consumer<L> consumer){
        listeners.forEach(consumer);
    }

    private C cacheConfig = null;
    private P cachePersistence = null;

    public @NotNull C getConfig() {
        return Objects.requireNonNullElse(cacheConfig, cacheConfig = this.configProvider.get());
    }

    public P getPersistence() {
        if (cachePersistence == null){
            return cachePersistence = this.persistenceProvider.get();
        }else {
            return cachePersistence;
        }
    }

    @Override
    public void saveConfig() {
        cacheConfig = null;
        storageManager.saveConfig(getConfig());
    }

    @Override
    public void savePersistence(){
        cachePersistence = null;
        storageManager.savePersistence(getConfig());
    }

    protected void saveConfig(C config) {
        storageManager.saveConfig(config);
    }

    protected void savePersistence(P persistence){
        storageManager.savePersistence(persistence);
    }

    protected Map<String, BigDecimal> fGetBalance(){
        if (balanceRiskManager == null){
            if (connector instanceof BinanceConnector binanceConnector) {
                return binanceConnector.fGetBalance();
            }else {
                return Collections.emptyMap();
            }
        }else {
            return balanceRiskManager.futuroBalance(this);
        }
    }

    protected Map<String, BigDecimal> fGetBalanceTotal(){
        if (balanceRiskManager == null){
            if (connector instanceof BinanceConnector binanceConnector) {
                return binanceConnector.fGetBalance();
            }else {
                return Collections.emptyMap();
            }
        }else {
            return balanceRiskManager.futuroBalanceTotal(this);
        }
    }
    protected Map<String, BigDecimal> sGetBalance(){
        if (balanceRiskManager == null){
            return connector.sGetBalance();
        }else {
            return balanceRiskManager.spotBalance(this);
        }
    }


}
