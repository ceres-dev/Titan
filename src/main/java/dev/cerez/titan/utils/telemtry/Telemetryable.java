package dev.cerez.titan.utils.telemtry;

import dev.cerez.titan.utils.Snapshot;

public interface Telemetryable<S extends Snapshot<TelemetryCollector.TelemetrySnapshot>> {

    void setTelemetry(S telemetry);
}
