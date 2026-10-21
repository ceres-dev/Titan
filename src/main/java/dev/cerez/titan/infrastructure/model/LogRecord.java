package dev.cerez.titan.infrastructure.model;

public record LogRecord(String message, String thread, String level, long time) {
}
