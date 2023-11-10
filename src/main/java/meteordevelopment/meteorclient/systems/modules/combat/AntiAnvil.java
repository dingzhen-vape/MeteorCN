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
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

public class AntiAnvil extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("摇摆")
        .description("放置时客户端摇摆你的手。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("旋转")
        .description("放置时让你旋转。")
        .defaultValue(true)
        .build()
    );

    public AntiAnvil() {
        super(Categories.Combat, "反铁砧", "自动通过在你和铁砧之间放置来防止自动铁砧。");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        for (int i = 0; i <= mc.interactionManager.getReachDistance(); i++) {
            BlockPos pos = mc.player.getBlockPos().add(0, i + 3, 0);

            if (mc.world.getBlockState(pos).getBlock() == Blocks.ANVIL && mc.world.getBlockState(pos.down()).isAir()) {
                if (BlockUtils.place(pos.down(), InvUtils.findInHotbar(Items.OBSIDIAN), rotate.get(), 15, swing.get(), true))
                    break;
            }
        }
    }
}
