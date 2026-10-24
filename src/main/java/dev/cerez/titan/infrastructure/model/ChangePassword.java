package dev.cerez.titan.infrastructure.model;

public record ChangePassword(
        String currentPassword,
        String newPassword
) {
}
