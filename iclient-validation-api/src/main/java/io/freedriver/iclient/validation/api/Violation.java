package io.freedriver.iclient.validation.api;

/** A constraint failure with its property path, message and constraint annotation class name. */
public record Violation(String path, String message, String constraint) {}
