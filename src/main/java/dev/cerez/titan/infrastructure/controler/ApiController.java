package dev.cerez.titan.infrastructure.controler;

import dev.cerez.titan.Titan;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.infrastructure.model.Balance;
import dev.cerez.titan.infrastructure.model.LogRecord;
import dev.cerez.titan.infrastructure.model.Strategy;
import dev.cerez.titan.infrastructure.model.Time;
import dev.cerez.titan.infrastructure.user.custom.CustomUserDetails;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.utils.Switch;
import dev.cerez.titan.utils.TemporalRefence;
import dev.cerez.titan.utils.Utils;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class ApiController {

    public final Titan titan = Titan.getInstance();

    public TemporalRefence<Balance> balance = new TemporalRefence<>(TimeUnit.SECONDS, 30);

    @GetMapping("/balance")
    public ResponseEntity<Balance> getBalance(@AuthenticationPrincipal CustomUserDetails user) {
        var port = getPort(user);
        return ResponseEntity.ok(balance.getOrCompute(() ->
                new Balance(
                        Utils.removeIf(port.getSpotBalance(), (k, v) -> v.signum() == 0),
                        Utils.removeIf(port.getFutureBalance(), (k, v) -> v.signum() == 0)
                )
        ));
    }

    @GetMapping("/time")
    public ResponseEntity<Time> getTime(@AuthenticationPrincipal CustomUserDetails user) {
        var port = getPort(user);
        return ResponseEntity.ok(new Time(port.getLocalTimestamp(), port.getRemoteTimestamp()));
    }

    @GetMapping("/strategy")
    public ResponseEntity<List<Strategy>> getStrategy(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(getPort(user).getStrategy().stream().map(m -> new Strategy(m.getId(), m.getTypeManager(), m.getName(), m.isRunning())).toList());
    }

    @GetMapping("/logs")
    public ResponseEntity<List<LogRecord>> getLogs(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(getPort(user).getLogs().stream().map(i -> new LogRecord(Utils.cleanAnsi(i.getMessage()), i.getThreadName(), i.getLevel().toString(), i.getTimeStamp())).toList());
    }

    @PostMapping("/command")
    public ResponseEntity<Void> executeCommand(@AuthenticationPrincipal CustomUserDetails user,
                                                  @RequestParam(value = "command") String command
    ) {
        getPort(user).executeCommand(command);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stopAllSrategy")
    public ResponseEntity<Void> stopAll(@AuthenticationPrincipal CustomUserDetails user) {
        getPort(user).getStrategy().forEach(Switch::stop);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stopSrategy")
    public ResponseEntity<Void> stop(@AuthenticationPrincipal CustomUserDetails user, @RequestParam("id") String id) {
        return Utils.findManager(getPort(user), id, Switch::stop);
    }

    @PostMapping("/startStrategy")
    public ResponseEntity<Void> start(@AuthenticationPrincipal CustomUserDetails user, @RequestParam("id") String id) {
        return Utils.findManager(getPort(user), id, Switch::start);
    }

    private @NotNull EnvironmentManager.EnvironmentPort getPort(@NotNull CustomUserDetails user) {
        return titan.getEnvironmentManager(user.getId()).getPort();
    }
}
