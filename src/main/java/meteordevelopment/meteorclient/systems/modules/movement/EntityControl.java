/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

//Created by squidoodly 10/07/2020

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.ClientPlayerEntityAccessor;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class EntityControl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> maxJump = sgGeneral.add(new BoolSetting.Builder()
        .name("最大跳跃")
        .description("将跳跃力设为最大.")
        .defaultValue(true)
        .build()
    );

    public EntityControl() {
        super(Categories.Movement, "实体控制", "让你在没有鞍的情况下控制可骑乘的实体.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (maxJump.get()) ((ClientPlayerEntityAccessor) mc.player).setMountJumpStrength(1);
    }
}
