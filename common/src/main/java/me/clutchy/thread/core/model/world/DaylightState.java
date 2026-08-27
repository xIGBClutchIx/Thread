package me.clutchy.thread.core.model.world;

/** Stable local daylight state for the player's current dimension. */
public enum DaylightState {
  /** The current dimension is in its bright daytime interval. */
  DAY,

  /** The current dimension is in its dark nighttime interval. */
  NIGHT,

  /** The current dimension has fixed lighting rather than a day/night cycle. */
  FIXED
}
