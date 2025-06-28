/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.marker;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.BlockPos;

// TODO: Add outline and more modes
public class CuboidMarker extends BaseMarker {
    public static final String type = "立方体";

    public enum Mode {
        Full
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染");

    // General

    private final Setting<BlockPos> pos1 = sgGeneral.add(new BlockPosSetting.Builder()
        .name("pos-1")
        .description("立方体的第一个角")
        .build()
    );

    private final Setting<BlockPos> pos2 = sgGeneral.add(new BlockPosSetting.Builder()
        .name("pos-2")
        .description("立方体的第二个角")
        .build()
    );

    // Render

    private final Setting<Mode> mode = sgRender.add(new EnumSetting.Builder<Mode>()
        .name("模式")
        .description("这个标记使用的模式。")
        .defaultValue(Mode.Full)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("形状的渲染方式.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("侧面颜色")
        .description("要渲染的方块的侧颜色.")
        .defaultValue(new SettingColor(0, 100, 255, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("线条颜色")
        .description("要渲染的方块的线颜色.")
        .defaultValue(new SettingColor(0, 100, 255, 255))
        .build()
    );

    public CuboidMarker() {
        super(type);
    }

    @Override
    public String getTypeName() {
        return type;
    }

    @Override
    protected void render(Render3DEvent event) {
        int minX = Math.min(pos1.get().getX(), pos2.get().getX());
        int minY = Math.min(pos1.get().getY(), pos2.get().getY());
        int minZ = Math.min(pos1.get().getZ(), pos2.get().getZ());
        int maxX = Math.max(pos1.get().getX(), pos2.get().getX());
        int maxY = Math.max(pos1.get().getY(), pos2.get().getY());
        int maxZ = Math.max(pos1.get().getZ(), pos2.get().getZ());

        event.renderer.box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }
}
