/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

//Created by squidoodly 10/07/2020

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.ClientPlayerEntityAccessor;
import meteordevelopment.meteorclient.mixininterface.IHorseBaseEntity;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AbstractHorseEntity;

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

    @Override
    public void onDeactivate() {
        if (!Utils.canUpdate() || mc.world.getEntities() == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof AbstractHorseEntity) ((IHorseBaseEntity) entity).setSaddled(false);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof AbstractHorseEntity) ((IHorseBaseEntity) entity).setSaddled(true);
        }

        if (maxJump.get()) ((ClientPlayerEntityAccessor) mc.player).setMountJumpStrength(1);
    }
}
