/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

public class Multitask extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> attackingEntities = sgGeneral.add(new BoolSetting.Builder()
        .name("攻击实体")
        .description("允许在使用物品时攻击实体。")
        .defaultValue(true)
        .build()
    );

    public Multitask() {
        super(Categories.Player, "多任务", "允许同时使用物品和攻击。");
    }

    public boolean attackingEntities() {
        return isActive() && attackingEntities.get();
    }
}
