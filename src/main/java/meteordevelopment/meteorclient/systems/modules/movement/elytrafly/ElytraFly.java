/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement.elytrafly;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.modes.Packet;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.modes.Pitch40;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.modes.Bounce;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.modes.Vanilla;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.meteorclient.systems.modules.player.Rotation;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class ElytraFly extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgInventory = settings.createGroup("库存");
    private final SettingGroup sgAutopilot = settings.createGroup("自动驾驶");

    // General

    public final Setting<ElytraFlightModes> flightMode = sgGeneral.add(new EnumSetting.Builder<ElytraFlightModes>()
        .name("模式")
        .description("飞行模式。")
        .defaultValue(ElytraFlightModes.Vanilla)
        .onModuleActivated(flightModesSetting -> onModeChanged(flightModesSetting.get()))
        .onChanged(this::onModeChanged)
        .build()
    );

    public final Setting<Boolean> autoTakeOff = sgGeneral.add(new BoolSetting.Builder()
        .name("自动起飞")
        .description("按住跳跃时自动起飞,无需二段跳。")
        .defaultValue(false)
        .visible(() -> flightMode.get() != ElytraFlightModes.Pitch40 && flightMode.get() != ElytraFlightModes.Bounce)
        .build()
    );

    public final Setting<Double> fallMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("坠落倍数")
        .description("控制你自然下降的速度。")
        .defaultValue(0.01)
        .min(0)
        .visible(() -> flightMode.get() != ElytraFlightModes.Pitch40 && flightMode.get() != ElytraFlightModes.Bounce)
        .build()
    );

    public final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("水平速度")
        .description("你前进和后退的速度。")
        .defaultValue(1)
        .min(0)
        .visible(() -> flightMode.get() != ElytraFlightModes.Pitch40 && flightMode.get() != ElytraFlightModes.Bounce)
        .build()
    );

    public final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("垂直速度")
        .description("你上升和下降的速度。")
        .defaultValue(1)
        .min(0)
        .visible(() -> flightMode.get() != ElytraFlightModes.Pitch40 && flightMode.get() != ElytraFlightModes.Bounce)
        .build()
    );

    public final Setting<Boolean> acceleration = sgGeneral.add(new BoolSetting.Builder()
        .name("加速度")
        .defaultValue(false)
        .visible(() -> flightMode.get() != ElytraFlightModes.Pitch40 && flightMode.get() != ElytraFlightModes.Bounce)
        .build()
    );

    public final Setting<Double> accelerationStep = sgGeneral.add(new DoubleSetting.Builder()
        .name("acceleration-step")
        .min(0.1)
        .max(5)
        .defaultValue(1)
        .visible(() -> flightMode.get() != ElytraFlightModes.Pitch40 && acceleration.get() && flightMode.get() != ElytraFlightModes.Bounce)
        .build()
    );

    public final Setting<Double> accelerationMin = sgGeneral.add(new DoubleSetting.Builder()
        .name("acceleration-start")
        .min(0.1)
        .defaultValue(0)
        .visible(() -> flightMode.get() != ElytraFlightModes.Pitch40 && acceleration.get() && flightMode.get() != ElytraFlightModes.Bounce)
        .build()
    );

    public final Setting<Boolean> stopInWater = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-in-water")
        .description("停止在水中飞行。")
        .defaultValue(true)
        .visible(() -> flightMode.get() != ElytraFlightModes.Bounce)
        .build()
    );

    public final Setting<Boolean> dontGoIntoUnloadedChunks = sgGeneral.add(new BoolSetting.Builder()
        .name("no-unloaded-chunks")
        .description("阻止你进入卸载的块。")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> autoHover = sgGeneral.add(new BoolSetting.Builder()
        .name("auto -hover")
        .description("按住 Shift 键时自动悬停在离地 0.3 格的位置。")
        .defaultValue(false)
        .visible(() -> flightMode.get() != ElytraFlightModes.Bounce)
        .build()
    );

    public final Setting<Boolean> noCrash = sgGeneral.add(new BoolSetting.Builder()
        .name("无碰撞")
        .description("阻止你撞到墙壁。")
        .defaultValue(false)
        .visible(() -> flightMode.get() != ElytraFlightModes.Bounce)
        .build()
    );

    public final Setting<Integer> crashLookAhead = sgGeneral.add(new IntSetting.Builder()
        .name("碰撞前视")
        .description("飞行时向前看的距离。 ")
        .defaultValue(5)
        .range(1, 15)
        .sliderMin(1)
        .visible(() -> noCrash.get() && flightMode.get() != ElytraFlightModes.Bounce)
        .build()
    );

    private final Setting<Boolean> instaDrop = sgGeneral.add(new BoolSetting.Builder()
        .name("insta-drop")
        .description("让你立即脱离飞行。")
        .defaultValue(false)
        .visible(() -> flightMode.get() != ElytraFlightModes.Bounce)
        .build()
    );

    public final Setting<Double> pitch40lowerBounds = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch40-lower-bounds")
        .description("pitch40 的底部高度边界。")
        .defaultValue(80)
        .min(-128)
        .sliderMax(360)
        .visible(() -> flightMode.get() == ElytraFlightModes.Pitch40)
        .build()
    );

    public final Setting<Double> pitch40upperBounds = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch40-upper-bounds")
        .description("上部高度边界foritch40.")
        .defaultValue(120)
        .min(-128)
        .sliderMax(360)
        .visible(() -> flightMode.get() == ElytraFlightModes.Pitch40)
        .build()
    );

    public final Setting<Double> pitch40rotationSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch40-rotate-speed")
        .description("俯仰旋转的速度(每刻度数)")
        .defaultValue(4)
        .min(1)
        .sliderMax(6)
        .visible(() -> flightMode.get() == ElytraFlightModes.Pitch40)
        .build()
    );

    public final Setting<Boolean> autoJump = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-jump")
        .description("自动为你跳跃。")
        .defaultValue(true)
        .visible(() -> flightMode.get() == ElytraFlightModes.Bounce)
        .build()
    );

    public final Setting<Rotation.LockMode> yawLockMode = sgGeneral.add(new EnumSetting.Builder<Rotation.LockMode>()
            .name("yaw-lock")
            .description("是否启用是否偏航锁定")
            .defaultValue(Rotation.LockMode.Smart)
            .visible(() -> flightMode.get() == ElytraFlightModes.Bounce)
            .build()
    );

    public final Setting<Double> pitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("俯仰")
        .description("使用弹跳模式时要查看的俯仰角。")
        .defaultValue(85)
        .range(0, 90)
        .sliderRange(0, 90)
        .visible(() -> flightMode.get() == ElytraFlightModes.Bounce)
        .build()
    );

    public final Setting<Double> yaw = sgGeneral.add(new DoubleSetting.Builder()
            .name("偏航")
            .description("在弹跳模式下使用简单旋转锁定时要查看的偏航角。")
            .defaultValue(0)
            .range(0, 360)
            .sliderRange(0,360)
            .visible(() -> flightMode.get() == ElytraFlightModes.Bounce && yawLockMode.get() == Rotation.LockMode.Simple)
            .build()
    );

    public final Setting<Boolean> restart = sgGeneral.add(new BoolSetting.Builder()
        .name("重新启动")
        .description("用橡皮筋时重新开始用鞘翅飞行。")
        .defaultValue(true)
        .visible(() -> flightMode.get() == ElytraFlightModes.Bounce)
        .build()
    );

    public final Setting<Integer> restartDelay = sgGeneral.add(new IntSetting.Builder()
        .name("重启延迟")
        .description("橡皮筋后再次重新启动鞘翅之前要等待多少个滴答声。")
        .defaultValue(7)
        .min(0)
        .sliderRange(0, 20)
        .visible(() -> flightMode.get() == ElytraFlightModes.Bounce && restart.get())
        .build()
    );

    public final Setting<Boolean> sprint = sgGeneral.add(new BoolSetting.Builder()
        .name("冲刺")
        .description("一直冲刺。如果关闭,它只会在玩家接触地面时冲刺。")
        .defaultValue(true)
        .visible(() -> flightMode.get() == ElytraFlightModes.Bounce)
        .build()
    );

    // Inventory

    public final Setting<Boolean> replace = sgInventory.add(new BoolSetting.Builder()
        .name("鞘翅替换")
        .description("用新的鞘翅替换损坏的鞘翅。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Integer> replaceDurability = sgInventory.add(new IntSetting.Builder()
        .name("replace-durability")
        .description("你的鞘翅将被替换的耐久性阈值at.")
        .defaultValue(2)
        .range(1, Items.ELYTRA.getMaxDamage() - 1)
        .sliderRange(1, Items.ELYTRA.getMaxDamage() - 1)
        .visible(replace::get)
        .build()
    );

    public final Setting<ChestSwapMode> chestSwap = sgInventory.add(new EnumSetting.Builder<ChestSwapMode>()
        .name("chest-swap")
        .description("切换此模块时启用 ChestSwap。")
        .defaultValue(ChestSwapMode.Never)
        .build()
    );

    public final Setting<Boolean> autoReplenish = sgInventory.add(new BoolSetting.Builder()
        .name("replenish-fireworks")
        .description("将烟花移动到选定的热栏插槽中。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Integer> replenishSlot = sgInventory.add(new IntSetting.Builder()
        .name("replenish-slot")
        .description("插槽自动移动移动烟花到。")
        .defaultValue(9)
        .range(1, 9)
        .sliderRange(1, 9)
        .visible(autoReplenish::get)
        .build()
    );

    // Autopilot

    public final Setting<Boolean> autoPilot = sgAutopilot.add(new BoolSetting.Builder()
        .name("自动驾驶")
        .description("鞘翅飞行时向前移动。")
        .defaultValue(false)
        .visible(() -> flightMode.get() != ElytraFlightModes.Pitch40 && flightMode.get() != ElytraFlightModes.Bounce)
        .build()
    );

    public final Setting<Boolean> useFireworks = sgAutopilot.add(new BoolSetting.Builder()
        .name("use-fireworks")
        .description("您选择的每一秒都使用烟花火箭。")
        .defaultValue(false)
        .visible(() -> autoPilot.get() && flightMode.get() != ElytraFlightModes.Pitch40 && flightMode.get() != ElytraFlightModes.Bounce)
        .build()
    );

    public final Setting<Double> autoPilotFireworkDelay = sgAutopilot.add(new DoubleSetting.Builder()
        .name("firework-delay")
        .description("之间的延迟(以秒为单位)如果 \"Use Fireworks\" 启用,则使用烟花。")
        .min(1)
        .defaultValue(8)
        .sliderMax(20)
        .visible(() -> useFireworks.get() && flightMode.get() != ElytraFlightModes.Pitch40 && flightMode.get() != ElytraFlightModes.Bounce)
        .build()
    );

    public final Setting<Double> autoPilotMinimumHeight = sgAutopilot.add(new DoubleSetting.Builder()
        .name("最小高度")
        .description("自动驾驶仪的最小高度。")
        .defaultValue(120)
        .min(-128)
        .sliderMax(260)
        .visible(() -> autoPilot.get() && flightMode.get() != ElytraFlightModes.Pitch40 && flightMode.get() != ElytraFlightModes.Bounce)
        .build()
    );

    private ElytraFlightMode currentMode = new Vanilla();

    public ElytraFly() {
        super(Categories.Movement, "鞘翅飞行", "让你更好地控制你的鞘翅。");
    }

    @Override
    public void onActivate() {
        currentMode.onActivate();
        if ((chestSwap.get() == ChestSwapMode.Always || chestSwap.get() == ChestSwapMode.WaitForGround)
            && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
            Modules.get().get(ChestSwap.class).swap();
        }
    }

    @Override
    public void onDeactivate() {
        if (autoPilot.get()) mc.options.forwardKey.setPressed(false);

        if (chestSwap.get() == ChestSwapMode.Always && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
            Modules.get().get(ChestSwap.class).swap();
        } else if (chestSwap.get() == ChestSwapMode.WaitForGround) {
            enableGroundListener();
        }

        if (mc.player.isFallFlying() && instaDrop.get()) {
            enableInstaDropListener();
        }

        currentMode.onDeactivate();
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (!(mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() instanceof ElytraItem)) return;

        currentMode.autoTakeoff();

        if (mc.player.isFallFlying()) {

            if (flightMode.get() != ElytraFlightModes.Bounce) {
                currentMode.velX = 0;
                currentMode.velY = event.movement.y;
                currentMode.velZ = 0;
                currentMode.forward = Vec3d.fromPolar(0, mc.player.getYaw()).multiply(0.1);
                currentMode.right = Vec3d.fromPolar(0, mc.player.getYaw() + 90).multiply(0.1);

                // Handle stopInWater
                if (mc.player.isTouchingWater() && stopInWater.get()) {
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    return;
                }

                currentMode.handleFallMultiplier();
                currentMode.handleAutopilot();

                currentMode.handleAcceleration();
                currentMode.handleHorizontalSpeed(event);
                currentMode.handleVerticalSpeed(event);
            }

            int chunkX = (int) ((mc.player.getX() + currentMode.velX) / 16);
            int chunkZ = (int) ((mc.player.getZ() + currentMode.velZ) / 16);
            if (dontGoIntoUnloadedChunks.get()) {
                if (mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                    if (flightMode.get() != ElytraFlightModes.Bounce) ((IVec3d) event.movement).set(currentMode.velX, currentMode.velY, currentMode.velZ);
                } else {
                    ((IVec3d) event.movement).set(0, currentMode.velY, 0);
                }
            } else if (flightMode.get() != ElytraFlightModes.Bounce) ((IVec3d) event.movement).set(currentMode.velX, currentMode.velY, currentMode.velZ);

            if (flightMode.get() != ElytraFlightModes.Bounce) currentMode.onPlayerMove();
        } else {
            if (currentMode.lastForwardPressed && flightMode.get() != ElytraFlightModes.Bounce) {
                mc.options.forwardKey.setPressed(false);
                currentMode.lastForwardPressed = false;
            }
        }

        if (noCrash.get() && mc.player.isFallFlying() && flightMode.get() != ElytraFlightModes.Bounce) {
            Vec3d lookAheadPos = mc.player.getPos().add(mc.player.getVelocity().normalize().multiply(crashLookAhead.get()));
            RaycastContext raycastContext = new RaycastContext(mc.player.getPos(), new Vec3d(lookAheadPos.getX(), mc.player.getY(), lookAheadPos.getZ()), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
            BlockHitResult hitResult = mc.world.raycast(raycastContext);
            if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                ((IVec3d) event.movement).set(0, currentMode.velY, 0);
            }
        }

        if (autoHover.get() && mc.player.input.sneaking && !Modules.get().get(Freecam.class).isActive() && mc.player.isFallFlying() && flightMode.get() != ElytraFlightModes.Bounce) {
            BlockState underState = mc.world.getBlockState(mc.player.getBlockPos().down());
            Block under = underState.getBlock();
            BlockState under2State = mc.world.getBlockState(mc.player.getBlockPos().down().down());
            Block under2 = under2State.getBlock();

            final boolean underCollidable = under.collidable || !underState.getFluidState().isEmpty();
            final boolean under2Collidable = under2.collidable || !under2State.getFluidState().isEmpty();

            if (!underCollidable && under2Collidable) {
                ((IVec3d)event.movement).set(event.movement.x, -0.1f, event.movement.z);

                mc.player.setPitch(MathHelper.clamp(mc.player.getPitch(0), -50.f, 20.f)); // clamp between -50 and 20 (>= 30 will pop you off, but lag makes that threshold lower)
            }

            if (underCollidable) {
                ((IVec3d)event.movement).set(event.movement.x, -0.03f, event.movement.z);

                mc.player.setPitch(MathHelper.clamp(mc.player.getPitch(0), -50.f, 20.f));

                if (mc.player.getPos().y <= mc.player.getBlockPos().down().getY() + 1.34f) {
                    ((IVec3d)event.movement).set(event.movement.x, 0, event.movement.z);
                    mc.player.setSneaking(false);
                    mc.player.input.sneaking = false;
                }
            }
        }
    }

    public boolean canPacketEfly() {
        return isActive() && flightMode.get() == ElytraFlightModes.Packet && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() instanceof ElytraItem && !mc.player.isOnGround();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        currentMode.onTick();
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        currentMode.onPreTick();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        currentMode.onPacketSend(event);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        currentMode.onPacketReceive(event);
    }

    private void onModeChanged(ElytraFlightModes mode) {
        switch (mode) {
            case Vanilla -> currentMode = new Vanilla();
            case Packet -> currentMode = new Packet();
            case Pitch40 -> {
                currentMode = new Pitch40();
                autoPilot.set(false); // Pitch 40 is an autopilot of its own
            }
            case Bounce -> currentMode = new Bounce();
        }
    }

    //Ground
    private class StaticGroundListener {
        @EventHandler
        private void chestSwapGroundListener(PlayerMoveEvent event) {
            if (mc.player != null && mc.player.isOnGround()) {
                if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                    Modules.get().get(ChestSwap.class).swap();
                    disableGroundListener();
                }
            }
        }
    }

    private final StaticGroundListener staticGroundListener = new StaticGroundListener();

    protected void enableGroundListener() {
        MeteorClient.EVENT_BUS.subscribe(staticGroundListener);
    }

    protected void disableGroundListener() {
        MeteorClient.EVENT_BUS.unsubscribe(staticGroundListener);
    }

    //Drop
    private class StaticInstaDropListener {
        @EventHandler
        private void onInstadropTick(TickEvent.Post event) {
            if (mc.player != null && mc.player.isFallFlying()) {
                mc.player.setVelocity(0, 0, 0);
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
            } else {
                disableInstaDropListener();
            }
        }
    }

    private final StaticInstaDropListener staticInstadropListener = new StaticInstaDropListener();

    protected void enableInstaDropListener() {
        MeteorClient.EVENT_BUS.subscribe(staticInstadropListener);
    }

    protected void disableInstaDropListener() {
        MeteorClient.EVENT_BUS.unsubscribe(staticInstadropListener);
    }

    @Override
    public String getInfoString() {
        return currentMode.getHudString();
    }

    public enum ChestSwapMode {
        Always,
        Never,
        WaitForGround
    }

    public enum AutoPilotMode {
        Vanilla,
        Pitch40
    }
}
