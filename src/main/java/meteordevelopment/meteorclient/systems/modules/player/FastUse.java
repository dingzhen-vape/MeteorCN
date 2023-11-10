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
        .name("模式")
        .description("快速使用的物品。")
        .defaultValue(Mode.All)
        .build()
    );

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("物品")
        .description("在 \"Some\" 模式下，快速放置的物品。")
        .visible(() -> mode.get() == Mode.Some)
        .build()
    );

    private final Setting<Boolean> blocks = sgGeneral.add(new BoolSetting.Builder()
        .name("方块")
        .description("如果模式是 \"Some\" 模式下，快速放置的物品。")
        .visible(() -> mode.get() == Mode.Some)
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("冷却")
        .description("快速使用的冷却时间，以刻为单位。")
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
