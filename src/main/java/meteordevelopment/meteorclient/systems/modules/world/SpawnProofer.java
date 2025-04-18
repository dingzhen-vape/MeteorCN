/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class SpawnProofer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("范围")
        .description("放置方块和渲染的范围。")
        .defaultValue(3)
        .min(0)
        .build()
    );

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("方块")
        .description("用于防止生成的方块。")
        .defaultValue(Blocks.TORCH, Blocks.STONE_BUTTON, Blocks.STONE_SLAB)
        .filter(this::filterBlocks)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("延迟")
        .description("放置方块之间的延迟,以刻为单位。")
        .defaultValue(0)
        .min(0)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("旋转")
        .description("旋转向被放置的方块。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("模式")
        .description("应该防止哪些生成类型。")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Setting<Boolean> newMobSpawnLightLevel = sgGeneral.add(new BoolSetting.Builder()
        .name("新怪物生成光照等级")
        .description("使用新的（1.18+）怪物生成行为")
        .defaultValue(true)
        .build()
    );


    private final Pool<BlockPos.Mutable> spawnPool = new Pool<>(BlockPos.Mutable::new);
    private final List<BlockPos.Mutable> spawns = new ArrayList<>();
    private int ticksWaited;

    public SpawnProofer() {
        super(Categories.World, "防止生成", "自动防止未照明的区域生成怪物。");
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        // Delay
        if (delay.get() != 0 && ticksWaited < delay.get() - 1) {
            return;
        }

        // Find slot
        boolean foundBlock = InvUtils.testInHotbar(itemStack -> blocks.get().contains(Block.getBlockFromItem(itemStack.getItem())));
        if (!foundBlock) {
            error("在快捷栏中找不到选择的方块。");
            toggle();
            return;
        }

        // Find spawn locations
        for (BlockPos.Mutable blockPos : spawns) spawnPool.free(blockPos);
        spawns.clear();

        int lightLevel = newMobSpawnLightLevel.get() ? 0 : 7;
        BlockIterator.register(range.get(), range.get(), (blockPos, blockState) -> {
            BlockUtils.MobSpawn spawn = BlockUtils.isValidMobSpawn(blockPos, blockState, lightLevel);

            if ((spawn == BlockUtils.MobSpawn.Always && (mode.get() == Mode.Always || mode.get() == Mode.Both)) ||
                    spawn == BlockUtils.MobSpawn.Potential && (mode.get() == Mode.Potential || mode.get() == Mode.Both)) {

                spawns.add(spawnPool.get().set(blockPos));
            }
        });
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        // Delay
        if (delay.get() != 0 && ticksWaited < delay.get() - 1) {
            ticksWaited++;
            return;
        }

        if (spawns.isEmpty()) return;

        // Find slot
        FindItemResult block = InvUtils.findInHotbar(itemStack -> blocks.get().contains(Block.getBlockFromItem(itemStack.getItem())));
        if (!block.found()) {
            error("在快捷栏中找不到选择的方块。");
            toggle();
            return;
        }

        // Place blocks
        if (delay.get() == 0) {
            for (BlockPos blockPos : spawns) BlockUtils.place(blockPos, block, rotate.get(), -50, false);
        }
        else {
            // Check if light source
            if (isLightSource(Block.getBlockFromItem(mc.player.getInventory().getStack(block.slot()).getItem()))) {

                // Find lowest light level
                int lowestLightLevel = 16;
                BlockPos.Mutable selectedBlockPos = spawns.getFirst();

                for (BlockPos blockPos : spawns) {
                    int lightLevel = mc.world.getLightLevel(blockPos);
                    if (lightLevel < lowestLightLevel) {
                        lowestLightLevel = lightLevel;
                        selectedBlockPos.set(blockPos);
                    }
                }

                BlockUtils.place(selectedBlockPos, block, rotate.get(), -50, false);
            }
            else {
                BlockUtils.place(spawns.getFirst(), block, rotate.get(), -50, false);
            }
        }

        ticksWaited = 0;
    }

    private boolean filterBlocks(Block block) {
        return isNonOpaqueBlock(block) || isLightSource(block);
    }

    private boolean isNonOpaqueBlock(Block block) {
        return block instanceof ButtonBlock ||
            block instanceof SlabBlock ||
            block instanceof AbstractPressurePlateBlock ||
            block instanceof TransparentBlock ||
            block instanceof TripwireBlock ||
            block instanceof CarpetBlock ||
            block instanceof LeverBlock ||
            block instanceof AbstractRedstoneGateBlock ||
            block instanceof AbstractRailBlock;
    }

    private boolean isLightSource(Block block) {
        return block.getDefaultState().getLuminance() > 0;
    }

    public enum Mode {
        Always,
        Potential,
        Both,
        None
    }
}
