/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement.speed;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.speed.modes.Strafe;
import meteordevelopment.meteorclient.systems.modules.movement.speed.modes.Vanilla;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.MovementType;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

public class Speed extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<SpeedModes> speedMode = sgGeneral.add(new EnumSetting.Builder<SpeedModes>()
        .name("模式")
        .description("应用速度的方法。")
        .defaultValue(SpeedModes.Vanilla)
        .onModuleActivated(speedModesSetting -> onSpeedModeChanged(speedModesSetting.get()))
        .onChanged(this::onSpeedModeChanged)
        .build()
    );

    public final Setting<Double> vanillaSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("原版速度")
        .description("以每秒方块数为单位的速度。")
        .defaultValue(5.6)
        .min(0)
        .sliderMax(20)
        .visible(() -> speedMode.get() == SpeedModes.Vanilla)
        .build()
    );

    public final Setting<Double> ncpSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("扫射速度")
        .description("速度。")
        .visible(() -> speedMode.get() == SpeedModes.Strafe)
        .defaultValue(1.6)
        .min(0)
        .sliderMax(3)
        .build()
    );

    public final Setting<Boolean> ncpSpeedLimit = sgGeneral.add(new BoolSetting.Builder()
        .name("速度限制")
        .description("在有非常严格的反作弊的服务器上限制你的速度。")
        .visible(() -> speedMode.get() == SpeedModes.Strafe)
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> timer = sgGeneral.add(new DoubleSetting.Builder()
        .name("计时器")
        .description("timer覆盖.")
        .defaultValue(1)
        .min(0.01)
        .sliderMin(0.01)
        .sliderMax(10)
        .build()
    );

    public final Setting<Boolean> inLiquids = sgGeneral.add(new BoolSetting.Builder()
        .name("在液体中")
        .description("在岩浆或水中使用速度。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> whenSneaking = sgGeneral.add(new BoolSetting.Builder()
        .name("潜行时")
        .description("潜行时使用速度。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> vanillaOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("只在地面上")
        .description("只在站在方块上时使用速度。")
        .visible(() -> speedMode.get() == SpeedModes.Vanilla)
        .defaultValue(false)
        .build()
    );

    private SpeedMode currentMode;

    public Speed() {
        super(Categories.Movement, "速度", "在地面上移动时修改你的移动速度。");

        onSpeedModeChanged(speedMode.get());
    }

    @Override
    public void onActivate() {
        currentMode.onActivate();
    }

    @Override
    public void onDeactivate() {
        Modules.get().get(Timer.class).setOverride(Timer.OFF);
        currentMode.onDeactivate();
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (event.type != MovementType.SELF || stopSpeed()) return;

        if (timer.get() != Timer.OFF) {
            Modules.get().get(Timer.class).setOverride(PlayerUtils.isMoving() ? timer.get() : Timer.OFF);
        }

        currentMode.onMove(event);
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        if (stopSpeed()) return;

        currentMode.onTick();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) currentMode.onRubberband();
    }

    private void onSpeedModeChanged(SpeedModes mode) {
        switch (mode) {
            case Vanilla -> currentMode = new Vanilla();
            case Strafe -> currentMode = new Strafe();
        }
    }

    private boolean stopSpeed() {
        if (mc.player.isGliding() || mc.player.isClimbing() || mc.player.getVehicle() != null) return true;
        if (!whenSneaking.get() && mc.player.isSneaking()) return true;
        if (vanillaOnGround.get() && !mc.player.isOnGround() && speedMode.get() == SpeedModes.Vanilla) return true;
        return !inLiquids.get() && (mc.player.isTouchingWater() || mc.player.isInLava());
    }

    @Override
    public String getInfoString() {
        return currentMode.getHudString();
    }
}
