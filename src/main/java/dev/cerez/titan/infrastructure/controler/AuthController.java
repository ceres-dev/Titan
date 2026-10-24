package dev.cerez.titan.infrastructure.controler;

import dev.cerez.titan.infrastructure.model.ChangePassword;
import dev.cerez.titan.infrastructure.user.custom.TitanUserDetails;
import dev.cerez.titan.infrastructure.user.custom.TitanUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TitanUserDetailsService userDetailsService;

    @PostMapping("/changePassword")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal TitanUserDetails user,
            @RequestBody ChangePassword request) {

        userDetailsService.changePassword(
                user.getId(),
                request.currentPassword(),
                request.newPassword()
        );

        return ResponseEntity.noContent().build();
    }

}
