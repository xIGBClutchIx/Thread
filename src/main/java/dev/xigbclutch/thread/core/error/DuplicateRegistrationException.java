package dev.xigbclutch.thread.core.error;

/** Indicates that two core extensions attempted to claim the same stable identifier. */
public final class DuplicateRegistrationException extends IllegalArgumentException {
  public DuplicateRegistrationException(String kind, String identifier) {
    super("duplicate " + kind + " ID: " + identifier);
  }
}
