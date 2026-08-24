package dev.xigbclutch.thread.core.serialization;

/** One deterministic validation failure against a Thread JSON schema. */
public record SchemaViolation(String path, String message) {}
