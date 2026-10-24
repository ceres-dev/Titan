package dev.cerez.titan;

import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.utils.Switch;
import dev.cerez.titan.utils.Utils;
import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Getter
@SpringBootApplication
public class TitanApplication implements Switch {

    private static final TitanApplication INSTANCE = new TitanApplication();

    @Getter(AccessLevel.NONE)
    private ConfigurableApplicationContext context = null;
    private boolean running = false;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(16, Utils.getThreadFactory(this));
    private final Map<UUID, EnvironmentManager> environmentManagers = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        INSTANCE.start();
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        Log.info("""
                                                          ___           ___    \s
                      ___       ___           ___        /  /\\         /__/\\   \s
                     /  /\\     /  /\\         /  /\\      /  /::\\        \\  \\:\\  \s
                    /  /:/    /  /:/        /  /:/     /  /:/\\:\\        \\  \\:\\ \s
                   /  /:/    /__/::\\       /  /:/     /  /:/~/::\\   _____\\__\\:\\\s
                  /  /::\\    \\__\\/\\:\\__   /  /::\\    /__/:/ /:/\\:\\ /__/::::::::\\
                 /__/:/\\:\\      \\  \\:\\/\\ /__/:/\\:\\   \\  \\:\\/:/__\\/ \\  \\:\\~~\\~~\\/
                 \\__\\/  \\:\\      \\__\\::/ \\__\\/  \\:\\   \\  \\::/       \\  \\:\\  ~~~\s
                      \\  \\:\\     /__/:/       \\  \\:\\   \\  \\:\\        \\  \\:\\    \s
                       \\__\\/     \\__\\/         \\__\\/    \\  \\:\\        \\  \\:\\   \s
                                                         \\__\\/         \\__\\/   \s
                """);

        context = SpringApplication.run(TitanApplication.class, "");
        Log.configure();
        getEnvironmentManager(Utils.getRootId());
    }

    public EnvironmentManager getEnvironmentManager(UUID idUser) {
        return this.environmentManagers.computeIfAbsent(idUser, EnvironmentManager::create);
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        SpringApplication.exit(context);
        System.exit(0);
    }

    @Contract(pure = true)
    @NotNull
    @Bean
    @Primary
    public static TitanApplication getInstance(){
        return INSTANCE;
    }
}
