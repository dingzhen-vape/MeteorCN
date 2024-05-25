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
import net.minecraft.entity.attribute.EntityAttributes;

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

    public double blockReach() {
        if (!isActive()) return mc.player.getAttributeValue(EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE);
        return blockReach.get().floatValue();
    }

    public double entityReach() {
        if (!isActive()) return mc.player.getAttributeValue(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE);
        return entityReach.get().floatValue();
    }
}
