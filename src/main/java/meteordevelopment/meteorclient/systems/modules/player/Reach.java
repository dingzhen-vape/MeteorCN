/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

public class Reach extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> blockReach = sgGeneral.add(new DoubleSetting.Builder()
        .name("方块触及")
        .description("方块的触及修正。")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> entityReach = sgGeneral.add(new DoubleSetting.Builder()
        .name("实体触及")
        .description("实体的触及修正。")
        .defaultValue(3)
        .min(0)
        .sliderMax(6)
        .build()
    );

    public Reach() {
        super(Categories.Player, "触及", "给你超长的手臂。");
    }

    public float blockReach() {
        if (!isActive()) return mc.interactionManager.getCurrentGameMode().isCreative() ? 5.0F : 4.5F;
        return blockReach.get().floatValue();
    }

    public float entityReach() {
        if (!isActive()) return 3;
        return entityReach.get().floatValue();
    }
}
