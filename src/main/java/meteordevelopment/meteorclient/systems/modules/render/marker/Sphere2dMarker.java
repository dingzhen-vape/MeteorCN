/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.marker;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dir;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class Sphere2dMarker extends BaseMarker {
    private static class Block {
        public final int x, y, z;
        public int excludeDir;

        public Block(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static final String type = "二维球体";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染");
    private final SettingGroup sgKeybinding = settings.createGroup("按键绑定");

    private final Setting<BlockPos> center = sgGeneral.add(new BlockPosSetting.Builder()
        .name("中心")
        .description("球体的中心")
        .onChanged(bp -> dirty = true)
        .build()
    );

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("半径")
        .description("球体的半径")
        .defaultValue(20)
        .min(1)
        .noSlider()
        .onChanged(r -> dirty = true)
        .build()
    );

    private final Setting<Integer> layer = sgGeneral.add(new IntSetting.Builder()
        .name("层")
        .description("渲染哪一层")
        .defaultValue(0)
        .min(0)
        .noSlider()
        .onChanged(l -> dirty = true)
        .build()
    );

    // Render

    private final Setting<Boolean> limitRenderRange = sgRender.add(new BoolSetting.Builder()
        .name("限制渲染范围")
        .description("是否限制渲染范围（在非常大的圆中有用）")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> renderRange = sgRender.add(new IntSetting.Builder()
        .name("渲染范围")
        .description("渲染范围")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 20)
        .visible(limitRenderRange::get)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("形状的渲染方式。")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("侧面颜色")
        .description("被渲染方块的侧面颜色。")
        .defaultValue(new SettingColor(0, 100, 255, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("线条颜色")
        .description("被渲染方块的线条颜色。")
        .defaultValue(new SettingColor(0, 100, 255, 255))
        .build()
    );

    // Keybinding

    @SuppressWarnings("未使用")
    private final Setting<Keybind> nextLayerKey = sgKeybinding.add(new KeybindSetting.Builder()
        .name("下一层按键绑定")
        .description("增加层的按键绑定")
        .action(() -> {
            if (isVisible() && layer.get() < radius.get() * 2) layer.set(layer.get() + 1);
        })
        .build()
    );

    @SuppressWarnings("未使用")
    private final Setting<Keybind> prevLayerKey = sgKeybinding.add(new KeybindSetting.Builder()
        .name("上一层按键绑定")
        .description("增加层的按键绑定")
        .action(() -> {
            if (isVisible()) layer.set(layer.get() - 1);
        })
        .build()
    );

    private final List<Block> blocks = new ArrayList<>();
    private boolean dirty = true, calculating;

    public Sphere2dMarker() {
        super(type);
    }

    @Override
    protected void render(Render3DEvent event) {
        if (dirty && !calculating) calcCircle();

        synchronized (blocks) {
            for (Block block : blocks) {
                if (!limitRenderRange.get() || PlayerUtils.isWithin(block.x, block.y, block.z, renderRange.get())) {
                    event.renderer.box(block.x, block.y, block.z, block.x + 1, block.y + 1, block.z + 1, sideColor.get(), lineColor.get(), shapeMode.get(), block.excludeDir);
                }
            }
        }
    }

    @Override
    public String getTypeName() {
        return type;
    }

    private void calcCircle() {
        calculating = true;
        blocks.clear();

        Runnable action = () -> {
            int cX = center.get().getX();
            int cY = center.get().getY();
            int cZ = center.get().getZ();

            int rSq = radius.get() * radius.get();
            int dY = -radius.get() + layer.get();

            // Calculate 1 octant and transform,mirror,flip the rest
            int dX = 0;
            while (true) {
                int dZ = (int) Math.round(Math.sqrt(rSq - (dX * dX + dY * dY)));

                synchronized (blocks) {
                    // First and second octant
                    add(cX + dX, cY + dY, cZ + dZ);
                    add(cX + dZ, cY + dY, cZ + dX);

                    // Fifth and sixth octant
                    add(cX - dX, cY + dY, cZ - dZ);
                    add(cX - dZ, cY + dY, cZ - dX);

                    // Third and fourth octant
                    add(cX + dX, cY + dY, cZ - dZ);
                    add(cX + dZ, cY + dY, cZ - dX);

                    // Seventh and eighth octant
                    add(cX - dX, cY + dY, cZ + dZ);
                    add(cX - dZ, cY + dY, cZ + dX);
                }


                // Stop when we reach the midpoint
                if (dX >= dZ) break;
                dX++;
            }

            // Calculate connected blocks
            synchronized (blocks) {
                for (Block block : blocks) {
                    for (Block b : blocks) {
                        if (b == block) continue;

                        if (b.x == block.x + 1 && b.z == block.z) block.excludeDir |= Dir.EAST;
                        if (b.x == block.x - 1 && b.z == block.z) block.excludeDir |= Dir.WEST;
                        if (b.x == block.x && b.z == block.z + 1) block.excludeDir |= Dir.SOUTH;
                        if (b.x == block.x && b.z == block.z - 1) block.excludeDir |= Dir.NORTH;
                    }
                }
            }

            dirty = false;
            calculating = false;
        };

        if (radius.get() <= 50) action.run();
        else MeteorExecutor.execute(action);
    }

    private void add(int x, int y, int z) {
        for (Block b : blocks) {
            if (b.x == x && b.y == y && b.z == z) return;
        }

        blocks.add(new Block(x, y, z));
    }
}
