package dev.cerez.titan.discord;

import dev.cerez.titan.Titan;
import dev.cerez.titan.io.IOdata;
import dev.cerez.titan.utils.Configurable;
import dev.cerez.titan.utils.Switch;
import lombok.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@RequiredArgsConstructor
public class DiscordConnector implements Switch, Configurable<DiscordConnector.DiscordConfig> {

    private final @NotNull String token;
    @Getter
    private final @NotNull DiscordConfig config;
    private @NotNull JDA jda;
    @Getter
    private boolean running = false;
    @Getter @Setter
    private StatusProfiler statusProfiler = null;

    public DiscordConnector() {
        DiscordConfig config = IOdata.loadOrSaveConfig(DiscordConfig.builder().build());
        this.token = config.token;
        this.config = config;
    }

    @SneakyThrows
    @Override
    public void start() {
        if (running) return;
        running = true;
        jda = JDABuilder.createDefault(token).build();
        jda.awaitReady();
        Titan.getInstance().getExecutor().execute(() -> {
            while (running) { // TODO: Bug: Cuando tarda en asignar el StatusProfiler se bloquea el bucle
                LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(4));
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

    @Builder
    public static class DiscordConfig{
        @Builder.Default private String token = "";
        @Builder.Default private String userMaster = "";
    }
}
