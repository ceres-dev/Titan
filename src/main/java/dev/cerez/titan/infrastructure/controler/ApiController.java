package dev.cerez.titan.infrastructure.controler;

import dev.cerez.titan.Titan;
import dev.cerez.titan.core.environment.EnvironmentManager;
import dev.cerez.titan.infrastructure.model.Balance;
import dev.cerez.titan.infrastructure.model.Strategy;
import dev.cerez.titan.infrastructure.model.Time;
import dev.cerez.titan.infrastructure.user.custom.CustomUserDetails;
import dev.cerez.titan.utils.TemporalRefence;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class ApiController {

    public final Titan titan = Titan.getInstance();

    public TemporalRefence<Balance> balance = new TemporalRefence<>(TimeUnit.SECONDS, 30);

    @GetMapping("/balance")
    public ResponseEntity<Balance> getBalance(@AuthenticationPrincipal CustomUserDetails user) {
        var port = getPort(user);
        return ResponseEntity.ok(balance.getOrCompute(() -> new Balance(port.getSpotBalance(), port.getFutureBalance())));
    }

    @GetMapping("/time/exchange")
    public ResponseEntity<Time> getTimeExchange(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(new Time(getPort(user).getRemoteTimestamp()));
    }

    @GetMapping("/time/local")
    public ResponseEntity<Time> getTimeLocal(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(new Time(getPort(user).getLocalTimestamp()));
    }

    @GetMapping("/strategy")
    public ResponseEntity<List<Strategy>> getStrategy(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(getPort(user).getManagers().stream().map(m -> new Strategy(m.getId(), m.getTypeManager(), m.getName(), m.isRunning())).toList());
    }

    private @NotNull EnvironmentManager.EnvironmentPort getPort(@NotNull CustomUserDetails user) {
        return titan.getEnvironmentManager(user.getId()).getPort();
    }
}
