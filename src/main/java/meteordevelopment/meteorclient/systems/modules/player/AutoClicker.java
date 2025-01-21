/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;

public class AutoClicker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> inScreens = sgGeneral.add(new BoolSetting.Builder()
        .name("在屏幕打开时点击")
        .description("是否在屏幕打开时进行点击。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Mode> leftClickMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("左键点击模式")
        .description("左键点击的方法。")
        .defaultValue(Mode.Press)
        .build()
    );

    private final Setting<Integer> leftClickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("左键点击延迟")
        .description("左键点击之间的延迟时间（以tick为单位）。")
        .defaultValue(2)
        .min(0)
        .sliderMax(60)
        .visible(() -> leftClickMode.get() == Mode.Press)
        .build()
    );

    private final Setting<Mode> rightClickMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("右键点击模式")
        .description("右键点击的方法。")
        .defaultValue(Mode.Press)
        .build()
    );

    private final Setting<Integer> rightClickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("右键点击延迟")
        .description("右键点击之间的延迟时间（以tick为单位）。")
        .defaultValue(2)
        .min(0)
        .sliderMax(60)
        .visible(() -> rightClickMode.get() == Mode.Press)
        .build()
    );


    private int rightClickTimer, leftClickTimer;

    public AutoClicker() {
        super(Categories.Player, "自动点击者", "可以自动完成鼠标点击的操作");
    }

    @Override
    public void onActivate() {
        rightClickTimer = 0;
        leftClickTimer = 0;
        mc.options.attackKey.setPressed(false);
        mc.options.useKey.setPressed(false);
    }

    @Override
    public void onDeactivate() {
        mc.options.attackKey.setPressed(false);
        mc.options.useKey.setPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!inScreens.get() && mc.currentScreen != null) return;

        switch (leftClickMode.get()) {
            case Disabled -> {}
            case Hold -> mc.options.attackKey.setPressed(true);
            case Press -> {
                leftClickTimer++;
                if (leftClickTimer > leftClickDelay.get()) {
                    Utils.leftClick();
                    leftClickTimer = 0;
                }
            }
        }

        switch (rightClickMode.get()) {
            case Disabled -> {}
            case Hold -> mc.options.useKey.setPressed(true);
            case Press -> {
                rightClickTimer++;
                if (rightClickTimer > rightClickDelay.get()) {
                    Utils.rightClick();
                    rightClickTimer = 0;
                }
            }
        }
    }

    public enum Mode {
        Disabled,
        Hold,
        Press
    }
}
