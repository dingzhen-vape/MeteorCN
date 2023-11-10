/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.entity.player.BreakBlockEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;

public class NoGhostBlocks extends Module {
    public NoGhostBlocks() {
        super(Categories.World, "无幽灵方块", "尝试防止快速破坏方块时产生的幽灵方块。对于多连接特别有用。");
    }

    @EventHandler
    public void onBreakBlock(BreakBlockEvent event) {
        if (mc.isInSingleplayer()) return;

        event.cancel();

        BlockState blockState = mc.world.getBlockState(event.blockPos);
        blockState.getBlock().onBreak(mc.world, event.blockPos, blockState, mc.player);
    }
}
