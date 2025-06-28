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
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.RaycastContext;

import java.util.Set;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

public class AutoTrap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染");

    // General

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("白名单")
        .description("要使用的方块。")
        .defaultValue(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("放置范围")
        .description("The range at which blocks can be placed.")
        .defaultValue(4)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> placeWallsRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("墙壁范围")
        .description("Range in which to place when behind blocks.")
        .defaultValue(4)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("目标优先级")
        .description("如何选择要目标的玩家.")
        .defaultValue(SortPriority.LowestHealth)
        .build()
    );

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("目标范围")
        .description("目标玩家的最大距离。")
        .defaultValue(3)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("放置延迟")
        .description("放置方块之间的延迟,以刻为单位。")
        .defaultValue(1)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("每刻放置")
        .description("每刻放置的方块数量。")
        .defaultValue(1)
        .min(1)
        .build()
    );

    private final Setting<TopMode> topPlacement = sgGeneral.add(new EnumSetting.Builder<TopMode>()
        .name("顶部方块")
        .description("放置在目标上半部的方块。")
        .defaultValue(TopMode.Full)
        .build()
    );

    private final Setting<BottomMode> bottomPlacement = sgGeneral.add(new EnumSetting.Builder<BottomMode>()
        .name("底部方块")
        .description("放置在目标下半部的方块。")
        .defaultValue(BottomMode.Platform)
        .build()
    );

    private final Setting<Boolean> selfToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("自动关闭")
        .description("放置完所有方块后关闭。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("旋转")
        .description("放置时向方块旋转。")
        .defaultValue(true)
        .build()
    );

    // Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("是否渲染将要放置的方块的覆盖层。")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("形状的渲染方式.")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("侧面颜色")
        .description("目标方块渲染的侧面颜色。")
        .defaultValue(new SettingColor(197, 137, 232, 10))
        .visible(() -> render.get() && shapeMode.get().sides())
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("线条颜色")
        .description("目标方块渲染的线条颜色。")
        .defaultValue(new SettingColor(197, 137, 232))
        .visible(() -> render.get() && shapeMode.get().lines())
        .build()
    );

    private final Setting<SettingColor> nextSideColor = sgRender.add(new ColorSetting.Builder()
        .name("下一个侧面颜色")
        .description("下一个要放置的方块的侧面颜色。")
        .defaultValue(new SettingColor(227, 196, 245, 10))
        .visible(() -> render.get() && shapeMode.get().sides())
        .build()
    );

    private final Setting<SettingColor> nextLineColor = sgRender.add(new ColorSetting.Builder()
        .name("下一个线条颜色")
        .description("下一个要放置的方块的线条颜色。")
        .defaultValue(new SettingColor(5, 139, 221))
        .visible(() -> render.get() && shapeMode.get().lines())
        .build()
    );

    private final List<BlockPos> placePositions = new ArrayList<>();
    private PlayerEntity target;
    private boolean placed;
    private int timer;

    public AutoTrap() {
        super(Categories.Combat, "自动困住", "把人困在一个箱子里,防止他们移动。");
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

        // Grab blocks from hotbar
        FindItemResult block = InvUtils.findInHotbar(itemStack -> blocks.get().contains(Block.getBlockFromItem(itemStack.getItem())));
        if (!block.found()) return;

        // Find target to trap
        if (TargetUtils.isBadTarget(target, targetRange.get())) {
            target = TargetUtils.getPlayerTarget(targetRange.get(), priority.get());
            if (TargetUtils.isBadTarget(target, targetRange.get())) return;
        }

        fillPlaceArray(target);

        if (timer >= delay.get() && !placePositions.isEmpty()) {

            // Place blocks!
            int placedCount = 0;
            for (BlockPos placePosition : placePositions) {
                if (placedCount >= blocksPerTick.get()) continue;

                if (BlockUtils.place(placePosition, block, rotate.get(), 50, true)) {
                    placed = true;
                    placedCount++;
                }
            }

            timer = 0;
        } else {
            timer++;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || placePositions.isEmpty()) return;

        for (int i = 0; i < placePositions.size(); i++) {
            boolean isNext = i < blocksPerTick.get();
            Color side = isNext ? nextSideColor.get() : sideColor.get();
            Color line = isNext ? nextLineColor.get() : lineColor.get();

            event.renderer.box(placePositions.get(i), side, line, shapeMode.get(), 0);
        }
    }

    private void fillPlaceArray(PlayerEntity target) {
        placePositions.clear();

        // Get block positions of all four corners of the bottom of our target's bounding box
        double epsilon = 1e-5;
        Box box = target.getBoundingBox();
        List<BlockPos> corners = new ArrayList<>();
        corners.add(BlockPos.ofFloored(box.minX, box.minY, box.minZ));
        corners.add(BlockPos.ofFloored(box.minX, box.minY, box.maxZ - epsilon));
        corners.add(BlockPos.ofFloored(box.maxX - epsilon, box.minY, box.minZ));
        corners.add(BlockPos.ofFloored(box.maxX - epsilon, box.minY, box.maxZ - epsilon));

        // Add our place positions based on blocks our target is overlapping
        Set<BlockPos> overlappedPositions = new LinkedHashSet<>(corners); // Remove duplicate entries
        for (BlockPos targetPos : overlappedPositions) {
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
                    add(targetPos.add(0, -1, 0));
                    add(targetPos.add(1, 0, 0));
                    add(targetPos.add(-1, 0, 0));
                    add(targetPos.add(0, 0, -1));
                    add(targetPos.add(0, 0, 1));
                }
                case Single -> add(targetPos.add(0, -1, 0));
            }
        }

        // Sort the placePositions to set the furthest positions from our player to be placed first
        double pX = mc.player.getX();
        double pY = mc.player.getY();
        double pZ = mc.player.getZ();
        placePositions.sort(Comparator.comparingDouble(value -> Utils.squaredDistance(pX, pY, pZ, value.getX() + 0.5, value.getY() + 0.5, value.getZ() + 0.5) * -1));
    }

    private void add(BlockPos blockPos) {
        if (placePositions.contains(blockPos)) return;

        // Check if the player can place at pos
        if (!BlockUtils.canPlace(blockPos)) return;

        // Check raycast and range
        if (isOutOfRange(blockPos)) return;

        placePositions.add(blockPos);
    }

    private boolean isOutOfRange(BlockPos blockPos) {
        Vec3d pos = blockPos.toCenterPos();
        if (!PlayerUtils.isWithin(pos, placeRange.get())) return true;

        RaycastContext raycastContext = new RaycastContext(mc.player.getEyePos(), pos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        BlockHitResult result = mc.world.raycast(raycastContext);
        if (result == null || !result.getBlockPos().equals(blockPos))
            return !PlayerUtils.isWithin(pos, placeWallsRange.get());

        return false;
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
