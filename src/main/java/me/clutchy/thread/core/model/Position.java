package me.clutchy.thread.core.model;

/** Three-dimensional world position using Minecraft's continuous coordinates. */
public record Position(double x, double y, double z) {
  public Position {
    ModelValidation.finite(x, "x");
    ModelValidation.finite(y, "y");
    ModelValidation.finite(z, "z");
  }
}
