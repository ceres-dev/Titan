package dev.cerez.titan.core;

import dev.cerez.titan.connector.Connector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.event.events.manager.ChangeStatusEvent;
import dev.cerez.titan.core.managers.BalanceMaxRiskManager;
import dev.cerez.titan.storage.StorageProfiler;
import dev.cerez.titan.utils.Revocable;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;

public abstract class StrategyManager<C  extends BaseConfig, P extends BasePersistent, O extends Connector> extends BaseManager<C, P, O> {


    @Getter @NotNull private volatile StatusManager status = StatusManager.STOPING;
    @Getter @Setter @Nullable protected BalanceMaxRiskManager balanceMaxRiskManager;
    protected final LinkedList<Revocable> subscribeLive = new LinkedList<>();
    private final Object lock = new Object();

    public StrategyManager(@NotNull StorageProfiler<C, P> storageManager,
                           @NotNull O connector
    ) {
        super(storageManager, connector);
    }

    protected @NotNull Map<String, BigDecimal> fGetBalanceAvailable(){
        if (balanceMaxRiskManager == null){
            if (connector instanceof BinanceConnector binanceConnector) {
                return binanceConnector.fGetBalanceAvailable();
            }else {
                return Collections.emptyMap();
            }
        }else {
            return balanceMaxRiskManager.futuroBalanceAvailable(this);
        }
    }

    protected @NotNull Map<String, BigDecimal> fGetBalanceValued(){
        if (balanceMaxRiskManager == null){
            if (connector instanceof BinanceConnector binanceConnector) {
                return binanceConnector.fGetBalanceValued();
            }else {
                return Collections.emptyMap();
            }
        }else {
            return balanceMaxRiskManager.futuroBalanceValued(this);
        }
    }

    protected @NotNull BinanceConnector.BalanceFuture fGetBalance(){
        if (balanceMaxRiskManager == null){
            if (connector instanceof BinanceConnector binanceConnector) {
                return binanceConnector.fGetBalance();
            }else {
                return new BinanceConnector.BalanceFuture(Collections.emptyMap(), Collections.emptyMap());
            }
        }else {
            return balanceMaxRiskManager.futuroBalance(this);
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
        synchronized (lock) {
            setStatus(StatusManager.STARTING);
            super.start();
            if (this.status == StatusManager.STARTING) {
                setStatus(StatusManager.READY);
            }
        }
    }

    @Override
    public void stop(){
        synchronized (lock) {
            setStatus(StatusManager.STOPING);
            super.stop();
            subscribeLive.forEach(Revocable::revoke);
            subscribeLive.clear();
            setStatus(StatusManager.STOPPED);
        }
    }

    protected synchronized void setStatus(StatusManager status){
        this.status = status;
        callEvent(new ChangeStatusEvent(this, status));
    }
}
