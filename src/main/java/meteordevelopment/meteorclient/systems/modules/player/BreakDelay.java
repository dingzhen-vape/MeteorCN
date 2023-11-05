/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.entity.player.BlockBreakingCooldownEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class BreakDelay extends Module {
    SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("以刻度为单位的方块破坏冷却时间。")
        .defaultValue(0)
        .min(0)
        .sliderMax(5)
        .build()
    );

    public final Setting<Boolean> noInstaBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("no-insta-break")
        .description("防止您立即破坏方块。")
        .defaultValue(false)
        .build()
    );

    public BreakDelay() {
        super(Categories.Player, "break-delay", "更改破坏方块之间的延迟。");
    }

    @EventHandler()
    private void onBlockBreakingCooldown(BlockBreakingCooldownEvent event) {
        event.cooldown = cooldown.get();
    }
}
