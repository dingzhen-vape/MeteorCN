/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BedBlock;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class AntiBed extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> placeStringTop = sgGeneral.add(new BoolSetting.Builder()
        .name("放置绳索-顶部")
        .description("在你上方放置绳索。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> placeStringMiddle = sgGeneral.add(new BoolSetting.Builder()
        .name("放置绳索-中部")
        .description("在你的上部碰撞箱放置绳索。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> placeStringBottom = sgGeneral.add(new BoolSetting.Builder()
        .name("放置绳索-底部")
        .description("在你的脚下放置绳索。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onlyInHole = sgGeneral.add(new BoolSetting.Builder()
        .name("只在洞里")
        .description("只有当你站在一个洞里时才起作用。")
        .defaultValue(true)
        .build()
    );

    private boolean breaking;

    public AntiBed() {
        super(Categories.Combat, "反床", "放置绳索来防止在你身上放置床。");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (onlyInHole.get() && !PlayerUtils.isInHole(true)) return;

        // Checking for and maybe breaking bed
        BlockPos head = mc.player.getBlockPos().up();

        if (mc.world.getBlockState(head).getBlock() instanceof BedBlock && !breaking) {
            Rotations.rotate(Rotations.getYaw(head), Rotations.getPitch(head), 50, () -> sendMinePackets(head));
            breaking = true;
        } else if (breaking) {
            Rotations.rotate(Rotations.getYaw(head), Rotations.getPitch(head), 50, () -> sendStopPackets(head));
            breaking = false;
        }

        // String placement
        if (placeStringTop.get()) place(mc.player.getBlockPos().up(2));
        if (placeStringMiddle.get()) place(mc.player.getBlockPos().up(1));
        if (placeStringBottom.get()) place(mc.player.getBlockPos());
    }

    private void place(BlockPos blockPos) {
        if (mc.world.getBlockState(blockPos).getBlock().asItem() != Items.STRING) {
            BlockUtils.place(blockPos, InvUtils.findInHotbar(Items.STRING), 50, false);
        }
    }

    private void sendMinePackets(BlockPos blockPos) {
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, blockPos, Direction.UP));
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, Direction.UP));
    }

    private void sendStopPackets(BlockPos blockPos) {
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, blockPos, Direction.UP));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }
}
