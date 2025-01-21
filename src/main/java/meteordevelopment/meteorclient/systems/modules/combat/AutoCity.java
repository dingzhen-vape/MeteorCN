/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class AutoCity extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染");


    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("目标范围")
        .description("玩家被目标的半径。")
        .defaultValue(5.5)
        .min(0)
        .sliderMax(7)
        .build()
    );

    private final Setting<Double> breakRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("破坏范围")
        .description("一个方块必须多靠近你才能被考虑。")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<SwitchMode> switchMode = sgGeneral.add(new EnumSetting.Builder<SwitchMode>()
        .name("切换模式")
        .description("如何切换到镐。")
        .defaultValue(SwitchMode.Normal)
        .build()
    );

    private final Setting<Boolean> support = sgGeneral.add(new BoolSetting.Builder()
        .name("支持")
        .description("如果挖掘方块下面没有方块，它会在挖掘之前放置一个。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("放置范围")
        .description("尝试放置方块的距离。")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .visible(support::get)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("旋转")
        .description("自动旋转到挖掘方块的方向。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatInfo = sgGeneral.add(new BoolSetting.Builder()
        .name("聊天信息")
        .description("模块是否应该在聊天中发送消息。")
        .defaultValue(true)
        .build()
    );

    // Render

    private final Setting<Boolean> swingHand = sgRender.add(new BoolSetting.Builder()
        .name("挥动手")
        .description("是否渲染你的手挥动。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> renderBlock = sgRender.add(new BoolSetting.Builder()
        .name("渲染方块")
        .description("是否渲染被破坏的方块。")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("形状的渲染方式。")
        .defaultValue(ShapeMode.Both)
        .visible(renderBlock::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("侧面颜色")
        .description("渲染的侧面颜色。")
        .defaultValue(new SettingColor(225, 0, 0, 75))
        .visible(() -> renderBlock.get() && shapeMode.get().sides())
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("线条颜色")
        .description("渲染的线条颜色。")
        .defaultValue(new SettingColor(225, 0, 0, 255))
        .visible(() -> renderBlock.get() && shapeMode.get().lines())
        .build()
    );

    private PlayerEntity target;
    private BlockPos targetPos;
    private FindItemResult pick;
    private float progress;

    public AutoCity() {
        super(Categories.Combat, "自动挖掘", "自动挖掘某人脚边的方块。");
    }

    @Override
    public void onActivate() {
        target = TargetUtils.getPlayerTarget(targetRange.get(), SortPriority.ClosestAngle);
        if (TargetUtils.isBadTarget(target, targetRange.get())) {
            if (chatInfo.get()) error("找不到目标，禁用。");
            toggle();
            return;
        }

        targetPos = EntityUtils.getCityBlock(target);
        if (targetPos == null || PlayerUtils.squaredDistanceTo(targetPos) > Math.pow(breakRange.get(), 2)) {
            if (chatInfo.get()) error("找不到好的方块，禁用。");
            toggle();
            return;
        }

        if (support.get()) {
            BlockPos supportPos = targetPos.down();
            if (!(PlayerUtils.squaredDistanceTo(supportPos) > Math.pow(placeRange.get(), 2))) {
                BlockUtils.place(supportPos, InvUtils.findInHotbar(Items.OBSIDIAN), rotate.get(), 0, true);
            }
        }

        pick = InvUtils.find(itemStack -> itemStack.getItem() == Items.DIAMOND_PICKAXE || itemStack.getItem() == Items.NETHERITE_PICKAXE);
        if (!pick.isHotbar()) {
            error("找不到镐... 禁用。");
            toggle();
            return;
        }

        progress = 0.0f;
        mine(false);
    }

    @Override
    public void onDeactivate() {
        target = null;
        targetPos = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (TargetUtils.isBadTarget(target, targetRange.get())) {
            toggle();
            return;
        }

        if (PlayerUtils.squaredDistanceTo(targetPos) > Math.pow(breakRange.get(), 2)) {
            if (chatInfo.get()) error("找不到目标，禁用。");
            toggle();
            return;
        }

        if (progress < 1.0f) {
            pick = InvUtils.find(itemStack -> itemStack.getItem() == Items.DIAMOND_PICKAXE || itemStack.getItem() == Items.NETHERITE_PICKAXE);
            if (!pick.isHotbar()) {
                error("找不到镐... 禁用。");
                toggle();
                return;
            }
            progress += BlockUtils.getBreakDelta(pick.slot(), mc.world.getBlockState(targetPos));
            if (progress < 1.0f) return;
        }

        mine(true);
        toggle();
    }

    public void mine(boolean done) {
        InvUtils.swap(pick.slot(), switchMode.get() == SwitchMode.Silent);
        if (rotate.get()) Rotations.rotate(Rotations.getYaw(targetPos), Rotations.getPitch(targetPos));

        Direction direction = BlockUtils.getDirection(targetPos);
        if (!done) mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, targetPos, direction));
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, targetPos, direction));

        if (swingHand.get()) mc.player.swingHand(Hand.MAIN_HAND);
        else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        if (switchMode.get() == SwitchMode.Silent) InvUtils.swapBack();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (targetPos == null || !renderBlock.get()) return;
        event.renderer.box(targetPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    @Override
    public String getInfoString() {
        return EntityUtils.getName(target);
    }

    public enum SwitchMode {
        Normal,
        Silent
    }
}
