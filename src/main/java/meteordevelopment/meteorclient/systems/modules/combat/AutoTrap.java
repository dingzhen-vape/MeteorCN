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
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class AutoTrap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染");

    // General

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("白名单")
        .description("使用哪些块。")
        .defaultValue(Blocks.OBSIDIAN, Blocks.NETHERITE_BLOCK)
        .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("目标范围")
        .description("可以定位的玩家范围。")
        .defaultValue(4)
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("目标优先级")
        .description("如何选择要定位的玩家。")
        .defaultValue(SortPriority.LowestHealth)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("块放置之间有多少个刻度。")
        .defaultValue(1)
        .build()
    );

    private final Setting<TopMode> topPlacement = sgGeneral.add(new EnumSetting.Builder<TopMode>()
        .name("top-blocks")
        .description("哪些块放置在目标的上半部分。")
        .defaultValue(TopMode.Full)
        .build()
    );

    private final Setting<BottomMode> bottomPlacement = sgGeneral.add(new EnumSetting.Builder<BottomMode>()
        .name("bottom-blocks")
        .description("哪些块放置在目标的上半部分。目标的下半部分。")
        .defaultValue(BottomMode.Platform)
        .build()
    );

    private final Setting<Boolean> selfToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("自切换")
        .description("放置所有块后关闭。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("旋转")
        .description("放置时朝块旋转。")
        .defaultValue(true)
        .build()
    );

    // Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("渲染")
        .description("渲染将放置块的覆盖层.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("形状如何渲染。")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("目标块渲染的侧面颜色。")
        .defaultValue(new SettingColor(197, 137, 232, 10))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("目标块的线条颜色渲染。")
        .defaultValue(new SettingColor(197, 137, 232))
        .build()
    );

    private final Setting<SettingColor> nextSideColor = sgRender.add(new ColorSetting.Builder()
        .name("next-side-color")
        .description("下一个要放置的块的侧面颜色。")
        .defaultValue(new SettingColor(227, 196, 245, 10))
        .build()
    );

    private final Setting<SettingColor> nextLineColor = sgRender.add(new ColorSetting.Builder()
        .name("next-line-color")
        .description("下一个要放置的块的线条颜色。")
        .defaultValue(new SettingColor(227, 196, 245))
        .build()
    );

    private final List<BlockPos> placePositions = new ArrayList<>();
    private PlayerEntity target;
    private boolean placed;
    private int timer;

    public AutoTrap() {
        super(Categories.Combat, "auto -trap", "将人困在盒子里以防止他们移动。");
    }

    @Override
    public void onActivate() {
        target = null;
        placePositions.clear();
        timer = 0;
        placed = false;
    }

    @Override
    public void onDeactivate() {
        placePositions.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (selfToggle.get() && placed && placePositions.isEmpty()) {
            placed = false;
            toggle();
            return;
        }

        for (Block currentBlock : blocks.get()) {
            FindItemResult itemResult = InvUtils.findInHotbar(currentBlock.asItem());

            if (!itemResult.isHotbar() && !itemResult.isOffhand()) {
                placePositions.clear();
                placed = false;
                continue;
            }

            if (TargetUtils.isBadTarget(target, range.get())) {
                target = TargetUtils.getPlayerTarget(range.get(), priority.get());
                if (TargetUtils.isBadTarget(target, range.get())) return;
            }

            fillPlaceArray(target);

            if (timer >= delay.get() && placePositions.size() > 0) {
                BlockPos blockPos = placePositions.get(placePositions.size() - 1);

                if (BlockUtils.place(blockPos, itemResult, rotate.get(), 50, true)) {
                    placePositions.remove(blockPos);
                    placed = true;
                }

                timer = 0;
            } else {
                timer++;
            }
            return;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || placePositions.isEmpty()) return;

        for (BlockPos pos : placePositions) {
            boolean isFirst = pos.equals(placePositions.get(placePositions.size() - 1));

            Color side = isFirst ? nextSideColor.get() : sideColor.get();
            Color line = isFirst ? nextLineColor.get() : lineColor.get();

            event.renderer.box(pos, side, line, shapeMode.get(), 0);
        }
    }

    private void fillPlaceArray(PlayerEntity target) {
        placePositions.clear();
        BlockPos targetPos = target.getBlockPos();

        switch (topPlacement.get()) {
            case Full -> {
                add(targetPos.add(0, 2, 0));
                add(targetPos.add(1, 1, 0));
                add(targetPos.add(-1, 1, 0));
                add(targetPos.add(0, 1, 1));
                add(targetPos.add(0, 1, -1));
            }
            case Face -> {
                add(targetPos.add(1, 1, 0));
                add(targetPos.add(-1, 1, 0));
                add(targetPos.add(0, 1, 1));
                add(targetPos.add(0, 1, -1));
            }
            case Top -> add(targetPos.add(0, 2, 0));
        }

        switch (bottomPlacement.get()) {
            case Platform -> {
                add(targetPos.add(0, -1, 0));
                add(targetPos.add(1, -1, 0));
                add(targetPos.add(-1, -1, 0));
                add(targetPos.add(0, -1, 1));
                add(targetPos.add(0, -1, -1));
            }
            case Full -> {
                add(targetPos.add(1, 0, 0));
                add(targetPos.add(-1, 0, 0));
                add(targetPos.add(0, 0, -1));
                add(targetPos.add(0, 0, 1));
            }
            case Single -> add(targetPos.add(0, -1, 0));
        }
    }


    private void add(BlockPos blockPos) {
        if (!placePositions.contains(blockPos) && BlockUtils.canPlace(blockPos)) placePositions.add(blockPos);
    }

    @Override
    public String getInfoString() {
        return EntityUtils.getName(target);
    }

    public enum TopMode {
        Full,
        Top,
        Face,
        None
    }

    public enum BottomMode {
        Single,
        Platform,
        Full,
        None
    }
}
