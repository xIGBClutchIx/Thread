package me.clutchy.thread.core.integration;

/** Outcome of evaluating one optional integration candidate. */
public enum IntegrationActivationStatus {
  ACTIVE,
  UNAVAILABLE,
  DISABLED,
  INCOMPATIBLE,
  FAILED
}
