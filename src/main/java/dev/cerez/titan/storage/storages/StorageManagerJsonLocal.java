package dev.cerez.titan.storage.storages;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.core.BaseConfig;
import dev.cerez.titan.core.BasePersistent;
import dev.cerez.titan.storage.BaseStorageManager;
import dev.cerez.titan.storage.LocalStorageManager;
import dev.cerez.titan.storage.Storage;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.utils.Nameable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class StorageManagerJsonLocal extends LocalStorageManager {

    private static final String EXTENSION = ".json";
    private static final String BASE_PATH = "userData";

    private final Path path;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public StorageManagerJsonLocal(@NotNull UUID idUser) {
        super(idUser);
        this.path = Paths.get(BASE_PATH, idUser.toString());
        File file = new File(path.toFile().toURI());
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    @Override
    public @NotNull Storage<BaseConfig> buildConfig(@NotNull Class<BaseConfig> clazz, @NotNull String profilerName) {
        return new StorageJsonLocal<>(clazz, profilerName);
    }

    @Override
    public @NotNull Storage<BasePersistent> buildPersistent(@NotNull Class<BasePersistent> clazz,  @NotNull String profilerName) {
        return new StorageJsonLocal<>(clazz, profilerName);
    }

    @Override
    public BaseStorageManager copy() {
        return new StorageManagerJsonLocal(getIdUser());
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    public class StorageJsonLocal<O extends Nameable> implements Storage<O> {

        private final Class<O> clazz;
        private final String profilerName;

        @Override
        public void save(@NotNull O persistence, @NotNull String nameProfiler) {
            try (FileWriter writer = new FileWriter(path.resolve(persistence.getClass().getSimpleName().concat(SYMBOL_SEPARATOR).concat(getFullProfilerName(nameProfiler)).concat(EXTENSION)).toFile())) {
                gson.toJson(persistence, writer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public O load(@NotNull String nameProfiler) {
            try (FileReader reader = new FileReader(
                    path.resolve(
                            clazz.getSimpleName().concat(SYMBOL_SEPARATOR).concat(getFullProfilerName(nameProfiler)).concat(EXTENSION)
                    ).toFile())
            ) {
                return gson.fromJson(reader, clazz);
            } catch (IOException e) {
                Log.exception(e);
                return null;
            }
        }
    }

}
