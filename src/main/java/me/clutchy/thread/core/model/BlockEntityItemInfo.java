package me.clutchy.thread.core.model;

import java.util.Objects;

/** One non-empty, semantically named item position inside a block entity. */
public record BlockEntityItemInfo(String slot, ItemStackInfo item) {
  public BlockEntityItemInfo {
    slot = ModelValidation.boundedNonBlank(slot, "slot", 64);
    Objects.requireNonNull(item, "item");
  }
}
