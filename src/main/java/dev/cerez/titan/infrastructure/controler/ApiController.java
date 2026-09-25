package dev.cerez.titan.infrastructure.controler;

import dev.cerez.titan.Titan;
import dev.cerez.titan.core.environment.EnvironmentManager;
import dev.cerez.titan.infrastructure.model.Balance;
import dev.cerez.titan.infrastructure.user.custom.CustomUserDetails;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ApiController {

    public final Titan titan = Titan.getInstance();

    @GetMapping("/balance")
    public ResponseEntity<?> getBalance(@AuthenticationPrincipal CustomUserDetails user) {
        var port = getPort(user);
        return ResponseEntity.ok(new Balance(port.getSpotBalance(), port.getFutureBalance()));
    }

    @PostMapping("/stopAll")
    public ResponseEntity<Void> postStopAll(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.noContent().build();
    }


    private @NotNull EnvironmentManager.EnvironmentPort getPort(@NotNull CustomUserDetails user) {
        return titan.getEnvironmentManager(user.getId()).getPort();
    }
}
