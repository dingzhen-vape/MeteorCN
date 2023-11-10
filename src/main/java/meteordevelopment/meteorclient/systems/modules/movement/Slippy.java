/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.block.Block;

import java.util.List;

public class Slippy extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Double> friction = sgGeneral.add(new DoubleSetting.Builder()
        .name("摩擦")
        .description("基础摩擦水平。")
        .range(0.01, 1.10)
        .sliderRange(0.01, 1.10)
        .defaultValue(1)
        .build()
    );

    public final Setting<ListMode> listMode = sgGeneral.add(new EnumSetting.Builder<ListMode>()
        .name("列表模式")
        .description("选择方块的方式。")
        .defaultValue(ListMode.Blacklist)
        .build()
    );

    public final Setting<List<Block>> ignoredBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("忽略的方块")
        .description("决定哪些方块不会滑动")
        .visible(() -> listMode.get() == ListMode.Blacklist)
        .build()
    );

    public final Setting<List<Block>> allowedBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("允许的方块")
        .description("决定哪些方块会滑动")
        .visible(() -> listMode.get() == ListMode.Whitelist)
        .build()
    );

    public Slippy() {
        super(Categories.Movement, "滑溜溜", "改变方块的基础摩擦水平。");
    }

    public enum ListMode {
        Whitelist,
        Blacklist
    }
}
