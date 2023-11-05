/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class Rotation extends Module {
    private final SettingGroup sgYaw = settings.createGroup("偏航");
    private final SettingGroup sgPitch = settings.createGroup("俯仰");

    // Yaw

    private final Setting<LockMode> yawLockMode = sgYaw.add(new EnumSetting.Builder<LockMode>()
        .name("偏航锁定模式")
        .description("锁定偏航的方式。")
        .defaultValue(LockMode.Simple)
        .build()
    );

    private final Setting<Double> yawAngle = sgYaw.add(new DoubleSetting.Builder()
        .name("偏航角度")
        .description("偏航角度(以度为单位)。")
        .defaultValue(0)
        .sliderMax(360)
        .max(360)
        .visible(() -> yawLockMode.get() == LockMode.Simple)
        .build()
    );

    // Pitch

    private final Setting<LockMode> pitchLockMode = sgPitch.add(new EnumSetting.Builder<LockMode>()
        .name("俯仰锁定模式")
        .description("锁定俯仰的方式。")
        .defaultValue(LockMode.Simple)
        .build()
    );

    private final Setting<Double> pitchAngle = sgPitch.add(new DoubleSetting.Builder()
        .name("俯仰角")
        .description("以度为单位的俯仰角。")
        .defaultValue(0)
        .range(-90, 90)
        .sliderRange(-90, 90)
        .visible(() -> pitchLockMode.get() == LockMode.Simple)
        .build()
    );

    public Rotation() {
        super(Categories.Player, "旋转", "改变/锁定你的偏航和俯仰。");
    }

    @Override
    public void onActivate() {
        onTick(null);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        switch (yawLockMode.get()) {
            case Simple -> setYawAngle(yawAngle.get().floatValue());
            case Smart  -> setYawAngle(getSmartYawDirection());
        }

        switch (pitchLockMode.get()) {
            case Simple -> mc.player.setPitch(pitchAngle.get().floatValue());
            case Smart  -> mc.player.setPitch(getSmartPitchDirection());
        }
    }

    private float getSmartYawDirection() {
        return Math.round((mc.player.getYaw() + 1f) / 45f) * 45f;
    }

    private float getSmartPitchDirection() {
        return Math.round((mc.player.getPitch() + 1f) / 30f) * 30f;
    }

    private void setYawAngle(float yawAngle) {
        mc.player.setYaw(yawAngle);
        mc.player.headYaw = yawAngle;
        mc.player.bodyYaw = yawAngle;
    }

    public enum LockMode {
        Smart,
        Simple,
        None
    }
}
