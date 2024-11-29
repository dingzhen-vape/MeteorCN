/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;

public class Reach extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> blockReach = sgGeneral.add(new DoubleSetting.Builder()
        .name("额外方块触及")
        .description("增加到你方块触及的距离。")
        .sliderMax(1)
        .build()
    );

    private final Setting<Double> entityReach = sgGeneral.add(new DoubleSetting.Builder()
        .name("额外实体触及")
        .description("增加到你实体触及的距离。")
        .sliderMax(1)
        .build()
    );

    public Reach() {
        super(Categories.Player, "长臂猿", "赋予你超长的手臂。");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return theme.label("注意：在原版服务器上，你最多可以为特定操作（如与方块实体（箱子、炉子等）或车辆互动）增加4个方块的额外触及距离 - " +
            "与方块实体（箱子、炉子等）或车辆互动。此功能在Paper服务器上无法使用。", Utils.getWindowWidth() / 3.0);
    }

    public double blockReach() {
        return isActive() ? blockReach.get() : 0;
    }

    public double entityReach() {
        return isActive() ? entityReach.get() : 0;
    }
}
