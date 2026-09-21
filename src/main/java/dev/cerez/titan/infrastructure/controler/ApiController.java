package dev.cerez.titan.infrastructure.controler;

import dev.cerez.titan.infrastructure.user.custom.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping("/account")
    public String account(@AuthenticationPrincipal CustomUserDetails user) {

        String username = user.getUsername();
        return "Usuario: " + username;
    }
}
