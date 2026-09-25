package dev.cerez.titan.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.cerez.titan.Log;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class StorageManagerJsonLocal implements StorageManager {

    private static final String EXTENSION = ".json";

    @Getter
    private final UUID idUser;
    private final Path path;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public StorageManagerJsonLocal(@NotNull UUID idUser) {
        this.idUser = idUser;
        this.path = Paths.get("userData", idUser.toString());
        File file = new File(path.toFile().toURI());
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    @Override
    public <P> void savePersistence(@NonNull P persistence) {
        try (FileWriter writer = new FileWriter(path.resolve(persistence.getClass().getSimpleName() + EXTENSION).toFile())) {
            gson.toJson(persistence, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <P> PersistenceProvider<P> getPersistenceProvider(Class<P> p) {
        return () -> {
            try (FileReader reader = new FileReader(path.resolve(p.getSimpleName() + EXTENSION).toFile())) {
                return gson.fromJson(reader, p);
            } catch (IOException e) {
                Log.exception(e);
                return null;
            }
        };
    }

    @Override
    public <C> void saveConfig(@NotNull C config) {
        try (FileWriter writer = new FileWriter(path.resolve(config.getClass().getSimpleName() + EXTENSION).toFile())) {
            gson.toJson(config, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <C> ConfigurationProvider<C> getConfigProvider(Class<C> c) {
        return () -> {
            try (FileReader reader = new FileReader(path.resolve(c.getSimpleName() + EXTENSION).toFile())) {
                return gson.fromJson(reader, c);
            } catch (IOException e) {
                Log.exception(e);
                return null;
            }
        };
    }

}
