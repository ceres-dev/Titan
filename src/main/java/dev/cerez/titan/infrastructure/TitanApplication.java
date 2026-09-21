package dev.cerez.titan.infrastructure;

import dev.cerez.titan.utils.Switch;
import lombok.Getter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class TitanApplication implements Switch {

    @Getter
    private boolean running = false;
    private ConfigurableApplicationContext context = null;

    @Override
    public void start() {
        if (running) return;
        running = true;
        context = SpringApplication.run(TitanApplication.class, "");
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        SpringApplication.exit(context);
    }
}
