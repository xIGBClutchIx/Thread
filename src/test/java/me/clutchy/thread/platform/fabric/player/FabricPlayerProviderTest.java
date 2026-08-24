package me.clutchy.thread.platform.fabric.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class FabricPlayerProviderTest {
  @Test
  void missAndAbsentTargetsBecomeAnEmptyResult() {
    BlockHitResult miss = BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO);
    BlockHitResult hit = new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false);

    assertFalse(FabricPlayerProvider.isValidBlockTarget(null));
    assertFalse(FabricPlayerProvider.isValidBlockTarget(miss));
    assertTrue(FabricPlayerProvider.isValidBlockTarget(hit));
  }
}
