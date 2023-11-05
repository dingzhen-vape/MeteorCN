/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

public class UnfocusedCPU extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    public final Setting<Integer> fps = sgGeneral.add(new IntSetting.Builder()
        .name("target-fps")
        .description("当窗口未聚焦时设置为限制的目标 FPS。")
        .min(1)
        .defaultValue(1)
        .sliderRange(1, 20)
        .build()
    );

    public UnfocusedCPU() {
        super(Categories.Render, "unfocused-cpu", "当 Minecraft 窗口未聚焦时限制 FPS。");
    }
}
