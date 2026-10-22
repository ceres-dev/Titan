package dev.cerez.titan.infrastructure.controler;

import dev.cerez.titan.Titan;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.infrastructure.model.Balance;
import dev.cerez.titan.infrastructure.model.LogRecord;
import dev.cerez.titan.infrastructure.model.Strategy;
import dev.cerez.titan.infrastructure.model.Time;
import dev.cerez.titan.infrastructure.user.custom.TitanUserDetails;
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
    public ResponseEntity<Balance> getBalance(@AuthenticationPrincipal TitanUserDetails user) {
        return ResponseEntity.ok(getPort(user).getBalance());
    }

    @GetMapping("/time")
    public ResponseEntity<Time> getTime(@AuthenticationPrincipal TitanUserDetails user) {
        return ResponseEntity.ok(getPort(user).getTime());
    }

    @GetMapping("/strategy")
    public ResponseEntity<List<Strategy>> getStrategy(@AuthenticationPrincipal TitanUserDetails user) {
        return ResponseEntity.ok(getPort(user).getStrategy());
    }

    @GetMapping("/logs")
    public ResponseEntity<List<LogRecord>> getLogs(@AuthenticationPrincipal TitanUserDetails user) {
        return ResponseEntity.ok(getPort(user).getLogs());
    }

    @GetMapping("/positions")
    public ResponseEntity<List<BinanceConnector.FuturePosition>> getPositions(@AuthenticationPrincipal TitanUserDetails user) {
        return ResponseEntity.ok(getPort(user).getPositions());
    }

    @PostMapping("/command")
    public ResponseEntity<Void> executeCommand(@AuthenticationPrincipal TitanUserDetails user,
                                                  @RequestParam(value = "command") String command
    ) {
        getPort(user).executeCommand(command);
        return ResponseEntity.ok().build();
    }

//    @PostMapping("/stopAllSrategy")
//    public ResponseEntity<Void> stopAll(@AuthenticationPrincipal TitanUserDetails user) {
//        getPort(user).getStrategy().forEach(Switch::stop);
//        return ResponseEntity.ok().build();
//    }
//
//    @PostMapping("/stopSrategy")
//    public ResponseEntity<Void> stop(@AuthenticationPrincipal TitanUserDetails user, @RequestParam("id") String id) {
//        return Utils.findManager(getPort(user), id, Switch::stop);
//    }
//
//    @PostMapping("/startStrategy")
//    public ResponseEntity<Void> start(@AuthenticationPrincipal TitanUserDetails user, @RequestParam("id") String id) {
//        return Utils.findManager(getPort(user), id, Switch::start);
//    }

    private @NotNull EnvironmentManager.EnvironmentPort getPort(@NotNull TitanUserDetails user) {
        return titan.getEnvironmentManager(user.getId()).getPort();
    }
}
