/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.tag.ItemTags;

import java.util.Set;

public class NoMiningTrace extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("黑名单实体")
        .description("你将正常互动的实体。")
        .defaultValue()
        .build()
    );

    private final Setting<Boolean> onlyWhenHoldingPickaxe = sgGeneral.add(new BoolSetting.Builder()
        .name("仅持有镐时")
        .description("是否仅在持有镐时工作。")
        .defaultValue(true)
        .build()
    );

    public NoMiningTrace() {
        super(Categories.Player, "不挖掘痕迹", "允许你通过实体挖掘方块。");
    }

    public boolean canWork(Entity entity) {
        if (!isActive()) return false;

        return (!onlyWhenHoldingPickaxe.get() || mc.player.getMainHandStack().isIn(ItemTags.PICKAXES) || mc.player.getOffHandStack().isIn(ItemTags.PICKAXES)) &&
            (entity == null || !entities.get().contains(entity.getType()));
    }
}
