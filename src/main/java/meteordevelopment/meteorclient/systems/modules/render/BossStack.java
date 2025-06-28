/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.RenderBossBarEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public class BossStack extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> stack = sgGeneral.add(new BoolSetting.Builder()
        .name("timer")
        .description("堆叠boss条并在文本中添加一个计数器。")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> hideName = sgGeneral.add(new BoolSetting.Builder()
        .name("隐藏名字")
        .description("隐藏boss条的名字。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> spacing = sgGeneral.add(new DoubleSetting.Builder()
        .name("bar-spacing")
        .description("每个boss条之间的间距减少。")
        .defaultValue(10)
        .min(0)
        .build()
    );

    public static final Map<ClientBossBar, Integer> barMap = new WeakHashMap<>();

    public BossStack() {
        super(Categories.Render, "更好的boss条", "堆叠boss条,使你的HUD更清爽。");
    }

    @EventHandler
    private void onFetchText(RenderBossBarEvent.BossText event) {
        if (hideName.get()) {
            event.name = Text.empty();
            return;
        } else if (barMap.isEmpty() || !stack.get()) return;
        ClientBossBar bar = event.bossBar;
        Integer integer = barMap.get(bar);
        barMap.remove(bar);
        if (integer != null && !hideName.get()) event.name = event.name.copy().append(" x" + integer);
    }

    @EventHandler
    private void onSpaceBars(RenderBossBarEvent.BossSpacing event) {
        event.spacing = spacing.get().intValue();
    }

    @EventHandler
    private void onGetBars(RenderBossBarEvent.BossIterator event) {
        if (stack.get()) {
            HashMap<String, ClientBossBar> chosenBarMap = new HashMap<>();
            event.iterator.forEachRemaining(bar -> {
                String name = bar.getName().getString();
                if (chosenBarMap.containsKey(name)) {
                    barMap.compute(chosenBarMap.get(name), (clientBossBar, integer) -> (integer == null) ? 2 : integer + 1);
                } else {
                    chosenBarMap.put(name, bar);
                }
            });
            event.iterator = chosenBarMap.values().iterator();
        }
    }
}
