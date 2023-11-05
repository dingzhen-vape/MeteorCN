/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.meteorclient.utils.world.Dir;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.List;

public class VoidESP extends Module {
    private static final Direction[] SIDES = {Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.WEST};

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染");

    // General

    private final Setting<Boolean> airOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("仅空气")
        .description("仅检查基岩中的空气块。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> horizontalRadius = sgGeneral.add(new IntSetting.Builder()
        .name("水平半径")
        .description("搜索孔的水平半径。")
        .defaultValue(64)
        .min(0)
        .sliderMax(256)
        .build()
    );

    private final Setting<Integer> holeHeight = sgGeneral.add(new IntSetting.Builder()
        .name("孔高度")
        .description("最小孔高度要渲染。")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Boolean> netherRoof = sgGeneral.add(new BoolSetting.Builder()
        .name("nether-roof")
        .description("检查下界屋顶上的孔。")
        .defaultValue(true)
        .build()
    );

    // Render

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("如何渲染形状。")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("fill-color")
        .description("填充孔的颜色虚空。")
        .defaultValue(new SettingColor(225, 25, 25, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("绘制通向虚空的孔线的颜色。")
        .defaultValue(new SettingColor(225, 25, 255))
        .build()
    );

    private final BlockPos.Mutable blockPos = new BlockPos.Mutable();

    private final Pool<Void> voidHolePool = new Pool<>(Void::new);
    private final List<Void> voidHoles = new ArrayList<>();

    public VoidESP() {
        super(Categories.Render, "void-esp", "渲染通向虚空的基岩层中的孔。");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        voidHoles.clear();
        if (PlayerUtils.getDimension() == Dimension.End) return;

        int px = mc.player.getBlockPos().getX();
        int pz = mc.player.getBlockPos().getZ();
        int radius = horizontalRadius.get();

        for (int x = px - radius; x <= px + radius; x++) {
            for (int z = pz - radius; z <= pz + radius; z++) {
                blockPos.set(x, mc.world.getBottomY(), z);
                if (isHole(blockPos, false)) voidHoles.add(voidHolePool.get().set(blockPos.set(x, mc.world.getBottomY(), z), false));

                // Check for nether roof
                if (netherRoof.get() && PlayerUtils.getDimension() == Dimension.Nether) {
                    blockPos.set(x, 127, z);
                    if (isHole(blockPos, true)) voidHoles.add(voidHolePool.get().set(blockPos.set(x, 127, z), true));
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (Void voidHole : voidHoles) voidHole.render(event);
    }

    private boolean isBlockWrong(BlockPos blockPos) {
        Chunk chunk = mc.world.getChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4, ChunkStatus.FULL, false);
        if (chunk == null) return true;

        Block block = chunk.getBlockState(blockPos).getBlock();

        if (airOnly.get()) return block != Blocks.AIR;
        return block == Blocks.BEDROCK;
    }

    private boolean isHole(BlockPos.Mutable blockPos, boolean nether) {
        for (int i = 0; i < holeHeight.get(); i++) {
            blockPos.setY(nether ? 127 - i : mc.world.getBottomY());
            if (isBlockWrong(blockPos)) return false;
        }

        return true;
    }

    private class Void {
        private int x, y, z;
        private int excludeDir;

        public Void set(BlockPos.Mutable blockPos, boolean nether) {
            x = blockPos.getX();
            y = blockPos.getY();
            z = blockPos.getZ();

            excludeDir = 0;

            for (Direction side : SIDES) {
                blockPos.set(x + side.getOffsetX(), y, z + side.getOffsetZ());
                if (isHole(blockPos, nether)) excludeDir |= Dir.get(side);
            }

            return this;
        }

        public void render(Render3DEvent event) {
            event.renderer.box(x, y, z, x + 1, y + 1, z + 1, sideColor.get(), lineColor.get(), shapeMode.get(), excludeDir);
        }
    }
}
