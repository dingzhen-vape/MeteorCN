/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BreakIndicators;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.List;

public class PacketMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染");

    // General

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("延迟")
        .description("挖掘方块之间的延迟，以刻为单位。")
        .defaultValue(1)
        .min(0)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("旋转")
        .description("挖掘时向服务器发送旋转包。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("自动切换")
        .description("当方块可以瞬间被挖掘时，自动切换到最佳工具。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> notOnUse = sgGeneral.add(new BoolSetting.Builder()
        .name("不在使用时")
        .description("如果你正在使用一个物品，不会自动切换。")
        .defaultValue(true)
        .visible(autoSwitch::get)
        .build()
    );

    // Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("渲染")
        .description("是否渲染正在挖掘的方块。")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("形状的渲染方式。")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> readySideColor = sgRender.add(new ColorSetting.Builder()
        .name("准备好的侧面颜色")
        .description("可以被破坏的方块的侧面颜色。")
        .defaultValue(new SettingColor(0, 204, 0, 10))
        .build()
    );

    private final Setting<SettingColor> readyLineColor = sgRender.add(new ColorSetting.Builder()
        .name("准备好的线条颜色")
        .description("可以被破坏的方块的线条颜色。")
        .defaultValue(new SettingColor(0, 204, 0, 255))
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("侧面颜色")
        .description("渲染的方块的侧面颜色。")
        .defaultValue(new SettingColor(204, 0, 0, 10))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("线条颜色")
        .description("渲染的方块的线条颜色。")
        .defaultValue(new SettingColor(204, 0, 0, 255))
        .build()
    );

    private final Pool<MyBlock> blockPool = new Pool<>(MyBlock::new);
    public final List<MyBlock> blocks = new ArrayList<>();

    private boolean swapped, shouldUpdateSlot;

    public PacketMine() {
        super(Categories.World, "包挖掘", "发送包来挖掘方块，无需挖掘动画。");
    }

    @Override
    public void onActivate() {
        swapped = false;
    }

    @Override
    public void onDeactivate() {
        for (MyBlock block : blocks) blockPool.free(block);
        blocks.clear();
        if (shouldUpdateSlot) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
            shouldUpdateSlot = false;
        }
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (!BlockUtils.canBreak(event.blockPos)) return;

        event.cancel();

        swapped = false;

        if (!isMiningBlock(event.blockPos)) {
            blocks.add(blockPool.get().set(event));
        }
    }

    public boolean isMiningBlock(BlockPos pos) {
        for (MyBlock block : blocks) {
            if (block.blockPos.equals(pos)) return true;
        }

        return false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        blocks.removeIf(MyBlock::shouldRemove);

        if (shouldUpdateSlot) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
            shouldUpdateSlot = false;
        }

        if (!blocks.isEmpty()) blocks.get(0).mine();

        if (!swapped && autoSwitch.get() && (!mc.player.isUsingItem() || !notOnUse.get())) {
            for (MyBlock block : blocks) {
                if (block.isReady()) {
                    FindItemResult slot = InvUtils.findFastestTool(block.blockState);
                    if (!slot.found() || mc.player.getInventory().selectedSlot == slot.slot()) continue;
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot.slot()));
                    swapped = true;
                    shouldUpdateSlot = true;
                    break;
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (render.get()) {
            for (MyBlock block : blocks) {
                if (Modules.get().get(BreakIndicators.class).isActive() && Modules.get().get(BreakIndicators.class).packetMine.get() && block.mining) {
                    continue;
                } else block.render(event);
            }
        }
    }

    public class MyBlock {
        public BlockPos blockPos;
        public BlockState blockState;
        public Block block;

        public Direction direction;

        public int timer;
        public boolean mining;
        public double progress;

        public MyBlock set(StartBreakingBlockEvent event) {
            this.blockPos = event.blockPos;
            this.direction = event.direction;
            this.blockState = mc.world.getBlockState(blockPos);
            this.block = blockState.getBlock();
            this.timer = delay.get();
            this.mining = false;
            this.progress = 0;

            return this;
        }

        public boolean shouldRemove() {
            boolean remove = mc.world.getBlockState(blockPos).getBlock() != block || Utils.distance(mc.player.getX() - 0.5, mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()), mc.player.getZ() - 0.5, blockPos.getX() + direction.getOffsetX(), blockPos.getY() + direction.getOffsetY(), blockPos.getZ() + direction.getOffsetZ()) > mc.interactionManager.getReachDistance();

            if (remove) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, blockPos, direction));
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }

            return remove;
        }

        public boolean isReady() {
            return progress >= 1;
        }

        public void mine() {
            if (rotate.get()) Rotations.rotate(Rotations.getYaw(blockPos), Rotations.getPitch(blockPos), 50, this::sendMinePackets);
            else sendMinePackets();

            double bestScore = -1;
            int bestSlot = -1;

            for (int i = 0; i < 9; i++) {
                double score = mc.player.getInventory().getStack(i).getMiningSpeedMultiplier(blockState);

                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }

            progress += BlockUtils.getBreakDelta(bestSlot != -1 ? bestSlot : mc.player.getInventory().selectedSlot, blockState);
        }

        private void sendMinePackets() {
            if (timer <= 0) {
                if (!mining) {
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, blockPos, direction));
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, direction));

                    mining = true;
                }
            }
            else {
                timer--;
            }
        }

        public void render(Render3DEvent event) {
            VoxelShape shape = mc.world.getBlockState(blockPos).getOutlineShape(mc.world, blockPos);

            double x1 = blockPos.getX();
            double y1 = blockPos.getY();
            double z1 = blockPos.getZ();
            double x2 = blockPos.getX() + 1;
            double y2 = blockPos.getY() + 1;
            double z2 = blockPos.getZ() + 1;

            if (!shape.isEmpty()) {
                x1 = blockPos.getX() + shape.getMin(Direction.Axis.X);
                y1 = blockPos.getY() + shape.getMin(Direction.Axis.Y);
                z1 = blockPos.getZ() + shape.getMin(Direction.Axis.Z);
                x2 = blockPos.getX() + shape.getMax(Direction.Axis.X);
                y2 = blockPos.getY() + shape.getMax(Direction.Axis.Y);
                z2 = blockPos.getZ() + shape.getMax(Direction.Axis.Z);
            }

            if (isReady()) {
                event.renderer.box(x1, y1, z1, x2, y2, z2, readySideColor.get(), readyLineColor.get(), shapeMode.get(), 0);
            } else {
                event.renderer.box(x1, y1, z1, x2, y2, z2, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }
        }
    }
}
