package dev.cerez.titan.storage.exception;

public class IllegalProfilerNameException extends StorageManagerException {

    public IllegalProfilerNameException(String profilerName) {
        super("Illegal Profiler Name: %s, Regex Allow: ^[a-zA-Z0-9]+$".formatted(profilerName));
    }

}
