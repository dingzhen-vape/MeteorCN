/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * @author seasnail8169
 */
public class Burrow extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Block> block = sgGeneral.add(new EnumSetting.Builder<Block>()
        .name("使用方块")
        .description("用于埋身的方块.")
        .defaultValue(Block.EChest)
        .build()
    );

    private final Setting<Boolean> instant = sgGeneral.add(new BoolSetting.Builder()
        .name("瞬间")
        .description("用数据包跳跃而不是原版跳跃.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> automatic = sgGeneral.add(new BoolSetting.Builder()
        .name("自动")
        .description("激活后自动埋身而不是等待跳跃.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> triggerHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("触发高度")
        .description("跳跃多高才触发橡皮筋.")
        .defaultValue(1.12)
        .range(0.01, 1.4)
        .sliderRange(0.01, 1.4)
        .build()
    );

    private final Setting<Double> rubberbandHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("橡皮筋高度")
        .description("尝试造成橡皮筋的距离.")
        .defaultValue(12)
        .sliderMin(-30)
        .sliderMax(30)
        .build()
    );

    private final Setting<Double> timer = sgGeneral.add(new DoubleSetting.Builder()
        .name("计时器")
        .description("timer覆盖.")
        .defaultValue(1)
        .min(0.01)
        .sliderRange(0.01, 10)
        .build()
    );

    private final Setting<Boolean> onlyInHole = sgGeneral.add(new BoolSetting.Builder()
        .name("只在洞里")
        .description("不在洞里时停止埋身.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> center = sgGeneral.add(new BoolSetting.Builder()
        .name("居中")
        .description("埋身前将你居中到方块的中间.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("旋转")
        .description("服务器端面向你放置的方块.")
        .defaultValue(true)
        .build()
    );

    private final BlockPos.Mutable blockPos = new BlockPos.Mutable();
    private boolean shouldBurrow;

    public Burrow() {
        super(Categories.Combat, "埋身", "尝试将你卡进方块里.");
    }

    @Override
    public void onActivate() {
        if (!mc.world.getBlockState(mc.player.getBlockPos()).isReplaceable()) {
            error("已经埋身,禁用.");
            toggle();
            return;
        }

        if (!PlayerUtils.isInHole(false) && onlyInHole.get()) {
            error("不在洞里,禁用.");
            toggle();
            return;
        }

        if (!checkHead()) {
            error("头顶空间不足,禁用.");
            toggle();
            return;
        }

        FindItemResult result = getItem();

        if (!result.isHotbar() && !result.isOffhand()) {
            error("没有找到埋身方块,禁用.");
            toggle();
            return;
        }

        blockPos.set(mc.player.getBlockPos());

        Modules.get().get(Timer.class).setOverride(this.timer.get());

        shouldBurrow = false;

        if (automatic.get()) {
            if (instant.get()) shouldBurrow = true;
            else mc.player.jump();
        } else {
            info("等待手动跳跃.");
        }
    }

    @Override
    public void onDeactivate() {
        Modules.get().get(Timer.class).setOverride(Timer.OFF);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!instant.get()) shouldBurrow = mc.player.getY() > blockPos.getY() + triggerHeight.get();
        if (!shouldBurrow && instant.get()) blockPos.set(mc.player.getBlockPos());

        if (shouldBurrow) {
           if (rotate.get())
                Rotations.rotate(Rotations.getYaw(mc.player.getBlockPos()), Rotations.getPitch(mc.player.getBlockPos()), 50, this::burrow);
            else burrow();

            toggle();
        }
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        if (instant.get() && !shouldBurrow) {
            if (event.action == KeyAction.Press && mc.options.jumpKey.matchesKey(event.key, 0)) {
                shouldBurrow = true;
            }
            blockPos.set(mc.player.getBlockPos());
        }
    }

    private void burrow() {
        if (center.get()) PlayerUtils.centerPlayer();

        if (instant.get()) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.4, mc.player.getZ(), false, mc.player.horizontalCollision));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.75, mc.player.getZ(), false, mc.player.horizontalCollision));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.01, mc.player.getZ(), false, mc.player.horizontalCollision));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.15, mc.player.getZ(), false, mc.player.horizontalCollision));
        }


        FindItemResult block = getItem();

        if (!(mc.player.getInventory().getStack(block.slot()).getItem() instanceof BlockItem)) return;
        InvUtils.swap(block.slot(), true);

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Utils.vec3d(blockPos), Direction.UP, blockPos, false));
        mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        InvUtils.swapBack();

        if (instant.get()) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + rubberbandHeight.get(), mc.player.getZ(), false, mc.player.horizontalCollision));
        } else {
            mc.player.updatePosition(mc.player.getX(), mc.player.getY() + rubberbandHeight.get(), mc.player.getZ());
        }
    }

    private FindItemResult getItem() {
        return switch (block.get()) {
            case EChest -> InvUtils.findInHotbar(Items.ENDER_CHEST);
            case Anvil -> InvUtils.findInHotbar(itemStack -> net.minecraft.block.Block.getBlockFromItem(itemStack.getItem()) instanceof AnvilBlock);
            case Held -> new FindItemResult(mc.player.getInventory().getSelectedSlot(), mc.player.getMainHandStack().getCount());
            default -> InvUtils.findInHotbar(Items.OBSIDIAN, Items.CRYING_OBSIDIAN);
        };
    }

    private boolean checkHead() {
        BlockState blockState1 = mc.world.getBlockState(blockPos.set(mc.player.getX() + .3, mc.player.getY() + 2.3, mc.player.getZ() + .3));
        BlockState blockState2 = mc.world.getBlockState(blockPos.set(mc.player.getX() + .3, mc.player.getY() + 2.3, mc.player.getZ() - .3));
        BlockState blockState3 = mc.world.getBlockState(blockPos.set(mc.player.getX() - .3, mc.player.getY() + 2.3, mc.player.getZ() - .3));
        BlockState blockState4 = mc.world.getBlockState(blockPos.set(mc.player.getX() - .3, mc.player.getY() + 2.3, mc.player.getZ() + .3));
        boolean air1 = blockState1.isReplaceable();
        boolean air2 = blockState2.isReplaceable();
        boolean air3 = blockState3.isReplaceable();
        boolean air4 = blockState4.isReplaceable();
        return air1 && air2 && air3 && air4;
    }

    public enum Block {
        EChest,
        Obsidian,
        Anvil,
        Held
    }
}
