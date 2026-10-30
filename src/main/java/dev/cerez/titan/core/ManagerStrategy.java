package dev.cerez.titan.core;

import dev.cerez.titan.connector.Connector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.event.BaseListener;
import dev.cerez.titan.core.event.Listener;
import dev.cerez.titan.core.managers.BalanceMaxRiskManager;
import dev.cerez.titan.core.strategy.TypeManager;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.storage.StorageProfiler;
import dev.cerez.titan.utils.Utils;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public abstract class ManagerStrategy<C  extends BaseConfig, P extends BasePersistent, O extends Connector, L extends BaseListener> extends BaseManager<C, P, O> {


    @Getter @NotNull private volatile StatusManager status = StatusManager.STOPING;

    @Getter @Setter @Nullable protected BalanceMaxRiskManager balanceMaxRiskManager;

    public ManagerStrategy(@NotNull StorageProfiler<C, P> storageManager,
                           @NotNull O connector,
                           @NotNull TypeManager typeManager
    ) {
        super(storageManager, connector, typeManager);
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

    @Override
    public void start(){
        setStatus(StatusManager.STARTING);
        super.start();
        if (this.status == StatusManager.STARTING) {
            setStatus(StatusManager.READY);
        }
        callEvent(BaseListener::started);
    }

    @Override
    public void stop(){
        setStatus(StatusManager.STOPING);
        super.stop();
        setStatus(StatusManager.STOPPED);
        callEvent(BaseListener::stopped);
    }

    protected void setStatus(StatusManager status){
        this.status = status;
        callEvent(BaseListener::changeStatus);
    }

    @SuppressWarnings("unchecked")
    protected void callEvent(Consumer<L> consumer){
        synchronized (listeners){
            for (BaseListener listener : listeners) {
                consumer.accept((L) listener);
            }
        }
    }
}
