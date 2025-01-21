/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

public class AutoWasp extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("水平速度")
        .description("水平鞘翅速度。")
        .defaultValue(2.0)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("垂直速度")
        .description("垂直鞘翅速度。")
        .defaultValue(3.0)
        .build()
    );

    private final Setting<Boolean> avoidLanding = sgGeneral.add(new BoolSetting.Builder()
        .name("蜻蜓点水")
        .description("如果您的目标在地面上，将尝试避免着陆。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> predictMovement = sgGeneral.add(new BoolSetting.Builder()
        .name("预测移动")
        .description("尝试根据目标的移动预测目标位置。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("好人")
        .description("只会跟随朋友。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Action> action = sgGeneral.add(new EnumSetting.Builder<Action>()
        .name("丢失策略")
        .description("如果失去目标该怎么办。")
        .defaultValue(Action.TOGGLE)
        .build()
    );

    private final Setting<Vector3d> offset = sgGeneral.add(new Vector3dSetting.Builder()
        .name("offset")
        .description("与目标之间的偏移量是多少。")
        .defaultValue(0, 0, 0)
        .build()
    );

    public PlayerEntity target;
    private int jumpTimer = 0;
    private boolean incrementJumpTimer = false;

    public AutoWasp() {
        super(Categories.Movement, "auto-wasp", "Wasps for you. Unable to traverse around blocks, assumes a clear straight line to the target.");
    }

    @Override
    public void onActivate() {
        if (target == null || target.isRemoved()) {
            target = (PlayerEntity) TargetUtils.get(entity -> {
                if (!(entity instanceof PlayerEntity) || entity == mc.player) return false;
                if (((PlayerEntity) entity).isDead() || ((PlayerEntity) entity).getHealth() <= 0) return false;
                return !onlyFriends.get() || Friends.get().get((PlayerEntity) entity) != null;
            }, SortPriority.LowestDistance);

            if (target == null) {
                error("没有有效目标。");
                toggle();
                return;
            } else info(target.getName().getString() + "设置为目标。");
        }

        jumpTimer = 0;
        incrementJumpTimer = false;
    }

    @Override
    public void onDeactivate() {
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (target.isRemoved()) {
            warning("丢失目标！");

            switch (action.get()) {
                case CHOOSE_NEW_TARGET -> onActivate();
                case TOGGLE -> toggle();
                case DISCONNECT ->
                    mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("%s[%sAuto Wasp%s] 丢失目标。".formatted(Formatting.GRAY, Formatting.BLUE, Formatting.GRAY))));
            }

            if (!isActive()) return;
        }

        if (!(mc.player.getEquippedStack(EquipmentSlot.CHEST).contains(DataComponentTypes.GLIDER))) return;

        if (incrementJumpTimer) {
            jumpTimer++;
        }

        if (!mc.player.isGliding()) {
            if (!incrementJumpTimer) incrementJumpTimer = true;

            if (mc.player.isOnGround() && incrementJumpTimer) {
                mc.player.jump();
                return;
            }

            if (jumpTimer >= 4) {
                jumpTimer = 0;
                mc.player.setJumping(false);
                mc.player.setSprinting(true);
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
        } else {
            incrementJumpTimer = false;
            jumpTimer = 0;
        }
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        if (!(mc.player.getEquippedStack(EquipmentSlot.CHEST).contains(DataComponentTypes.GLIDER))) return;
        if (!mc.player.isGliding()) return;

        double xVel = 0, yVel = 0, zVel = 0;

        Vec3d targetPos = target.getPos().add(offset.get().x, offset.get().y, offset.get().z);

        if (predictMovement.get()) targetPos.add(PlayerEntity.adjustMovementForCollisions(target, target.getVelocity(),
            target.getBoundingBox(), mc.world, mc.world.getEntityCollisions(target, target.getBoundingBox().stretch(target.getVelocity()))));

        if (avoidLanding.get()) {
            double d = target.getBoundingBox().getLengthX() / 2; // x length = z length for players

            //get the block pos of the block underneath the corner of the targets bounding box
            for (Direction dir : Direction.HORIZONTAL) {
                BlockPos pos = BlockPos.ofFloored(targetPos.offset(dir, d).offset(dir.rotateYClockwise(), d)).down();
                if (mc.world.getBlockState(pos).getBlock().collidable && Math.abs(targetPos.getY() - (pos.getY() + 1)) <= 0.25) {
                    targetPos = new Vec3d(targetPos.x, pos.getY() + 1.25, targetPos.z);
                    break;
                }
            }
        }

        double xDist = targetPos.getX() - mc.player.getX();
        double zDist = targetPos.getZ() - mc.player.getZ();

        double absX = Math.abs(xDist);
        double absZ = Math.abs(zDist);

        double diag = 0;
        if (absX > 1.0E-5F && absZ > 1.0E-5F) diag = 1 / Math.sqrt(absX * absX + absZ * absZ);

        if (absX > 1.0E-5F) {
            if (absX < horizontalSpeed.get()) xVel = xDist;
            else xVel = horizontalSpeed.get() * Math.signum(xDist);

            if (diag != 0) xVel *= (absX * diag);
        }

        if (absZ > 1.0E-5F) {
            if (absZ < horizontalSpeed.get()) zVel = zDist;
            else zVel = horizontalSpeed.get() * Math.signum(zDist);

            if (diag != 0) zVel *= (absZ * diag);
        }

        double yDist = targetPos.getY() - mc.player.getY();
        if (Math.abs(yDist) > 1.0E-5F) {
            if (Math.abs(yDist) < verticalSpeed.get()) yVel = yDist;
            else yVel = verticalSpeed.get() * Math.signum(yDist);
        }

        ((IVec3d) event.movement).meteor$set(xVel, yVel, zVel);
    }

    public enum Action {
        TOGGLE,
        CHOOSE_NEW_TARGET,
        DISCONNECT;

        @Override
        public String toString() {
            return switch (this) {
                case TOGGLE -> "切换模块";
                case CHOOSE_NEW_TARGET -> "选择新目标";
                case DISCONNECT -> "断开连接";
            };
        }
    }
}
