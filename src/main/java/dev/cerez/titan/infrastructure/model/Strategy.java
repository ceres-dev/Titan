package dev.cerez.titan.infrastructure.model;

import dev.cerez.titan.core.StatusManager;

import java.util.UUID;

public record Strategy(UUID id, String name, String label, StatusManager status) {
}
