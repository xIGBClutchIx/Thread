package me.clutchy.thread.core.model;

/** Stable reasons why gameplay tools are not currently available. */
public enum SessionStatusReason {
  NO_WORLD,
  WORLD_LOADING,
  PLAYER_NOT_AVAILABLE,
  MULTIPLAYER_UNSUPPORTED
}
