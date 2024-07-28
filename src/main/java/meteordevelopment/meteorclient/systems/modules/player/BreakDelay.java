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
        .name("冷却")
        .description("破坏方块的冷却时间，以刻为单位。")
        .defaultValue(0)
        .min(0)
        .sliderMax(5)
        .build()
    );

    public final Setting<Boolean> noInstaBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("无瞬间破坏")
        .description("防止你瞬间破坏方块。")
        .defaultValue(false)
        .build()
    );

    public BreakDelay() {
        super(Categories.Player, "破坏延迟", "改变破坏方块之间的延迟。");
    }

    @EventHandler()
    private void onBlockBreakingCooldown(BlockBreakingCooldownEvent event) {
        event.cooldown = cooldown.get();
    }
}
