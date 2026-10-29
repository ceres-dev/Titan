package dev.cerez.titan.infrastructure.controler;


import dev.cerez.titan.infrastructure.model.Account;
import dev.cerez.titan.infrastructure.user.custom.TitanUserDetails;
import dev.cerez.titan.infrastructure.user.custom.TitanUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TitanUserDetailsService userDetailsService;

    @PostMapping("/changePassword")
    public ResponseEntity<Void> changePassword(
            @NotNull @AuthenticationPrincipal TitanUserDetails user,
            @NotNull @RequestParam String oldPassword,
            @NotNull @RequestParam String newPassword) {

        userDetailsService.changePassword(
                user.getId(),
                oldPassword,
                newPassword
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/account")
    public ResponseEntity<Account> getUsername(@NotNull @AuthenticationPrincipal TitanUserDetails user) {
        return ResponseEntity.ok(new Account(user.getUsername()));
    }

}
