/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

public class TridentBoost extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> multiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("提升")
        .description("使用激流时你的速度乘以多少。")
        .defaultValue(2)
        .min(0.1)
        .sliderMin(1)
        .build()
    );

    private final Setting<Boolean> allowOutOfWater = sgGeneral.add(new BoolSetting.Builder()
        .name("离水")
        .description("是否让激流在水外也能工作")
        .defaultValue(true)
        .build()
    );

    public TridentBoost() {
        super(Categories.Movement, "三叉戟提升", "使用三叉戟的激流时提升你。");
    }

    public double getMultiplier() {
        return isActive() ? multiplier.get() : 1;
    }

    public boolean allowOutOfWater() {
        return isActive() ? allowOutOfWater.get() : false;
    }
}
