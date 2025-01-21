/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.PickaxeItem;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class InstantRebreak extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("延迟")
        .description("破坏尝试之间的延迟.")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> pick = sgGeneral.add(new BoolSetting.Builder()
        .name("仅用镐")
        .description("仅在拿着镐时尝试开采方块。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("欺骗服务器")
        .description("服务器端面向正在开采的方块。")
        .defaultValue(true)
        .build()
    );

    // Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("渲染")
        .description("渲染正在破坏的方块的覆盖层。")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状渲染")
        .description("形状的渲染方式。")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("边缘颜色")
        .description("渲染方块边的颜色。")
        .defaultValue(new SettingColor(204, 0, 0, 10))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("线条颜色")
        .description("渲染方块线条的颜色。")
        .defaultValue(new SettingColor(204, 0, 0, 255))
        .build()
    );

    public final BlockPos.Mutable blockPos = new BlockPos.Mutable(0, Integer.MIN_VALUE, 0);
    private int ticks;
    private Direction direction;

    public InstantRebreak() {
        super(Categories.Player, "瞬时破坏", "在相同位置瞬间重新破坏方块.");
    }

    @Override
    public void onActivate() {
        ticks = 0;
        blockPos.set(0, -1, 0);
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        direction = event.direction;
        blockPos.set(event.blockPos);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (ticks >= tickDelay.get()) {
            ticks = 0;

            if (shouldMine()) {
                if (rotate.get()) Rotations.rotate(Rotations.getYaw(blockPos), Rotations.getPitch(blockPos), this::sendPacket);
                else sendPacket();

                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }
        } else {
            ticks++;
        }
    }

    public void sendPacket() {
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, direction == null ? Direction.UP : direction));
    }

    public boolean shouldMine() {
        if (mc.world.isOutOfHeightLimit(blockPos) || !BlockUtils.canBreak(blockPos)) return false;

        return !pick.get() || mc.player.getMainHandStack().getItem() instanceof PickaxeItem;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || !shouldMine()) return;

        event.renderer.box(blockPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }
}
