package dev.cerez.titan.utils.telemtry;

import dev.cerez.titan.connector.BaseConnector;
import dev.cerez.titan.utils.Snapshot;

public interface TelemetryConnector extends Snapshot<Telemetry.TelemetrySnapshot> {

    void addRequestConnector(BaseConnector.Method method, String finalUrl);

    void setCurrentDeltaDelayPingPongNanoTime(long deltaNanoTime);
}
