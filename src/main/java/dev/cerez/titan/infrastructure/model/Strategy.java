package dev.cerez.titan.infrastructure.model;

import dev.cerez.titan.core.strategy.TypeManager;

import java.util.UUID;

public record Strategy(UUID id, TypeManager strategy, String label, boolean isRunning) {
}
