package dev.cerez.titan.storage;

import dev.cerez.titan.storage.exception.IllegalProfilerNameException;
import dev.cerez.titan.storage.exception.IsRootProfilerException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class BaseStorageManager implements StorageManager {

    private static final String REGEX_PROFILER_NAME = "^[a-zA-Z0-9]+$";
    private String fullProfilerName = "";
    private final ConcurrentHashMap<String, StorageManager> subProfilers = new ConcurrentHashMap<>();
    @Nullable
    private StorageManager superProfiler = null;

    protected void concatBaseNameProfiler(@NotNull String fullProfilerName){
        this.fullProfilerName = fullProfilerName;
    }

    @Override
    public String getBaseNameProfiler() {
        return fullProfilerName;
    }

    @Override
    public @NotNull BaseStorageManager createSubProfiler(@NotNull String subProfilerName){
        if (!subProfilerName.matches(REGEX_PROFILER_NAME)){
            throw new IllegalProfilerNameException(subProfilerName);
        }
        BaseStorageManager manager = copy();
        manager.concatBaseNameProfiler(getFullProfilerName(subProfilerName));
        manager.superProfiler = this;
        subProfilers.putIfAbsent(subProfilerName, manager);
        return manager;
    }

    @Override
    public @NotNull StorageManager getSuperProfiler(){
        if (superProfiler == null) throw new IsRootProfilerException();
        return superProfiler;
    }

    @Override
    public @NotNull Set<StorageManager> getSubProfiler(){
        return Set.copyOf(subProfilers.values());
    }

    @Contract(pure = true, value = "-> new")
    protected abstract BaseStorageManager copy();
}
