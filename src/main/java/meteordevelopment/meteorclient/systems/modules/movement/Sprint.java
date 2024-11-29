/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.ClientPlayerEntityAccessor;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;

public class Sprint extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Mode {
        Strict,
        Rage
    }

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("冲刺模式")
        .description("奔跑模式。")
        .defaultValue(Mode.Strict)
        .build()
    );

    public final Setting<Boolean> jumpFix = sgGeneral.add(new BoolSetting.Builder()
        .name("跳跃修复")
        .description("是否修正跳跃方向。")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Rage)
        .build()
    );

    private final Setting<Boolean> keepSprint = sgGeneral.add(new BoolSetting.Builder()
        .name("保持冲刺")
        .description("攻击实体后是否保持冲刺状态。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> unsprintOnHit = sgGeneral.add(new BoolSetting.Builder()
        .name("攻击时停止冲刺")
        .description("攻击时是否停止冲刺，以确保你能获得暴击和扫射攻击。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> unsprintInWater = sgGeneral.add(new BoolSetting.Builder()
        .name("水中停止冲刺")
        .description("是否在水中停止冲刺。")
        .defaultValue(true)
        .build()
    );

    public Sprint() {
        super(Categories.Movement, "冲刺", "自动冲刺。");
    }

    @Override
    public void onDeactivate() {
        mc.player.setSprinting(false);
    }

    @EventHandler
    private void onTickMovement(TickEvent.Post event) {
        if (shouldSprint()) mc.player.setSprinting(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onPacketSend(PacketEvent.Send event) {
        if (!unsprintOnHit.get() || !(event.packet instanceof IPlayerInteractEntityC2SPacket packet) || packet.meteor$getType() != PlayerInteractEntityC2SPacket.InteractType.ATTACK) return;

        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        mc.player.setSprinting(false);
    }

    @EventHandler
    private void onPacketSent(PacketEvent.Sent event) {
        if (!unsprintOnHit.get() || !keepSprint.get()) return;
        if (!(event.packet instanceof IPlayerInteractEntityC2SPacket packet) || packet.meteor$getType() != PlayerInteractEntityC2SPacket.InteractType.ATTACK) return;

        if (shouldSprint() && !mc.player.isSprinting()) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
            mc.player.setSprinting(true);
        }
    }

    public boolean shouldSprint() {
        if (unsprintInWater.get() && (mc.player.isTouchingWater() || mc.player.isSubmergedInWater())) return false;

        boolean strictSprint = mc.player.forwardSpeed > 1.0E-5F
            && ((ClientPlayerEntityAccessor) mc.player).invokeCanSprint()
            && (!mc.player.horizontalCollision || mc.player.collidedSoftly)
            && !(mc.player.isTouchingWater() && !mc.player.isSubmergedInWater());

        return isActive() && (mode.get() == Mode.Rage || strictSprint) && (mc.currentScreen == null || Modules.get().get(GUIMove.class).sprint.get());
    }

    public boolean rageSprint() {
        return isActive() && mode.get() == Mode.Rage;
    }

    public boolean stopSprinting() {
        return !isActive() || !keepSprint.get();
    }
}
