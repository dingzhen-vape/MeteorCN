/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.StatusEffectInstanceAccessor;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;

import java.util.List;

import static net.minecraft.entity.effect.StatusEffects.*;

public class PotionSpoof extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Reference2IntMap<StatusEffect>> spoofPotions = sgGeneral.add(new StatusEffectAmplifierMapSetting.Builder()
        .name("伪装药水")
        .description("添加的药水。")
        .defaultValue(Utils.createStatusEffectMap())
        .build()
    );

    private final Setting<Boolean> clearEffects = sgGeneral.add(new BoolSetting.Builder()
        .name("清除效果")
        .description("在模块禁用时清除效果。")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<StatusEffect>> antiPotion = sgGeneral.add(new StatusEffectListSetting.Builder()
        .name("阻止药水")
        .description("要阻止的药水。")
        .defaultValue(
            LEVITATION.value(),
            JUMP_BOOST.value(),
            SLOW_FALLING.value(),
            DOLPHINS_GRACE.value()
        )
        .build()
    );

    public PotionSpoof() {
        super(Categories.Player, "药水伪装", "为你伪装药水状态。有些效果不起作用。");
    }

    @Override
    public void onDeactivate() {
        if (!clearEffects.get() || !Utils.canUpdate()) return;

        for (Reference2IntMap.Entry<StatusEffect> entry : spoofPotions.get().reference2IntEntrySet()) {
            if (entry.getIntValue() <= 0) continue;
            if (mc.player.hasStatusEffect(Registries.STATUS_EFFECT.getEntry(entry.getKey()))) mc.player.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(entry.getKey()));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        for (Reference2IntMap.Entry<StatusEffect> entry : spoofPotions.get().reference2IntEntrySet()) {
            int level = entry.getIntValue();
            if (level <= 0) continue;

            if (mc.player.hasStatusEffect(Registries.STATUS_EFFECT.getEntry(entry.getKey()))) {
                StatusEffectInstance instance = mc.player.getStatusEffect(Registries.STATUS_EFFECT.getEntry(entry.getKey()));
                ((StatusEffectInstanceAccessor) instance).setAmplifier(level - 1);
                if (instance.getDuration() < 20) ((StatusEffectInstanceAccessor) instance).setDuration(20);
            } else {
                mc.player.addStatusEffect(new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(entry.getKey()), 20, level - 1));
            }
        }
    }

    public boolean shouldBlock(StatusEffect effect) {
        return isActive() && antiPotion.get().contains(effect);
    }
}
