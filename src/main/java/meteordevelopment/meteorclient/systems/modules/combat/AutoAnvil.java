/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ButtonBlock;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

public class AutoAnvil extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // General

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("目标范围")
        .description("玩家被目标的半径。")
        .defaultValue(4)
        .min(0)
        .sliderMax(5)
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("目标优先级")
        .description("如何选择要目标的玩家。")
        .defaultValue(SortPriority.LowestHealth)
        .build()
    );

    private final Setting<Integer> height = sgGeneral.add(new IntSetting.Builder()
        .name("高度")
        .description("放置铁砧的高度。")
        .defaultValue(2)
        .range(0, 5)
        .sliderMax(5)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("延迟")
        .description("铁砧放置之间的延迟。")
        .defaultValue(10)
        .min(0)
        .sliderMax(50)
        .build()
    );

    private final Setting<Boolean> placeButton = sgGeneral.add(new BoolSetting.Builder()
        .name("放在脚下")
        .description("自动在目标的脚下放置一个按钮或压力板来打破铁砧。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> multiPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("多重放置")
        .description("一次放置多个铁砧。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> toggleOnBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("在破坏时切换")
        .description("当目标的头盔槽为空时切换。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("旋转")
        .description("自动旋转到铁砧/压力板/按钮放置的位置。")
        .defaultValue(true)
        .build()
    );

    private PlayerEntity target;
    private int timer;

    public AutoAnvil() {
        super(Categories.Combat, "自动铁砧", "自动在玩家头上放置铁砧来破坏头盔。");
    }

    @Override
    public void onActivate() {
        timer = 0;
        target = null;
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen instanceof AnvilScreen) event.cancel();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Head check
        if (toggleOnBreak.get() && target != null && target.getInventory().getArmorStack(3).isEmpty()) {
            error("目标头盔槽为空... 禁用。");
            toggle();
            return;
        }

        // Check distance + alive
        if (TargetUtils.isBadTarget(target, range.get())) {
            target = TargetUtils.getPlayerTarget(range.get(), priority.get());
            if (TargetUtils.isBadTarget(target, range.get())) return;
        }

        if (placeButton.get()) {
            FindItemResult floorBlock = InvUtils.findInHotbar(itemStack -> Block.getBlockFromItem(itemStack.getItem()) instanceof AbstractPressurePlateBlock || Block.getBlockFromItem(itemStack.getItem()) instanceof ButtonBlock);
            BlockUtils.place(target.getBlockPos(), floorBlock, rotate.get(), 0, false);
        }

        if (timer >= delay.get()) {
            timer = 0;

            FindItemResult anvil = InvUtils.findInHotbar(itemStack -> Block.getBlockFromItem(itemStack.getItem()) instanceof AnvilBlock);
            if (!anvil.found()) return;

            for (int i = height.get(); i > 1; i--) {
                BlockPos blockPos = target.getBlockPos().up().add(0, i, 0);

                for (int j = 0; j < i; j++) {
                    if (!mc.world.getBlockState(target.getBlockPos().up(j + 1)).isReplaceable()) {
                        break;
                    }
                }

                if (BlockUtils.place(blockPos, anvil, rotate.get(), 0) && !multiPlace.get()) break;
            }
        } else {
            timer++;
        }
    }

    @Override
    public String getInfoString() {
        return EntityUtils.getName(target);
    }
}
