/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseScrollEvent;
import meteordevelopment.meteorclient.events.render.GetFovEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class Zoom extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();


    private final Setting<Double> zoom = sgGeneral.add(new DoubleSetting.Builder()
        .name("缩放")
        .description("缩放的倍数。")
        .defaultValue(6)
        .min(1)
        .build()
    );

    private final Setting<Double> scrollSensitivity = sgGeneral.add(new DoubleSetting.Builder()
        .name("滚动灵敏度")
        .description("允许您使用滚动轮来改变缩放值。0 表示禁用。")
        .defaultValue(1)
        .min(0)
        .build()
    );

    private final Setting<Boolean> smooth = sgGeneral.add(new BoolSetting.Builder()
        .name("平滑过渡")
        .description("平滑的过渡效果。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cinematic = sgGeneral.add(new BoolSetting.Builder()
        .name("电影模式")
        .description("启用电影模式摄像机。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> hideHud = sgGeneral.add(new BoolSetting.Builder()
        .name("隐藏HUD")
        .description("是否隐藏Minecraft的HUD。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> renderHands = sgGeneral.add(new BoolSetting.Builder()
        .name("显示手")
        .description("是否显示您的手。")
        .defaultValue(false)
        .visible(() -> !hideHud.get())
        .build()
    );

    private boolean enabled;
    private boolean preCinematic;
    private double preMouseSensitivity;
    private double value;
    private double lastFov;
    private double time;

    private boolean hudManualToggled;

    public Zoom() {
        super(Categories.Render, "缩放 ", "缩放你的视野.");
        autoSubscribe = false;
    }

    @Override
    public void onActivate() {
        if (!enabled) {
            preCinematic = mc.options.smoothCameraEnabled;
            preMouseSensitivity = mc.options.getMouseSensitivity().getValue();
            value = zoom.get();
            lastFov = mc.options.getFov().getValue();
            time = 0.001;

            MeteorClient.EVENT_BUS.subscribe(this);
            enabled = true;
        }

        if (hideHud.get() && !mc.options.hudHidden) {
            hudManualToggled = false;
            mc.options.hudHidden = true;
        }
    }

    @Override
    public void onDeactivate() {
        if (hideHud.get() && !hudManualToggled) {
            mc.options.hudHidden = false;
        }
    }

    @EventHandler
    public void onKeyPressed(KeyEvent event) {
        if (event.key != GLFW.GLFW_KEY_F1) return;
        hudManualToggled = true;
    }

    public void onStop() {
        mc.options.smoothCameraEnabled = preCinematic;
        mc.options.getMouseSensitivity().setValue(preMouseSensitivity);

        mc.worldRenderer.scheduleTerrainUpdate();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        mc.options.smoothCameraEnabled = cinematic.get();

        if (!cinematic.get()) {
            mc.options.getMouseSensitivity().setValue(preMouseSensitivity / Math.max(getScaling() * 0.5, 1));
        }

        if (time == 0) {
            MeteorClient.EVENT_BUS.unsubscribe(this);
            enabled = false;

            onStop();
        }
    }

    @EventHandler
    private void onMouseScroll(MouseScrollEvent event) {
        if (scrollSensitivity.get() > 0 && isActive()) {
            value += event.value * 0.25 * (scrollSensitivity.get() * value);
            if (value < 1) value = 1;

            event.cancel();
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!smooth.get()) {
            time = isActive() ? 1 : 0;
            return;
        }

        if (isActive()) time += event.frameTime * 5;
        else time -= event.frameTime * 5;

        time = MathHelper.clamp(time, 0, 1);
    }

    @EventHandler
    private void onGetFov(GetFovEvent event) {
        event.fov /= (float) getScaling();

        if (lastFov != event.fov) mc.worldRenderer.scheduleTerrainUpdate();
        lastFov = event.fov;
    }

    public double getScaling() {
        double delta = time < 0.5 ? 4 * time * time * time : 1 - Math.pow(-2 * time + 2, 3) / 2; // Ease in out cubic
        return MathHelper.lerp(delta, 1, value);
    }

    public boolean renderHands() {
        return !isActive() || renderHands.get();
    }
}
