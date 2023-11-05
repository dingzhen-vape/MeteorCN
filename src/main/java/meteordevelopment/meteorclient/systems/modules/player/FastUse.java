/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;

public class FastUse extends Module {
    public enum Mode {
        All,
        Some
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("哪些项目可以快速使用。")
        .defaultValue(Mode.All)
        .build()
    );

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("哪些项目应该在中快速放置工作")
        .visible(() -> mode.get() == Mode.Some)
        .build()
    );

    private final Setting<Boolean> blocks = sgGeneral.add(new BoolSetting.Builder()
        .name("blocks")
        .description("如果模式为开则快速放置块")
        .visible(() -> mode.get() == Mode.Some)
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("冷却")
        .description("快速使用冷却时间。")
        .defaultValue(0)
        .min(0)
        .sliderMax(4)
        .build()
    );

    public FastUse() {
        super(Categories.Player, "快速使用", "允许你以非常高的速度使用物品。");
    }

    public int getItemUseCooldown(ItemStack itemStack) {
        if (mode.get() == Mode.All || shouldWorkSome(itemStack)) {
            return cooldown.get();
        }
        return 4; //default cooldown
    }

    private boolean shouldWorkSome(ItemStack itemStack) {
        return (blocks.get() && itemStack.getItem() instanceof BlockItem) || items.get().contains(itemStack.getItem());
    }
}
