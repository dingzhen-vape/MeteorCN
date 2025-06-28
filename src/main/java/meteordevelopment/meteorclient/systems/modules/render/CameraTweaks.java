/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.game.ChangePerspectiveEvent;
import meteordevelopment.meteorclient.events.meteor.MouseScrollEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.Perspective;
import org.lwjgl.glfw.GLFW;

public class CameraTweaks extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgScrolling = settings.createGroup("滚动");

    // General

    private final Setting<Boolean> clip = sgGeneral.add(new BoolSetting.Builder()
        .name("剪辑")
        .description("允许相机穿透方块。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> cameraDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("相机距离")
        .description("第三人称相机距离玩家的距离。")
        .defaultValue(4)
        .min(0)
        .onChanged(value -> distance = value)
        .build()
    );

    // Scrolling

    private final Setting<Boolean> scrollingEnabled = sgScrolling.add(new BoolSetting.Builder()
        .name("滚动启用")
        .description("允许你滚动来改变相机距离。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Keybind> scrollKeybind = sgScrolling.add(new KeybindSetting.Builder()
        .name("滚动灵敏度")
        .description("改变相机距离时的滚动灵敏度。0为禁用。")
        .visible(scrollingEnabled::get)
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_LEFT_ALT))
        .build()
    );

    private final Setting<Double> scrollSensitivity = sgScrolling.add(new DoubleSetting.Builder()
        .name("滚动按键绑定")
        .description("需要按下一个按键才能使滚动生效。")
        .visible(scrollingEnabled::get)
        .defaultValue(1)
        .min(0.01)
        .build()
    );

    public double distance;

    public CameraTweaks() {
        super(Categories.Render, "相机微调", "允许修改第三人称相机。");
    }

    @Override
    public void onActivate() {
        distance = cameraDistance.get();
    }

    @EventHandler
    private void onPerspectiveChanged(ChangePerspectiveEvent event) {
        distance = cameraDistance.get();
    }

    @EventHandler
    private void onMouseScroll(MouseScrollEvent event) {
        if (mc.options.getPerspective() == Perspective.FIRST_PERSON || mc.currentScreen != null || !scrollingEnabled.get() || (scrollKeybind.get().isSet() && !scrollKeybind.get().isPressed())) return;

        if (scrollSensitivity.get() > 0) {
            distance -= event.value * 0.25 * (scrollSensitivity.get() * distance);

            event.cancel();
        }
    }

    public boolean clip() {
        return isActive() && clip.get();
    }
}
