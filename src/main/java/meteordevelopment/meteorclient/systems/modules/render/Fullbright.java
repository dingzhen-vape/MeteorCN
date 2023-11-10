/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.world.LightType;

public class Fullbright extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("模式")
        .description("Fullbright的使用模式。")
        .defaultValue(Mode.Gamma)
        .onChanged(mode -> {
            if (mc.worldRenderer != null && isActive()) mc.worldRenderer.reload();
        })
        .build()
    );

    public final Setting<LightType> lightType = sgGeneral.add(new EnumSetting.Builder<LightType>()
        .name("光源类型")
        .description("Luminance模式使用的光源类型。")
        .defaultValue(LightType.BLOCK)
        .visible(() -> mode.get() == Mode.Luminance)
        .onChanged(integer -> {
            if (mc.worldRenderer != null && isActive()) mc.worldRenderer.reload();
        })
        .build()
    );

    private final Setting<Integer> minimumLightLevel = sgGeneral.add(new IntSetting.Builder()
        .name("最低光照等级")
        .description("使用Luminance模式时的最低光照等级。")
        .visible(() -> mode.get() == Mode.Luminance)
        .defaultValue(8)
        .range(0, 15)
        .sliderMax(15)
        .onChanged(integer -> {
            if (mc.worldRenderer != null && isActive()) mc.worldRenderer.reload();
        })
        .build()
    );

    public Fullbright() {
        super(Categories.Render, "全亮", "照亮你的世界！");
    }

    @Override
    public void onActivate() {
        if (mode.get() == Mode.Luminance) mc.worldRenderer.reload();
    }

    @Override
    public void onDeactivate() {
        if (mode.get() == Mode.Luminance) mc.worldRenderer.reload();
    }

    public int getLuminance(LightType type) {
        if (!isActive() || mode.get() != Mode.Luminance || type != lightType.get()) return 0;
        return minimumLightLevel.get();
    }

    public boolean getGamma() {
        return isActive() && mode.get() == Mode.Gamma;
    }

    public enum Mode {
        Gamma,
        Luminance
    }
}
