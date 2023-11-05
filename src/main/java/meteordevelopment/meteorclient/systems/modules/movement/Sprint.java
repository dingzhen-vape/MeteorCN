/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class Sprint extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Mode {
        Strict,
        Rage
    }

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("speed-mode")
        .description("什么模式的冲刺。")
        .defaultValue(Mode.Strict)
        .build()
    );

    // Removed whenStationary as it was just Rage sprint


    public Sprint() {
        super(Categories.Movement, "冲刺", "自动冲刺。");
    }

    @Override
    public void onDeactivate() {
        mc.player.setSprinting(false);
    }

    private void sprint() {
        if (mc.player.getHungerManager().getFoodLevel() <= 6) return;
        mc.player.setSprinting(true);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        switch (mode.get()) {
            case Strict -> {
                if (mc.player.forwardSpeed > 0) {
                    sprint();
                }
            }
            case Rage -> sprint();
            }
        }
}
