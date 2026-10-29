package dev.cerez.titan.infrastructure.controler;

import dev.cerez.titan.TitanApplication;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.connector.exception.ApiException;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.infrastructure.model.*;
import dev.cerez.titan.infrastructure.user.custom.TitanUserDetails;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.utils.TemporalRefence;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    public final TitanApplication titan;

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
    public ResponseEntity<List<Position>> getPositions(@AuthenticationPrincipal TitanUserDetails user) {
        return ResponseEntity.ok(getPort(user).getPositions());
    }

    @GetMapping("/telemetry")
    private ResponseEntity<Telemetry> getTelemetry(@AuthenticationPrincipal TitanUserDetails user) {
        return ResponseEntity.ok(getPort(user).getTelemetry());
    }

    @PostMapping("/command")
    public ResponseEntity<Void> executeCommand(@AuthenticationPrincipal TitanUserDetails user,
                                                  @RequestParam(value = "command") String command
    ) {
        getPort(user).executeCommand(command);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transfer")
    private ResponseEntity<Void> transfer(@AuthenticationPrincipal TitanUserDetails user,
                                          @NotNull @RequestParam String asset,
                                          @NotNull @RequestParam BigDecimal amount,
                                          @NotNull @RequestParam String direction
    ){
        try {
            BinanceConnector.Transfer.valueOf(direction.toUpperCase());
        }catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().build();
        }
        try {
            getPort(user).transferBalance(asset, amount, BinanceConnector.Transfer.valueOf(direction.toUpperCase()));
        }catch (ApiException e){
            Log.exception(e);
            return ResponseEntity.badRequest().build();
        }catch (Exception e){
            Log.exception(e);
            return ResponseEntity.internalServerError().build();
        }
        Log.info("User:%s Transfer %s @ %.4f %s", user.getUsername(), asset, amount, direction.toUpperCase());
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
