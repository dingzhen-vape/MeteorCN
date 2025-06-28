/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ParticleTypeListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;

import java.util.List;

public class Trail extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<ParticleType<?>>> particles = sgGeneral.add(new ParticleTypeListSetting.Builder()
        .name("粒子")
        .description("要绘制的粒子。")
        .defaultValue(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, ParticleTypes.CAMPFIRE_COSY_SMOKE)
        .build()
    );

    private final Setting<Boolean> pause = sgGeneral.add(new BoolSetting.Builder()
        .name("静止时暂停")
        .description("当你不移动时是否添加粒子。")
        .defaultValue(true)
        .build()
    );

    public Trail() {
        super(Categories.Render, "拖尾", "在你的玩家后面渲染一个可自定义的拖尾。");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (pause.get()
            && mc.player.getX() == mc.player.lastX
            && mc.player.getY() == mc.player.lastY
            && mc.player.getZ() == mc.player.lastZ) return;

        for (ParticleType<?> particleType : particles.get()) {
            mc.world.addParticleClient((ParticleEffect) particleType, mc.player.getX(), mc.player.getY(), mc.player.getZ(), 0, 0, 0);
        }
    }
}
