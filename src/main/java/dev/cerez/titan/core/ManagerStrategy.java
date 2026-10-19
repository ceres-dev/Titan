package dev.cerez.titan.core;

import dev.cerez.titan.connector.Connector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.event.Listener;
import dev.cerez.titan.core.managers.BalanceMaxRiskManager;
import dev.cerez.titan.core.strategy.TypeManager;
import dev.cerez.titan.storage.StorageProfiler;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

public abstract class ManagerStrategy<C  extends BaseConfig, P extends BasePersistent, O extends Connector, L extends Listener> extends BaseManager<C, P, O> {

    @Getter @NotNull protected final Set<L> listeners = new HashSet<>();

    @Getter @Setter @Nullable protected BalanceMaxRiskManager balanceMaxRiskManager;

    public ManagerStrategy(@NotNull StorageProfiler<C, P> storageManager,
                           @NotNull O connector,
                           @NotNull TypeManager typeManager
    ) {
        super(storageManager, connector, typeManager);
    }

    public void registerListener(L listener){
        listeners.add(listener);
    }

    protected Map<String, BigDecimal> fGetBalance(){
        if (balanceMaxRiskManager == null){
            if (connector instanceof BinanceConnector binanceConnector) {
                return binanceConnector.fGetBalance();
            }else {
                return Collections.emptyMap();
            }
        }else {
            return balanceMaxRiskManager.futuroBalance(this);
        }
    }

    protected Map<String, BigDecimal> fGetBalanceTotal(){
        if (balanceMaxRiskManager == null){
            if (connector instanceof BinanceConnector binanceConnector) {
                return binanceConnector.fGetBalanceTotal();
            }else {
                return Collections.emptyMap();
            }
        }else {
            return balanceMaxRiskManager.futuroBalanceTotal(this);
        }
    }

    protected Map<String, BigDecimal> sGetBalance(){
        if (balanceMaxRiskManager == null){
            return connector.sGetBalance();
        }else {
            return balanceMaxRiskManager.spotBalance(this);
        }
    }

    protected void callEvent(Consumer<L> consumer){
        listeners.forEach(consumer);
    }
}
