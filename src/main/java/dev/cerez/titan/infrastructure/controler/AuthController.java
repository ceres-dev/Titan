package dev.cerez.titan.infrastructure.controler;


import dev.cerez.titan.infrastructure.user.custom.TitanUserDetails;
import dev.cerez.titan.infrastructure.user.custom.TitanUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TitanUserDetailsService userDetailsService;

    @PostMapping("/changePassword")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal TitanUserDetails user,
            @NotNull @RequestParam String oldPassword,
            @NotNull @RequestParam String newPassword) {

        userDetailsService.changePassword(
                user.getId(),
                oldPassword,
                newPassword
        );

        return ResponseEntity.noContent().build();
    }

}
