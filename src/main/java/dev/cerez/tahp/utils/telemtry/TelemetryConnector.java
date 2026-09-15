package dev.cerez.tahp.utils.telemtry;

import dev.cerez.tahp.connector.BaseConnector;
import dev.cerez.tahp.utils.Snapshot;

public interface TelemetryConnector extends Snapshot<Telemetry.TelemetrySnapshot> {

    void addRequestConnector(BaseConnector.Method method, String finalUrl);

    void setCurrentDeltaDelayPingPongNanoTime(long deltaNanoTime);
}
