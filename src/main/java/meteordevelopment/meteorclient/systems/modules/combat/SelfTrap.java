/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class SelfTrap extends Module {
    public enum TopMode {
        AntiFacePlace,
        Full,
        Top,
        None
    }

    public enum BottomMode {
        Single,
        None
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染");

    // General

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("白名单")
        .description("要使用的方块.")
        .defaultValue(Blocks.OBSIDIAN, Blocks.NETHERITE_BLOCK)
        .build()
    );

    private final Setting<TopMode> topPlacement = sgGeneral.add(new EnumSetting.Builder<TopMode>()
        .name("上半部模式")
        .description("要在你上半身放置的位置.")
        .defaultValue(TopMode.Top)
        .build()
    );

    private final Setting<BottomMode> bottomPlacement = sgGeneral.add(new EnumSetting.Builder<BottomMode>()
        .name("下半部模式")
        .description("要在你下半身放置的位置.")
        .defaultValue(BottomMode.None)
        .build()
    );

    private final Setting<Integer> delaySetting = sgGeneral.add(new IntSetting.Builder()
        .name("放置延迟")
        .description("放置方块之间的刻延迟.")
        .defaultValue(1)
        .build()
    );

    private final Setting<Boolean> center = sgGeneral.add(new BoolSetting.Builder()
        .name("居中")
        .description("放置前将你居中到你站的方块上.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> turnOff = sgGeneral.add(new BoolSetting.Builder()
        .name("关闭")
        .description("放置后关闭.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("旋转")
        .description("放置时向服务器发送旋转数据包.")
        .defaultValue(true)
        .build()
    );

    // Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("渲染")
        .description("渲染一个方块覆盖在要放置的方块上.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("形状的渲染方式.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("侧颜色")
        .description("要渲染的方块的侧颜色.")
        .defaultValue(new SettingColor(204, 0, 0, 10))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("线颜色")
        .description("要渲染的方块的线颜色.")
        .defaultValue(new SettingColor(204, 0, 0, 255))
        .build()
    );

    private final List<BlockPos> placePositions = new ArrayList<>();
    private boolean placed;
    private int delay;

    public SelfTrap(){
        super(Categories.Combat, "自我困住", "在你头上放置方块.");
    }

    @Override
    public void onActivate() {
        if (!placePositions.isEmpty()) placePositions.clear();
        delay = 0;
        placed = false;

        if (center.get()) PlayerUtils.centerPlayer();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        for (Block currentBlock : blocks.get()) {
            FindItemResult itemResult = InvUtils.findInHotbar(currentBlock.asItem());

            if (turnOff.get() && ((placed && placePositions.isEmpty()) || !itemResult.found())) {
                toggle();
                continue;
            }

            if (!itemResult.found()) {
                placePositions.clear();
                continue;
            }

            findPlacePos(currentBlock);

            if (delay >= delaySetting.get() && placePositions.size() > 0) {
                BlockPos blockPos = placePositions.get(placePositions.size() - 1);

                if (BlockUtils.place(blockPos, itemResult, rotate.get(), 50)) {
                    placePositions.remove(blockPos);
                    placed = true;
                }

                delay = 0;
            }
            else delay++;
            return;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || placePositions.isEmpty()) return;
        for (BlockPos pos : placePositions) event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    private void findPlacePos(Block block) {
        placePositions.clear();
        BlockPos pos = mc.player.getBlockPos();

        switch (topPlacement.get()) {
            case Full -> {
                add(pos.add(0, 2, 0), block);
                add(pos.add(1, 1, 0), block);
                add(pos.add(-1, 1, 0), block);
                add(pos.add(0, 1, 1), block);
                add(pos.add(0, 1, -1), block);
            }
            case Top -> add(pos.add(0, 2, 0), block);
            case AntiFacePlace -> {
                add(pos.add(1, 1, 0), block);
                add(pos.add(-1, 1, 0), block);
                add(pos.add(0, 1, 1), block);
                add(pos.add(0, 1, -1), block);
            }
        }

        if (bottomPlacement.get() == BottomMode.Single) add(pos.add(0, -1, 0), block);
    }


    private void add(BlockPos blockPos, Block block) {
        if (!placePositions.contains(blockPos) &&
            mc.world.getBlockState(blockPos).isReplaceable() &&
            mc.world.canPlace(block.getDefaultState(), blockPos, ShapeContext.absent())) placePositions.add(blockPos);
    }
}
