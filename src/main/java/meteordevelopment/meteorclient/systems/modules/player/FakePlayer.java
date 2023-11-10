/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerManager;

public class FakePlayer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<String> name = sgGeneral.add(new StringSetting.Builder()
        .name("名字")
        .description("假玩家的名字。")
        .defaultValue("seasnail8169")
        .build()
    );

    public final Setting<Boolean> copyInv = sgGeneral.add(new BoolSetting.Builder()
        .name("复制物品栏")
        .description("复制你的物品栏到假玩家。")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> health = sgGeneral.add(new IntSetting.Builder()
        .name("生命值")
        .description("假玩家的默认生命值。")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    public FakePlayer() {
        super(Categories.Player, "假玩家", "生成一个客户端的假玩家用于测试用途。无需激活。");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();
        fillTable(theme, table);

        return table;
    }

    private void fillTable(GuiTheme theme, WTable table) {
        for (FakePlayerEntity fakePlayer : FakePlayerManager.getFakePlayers()) {
            table.add(theme.label(fakePlayer.getEntityName()));
            WMinus delete = table.add(theme.minus()).expandCellX().right().widget();
            delete.action = () -> {
                FakePlayerManager.remove(fakePlayer);
                table.clear();
                fillTable(theme, table);
            };
            table.row();
        }

        WButton spawn = table.add(theme.button("生成")).expandCellX().right().widget();
        spawn.action = () -> {
            FakePlayerManager.add(name.get(), health.get(), copyInv.get());
            table.clear();
            fillTable(theme, table);
        };

        WButton clear = table.add(theme.button("清除所有")).right().widget();
        clear.action = () -> {
            FakePlayerManager.clear();
            table.clear();
            fillTable(theme, table);
        };
    }

    @Override
    public String getInfoString() {
        return String.valueOf(FakePlayerManager.count());
    }
}
