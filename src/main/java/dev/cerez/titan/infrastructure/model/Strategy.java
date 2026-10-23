package dev.cerez.titan.infrastructure.model;

import dev.cerez.titan.core.StatusManager;
import dev.cerez.titan.core.strategy.TypeManager;

import java.util.UUID;

public record Strategy(UUID id, TypeManager type, String name, String label, StatusManager status) {
}
