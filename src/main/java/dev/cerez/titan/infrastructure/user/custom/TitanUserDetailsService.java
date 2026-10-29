package dev.cerez.titan.infrastructure.user.custom;

import dev.cerez.titan.infrastructure.user.User;
import dev.cerez.titan.infrastructure.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TitanUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public @NotNull UserDetails loadUserByUsername(@NotNull String username)
            throws UsernameNotFoundException {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("Usuario no encontrado"));

        return new TitanUserDetails(user);
    }

    public void changePassword(
            UUID userId,
            String currentPassword,
            String newPassword) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found"));

        if (!passwordEncoder.matches(
                currentPassword, user.getPassword())) {
            throw new BadCredentialsException(
                    "Current password is incorrect");
        }

//        if (newPassword == null || newPassword.length() < 12) {
//            throw new IllegalArgumentException(
//                    "Password must contain at least 12 characters");
//        }

        user.setPassword(passwordEncoder.encode(newPassword));

        userRepository.save(user);
    }
}