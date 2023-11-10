/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StatusEffectListSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.entity.effect.StatusEffect;

import java.util.List;

import static net.minecraft.entity.effect.StatusEffects.*;

public class PotionSaver extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<StatusEffect>> effects = sgGeneral.add(new StatusEffectListSetting.Builder()
        .name("效果")
        .description("要保留的效果。")
        .defaultValue(
            STRENGTH,
            ABSORPTION,
            RESISTANCE,
            FIRE_RESISTANCE,
            SPEED,
            HASTE,
            REGENERATION,
            WATER_BREATHING,
            SATURATION,
            LUCK,
            SLOW_FALLING,
            DOLPHINS_GRACE,
            CONDUIT_POWER,
            HERO_OF_THE_VILLAGE
        )
        .build()
    );

    public final Setting<Boolean> onlyWhenStationary = sgGeneral.add(new BoolSetting.Builder()
        .name("仅静止时")
        .description("只有在你不动时才冻结效果。")
        .defaultValue(false)
        .build()
    );

    public PotionSaver() {
        super(Categories.Player, "药水节省", "当你站着不动时停止药水效果的计时。");
    }

    public boolean shouldFreeze(StatusEffect effect) {
        return isActive() && (!onlyWhenStationary.get() || !PlayerUtils.isMoving()) && !mc.player.getStatusEffects().isEmpty() && effects.get().contains(effect);
    }
}
