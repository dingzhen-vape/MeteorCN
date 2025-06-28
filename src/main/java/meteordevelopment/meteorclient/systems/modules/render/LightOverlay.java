/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class LightOverlay extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("颜色");

    // General

    private final Setting<Integer> horizontalRange = sgGeneral.add(new IntSetting.Builder()
        .name("水平范围")
        .description("水平范围（以方块为单位）。")
        .defaultValue(8)
        .min(0)
        .build()
    );

    private final Setting<Integer> verticalRange = sgGeneral.add(new IntSetting.Builder()
        .name("垂直范围")
        .description("垂直范围（以方块为单位）。")
        .defaultValue(4)
        .min(0)
        .build()
    );

    private final Setting<Boolean> seeThroughBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("透视方块")
        .description("允许你透过方块看到线条。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> lightLevel = sgGeneral.add(new IntSetting.Builder()
        .name("light-level")
        .description("Which light levels to render. Old spawning light: 7.")
        .defaultValue(0)
        .min(0)
        .sliderMax(15)
        .build()
    );

    // Colors

    private final Setting<SettingColor> color = sgColors.add(new ColorSetting.Builder()
        .name("颜色")
        .description("怪物当前可以生成的地方的颜色。")
        .defaultValue(new SettingColor(225, 25, 25))
        .build()
    );

    private final Setting<SettingColor> potentialColor = sgColors.add(new ColorSetting.Builder()
        .name("潜在颜色")
        .description("怪物潜在可以生成的地方的颜色（例如在夜晚）。")
        .defaultValue(new SettingColor(225, 225, 25))
        .build()
    );

    private final Pool<Cross> crossPool = new Pool<>(Cross::new);
    private final List<Cross> crosses = new ArrayList<>();

    public LightOverlay() {
        super(Categories.Render, "光照覆盖", "显示怪物可以生成的方块。");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        for (Cross cross : crosses) crossPool.free(cross);
        crosses.clear();

        BlockIterator.register(horizontalRange.get(), verticalRange.get(), (blockPos, blockState) -> {
            switch (BlockUtils.isValidMobSpawn(blockPos, blockState, lightLevel.get())) {
                case Potential -> crosses.add(crossPool.get().set(blockPos, true));
                case Always -> crosses.add((crossPool.get().set(blockPos, false)));
            }
        });
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (crosses.isEmpty()) return;

        Renderer3D renderer = seeThroughBlocks.get() ? event.renderer : event.depthRenderer;

        for (Cross cross : crosses) {
            cross.render(renderer);
        }
    }

    private class Cross {
        private double x, y, z;
        private boolean potential;

        public Cross set(BlockPos blockPos, boolean potential) {
            x = blockPos.getX();
            y = blockPos.getY() + 0.0075;
            z = blockPos.getZ();

            this.potential = potential;

            return this;
        }

        public void render(Renderer3D renderer) {
            Color c = potential ? potentialColor.get() : color.get();

            renderer.line(x, y, z, x + 1, y, z + 1, c);
            renderer.line(x + 1, y, z, x, y, z + 1, c);
        }
    }

    public enum Spawn {
        Never,
        Potential,
        Always
    }
}
