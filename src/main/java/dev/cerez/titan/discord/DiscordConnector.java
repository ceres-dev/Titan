package dev.cerez.titan.discord;

import dev.cerez.titan.core.BaseConfig;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.utils.Configurable;
import dev.cerez.titan.utils.Switch;
import dev.cerez.titan.utils.Utils;
import lombok.*;
import lombok.experimental.SuperBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class DiscordConnector implements Switch, Configurable<DiscordConnector.DiscordConfig> {

    @Getter @NotNull private final DiscordConfig config;
    @Getter private boolean running = false;
    @Getter @Setter private StatusProfiler statusProfiler = null;
    private final ExecutorService executorService = Executors.newScheduledThreadPool(2, Utils.getThreadFactory(this));
    private JDA jda;


    public DiscordConnector(@NotNull StorageManager storageManager) {
        this.config = storageManager.getConfig(DiscordConfig.class).getProvider().get();
    }

    @SneakyThrows
    @Override
    public void start() {
        if (running) return;
        running = true;
        jda = JDABuilder.createDefault(config.token).build();
        jda.awaitReady();
        executorService.execute(() -> {
            while (running) {
                LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));
                if (statusProfiler == null) continue;
                StatusProfiler.PresenceProfile presenceProfiler = statusProfiler.getPresenceProfile();
                jda.getPresence().setStatus(presenceProfiler.onlineStatus());
                jda.getPresence().setActivity(presenceProfiler.activity());

            }
        });
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        jda.shutdown();
    }

    public void sendMessage(String message) {
        User user = jda.retrieveUserById(config.userMaster).complete();
        user.openPrivateChannel().queue(channel -> {
            channel.sendMessage(message).queue();
        });
    }

    @Data
    @SuperBuilder
    @EqualsAndHashCode(callSuper = true)
    public static class DiscordConfig extends BaseConfig {
        @Builder.Default private String token = "";
        @Builder.Default private String userMaster = "";
    }
}
