package dev.cerez.tahp.utils.telemtry;

import dev.cerez.tahp.utils.Snapshot;

public interface Telemetryable<S extends Snapshot<Telemetry.TelemetrySnapshot>> {

    void setTelemetry(S telemetry);
}
