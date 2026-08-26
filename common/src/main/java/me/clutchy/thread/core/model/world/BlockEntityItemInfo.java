package me.clutchy.thread.core.model.world;

import java.util.Objects;
import me.clutchy.thread.core.model.item.ItemStackInfo;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** One non-empty, semantically named item position inside a block entity. */
public record BlockEntityItemInfo(String slot, ItemStackInfo item) {
  public BlockEntityItemInfo {
    slot = ModelValidation.boundedNonBlank(slot, "slot", 64);
    Objects.requireNonNull(item, "item");
  }
}
