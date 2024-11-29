/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class FreeLook extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgArrows = settings.createGroup("箭头");

    // General

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("模式")
        .description("要旋转的实体。")
        .defaultValue(Mode.Player)
        .build()
    );

    public final Setting<Boolean> togglePerspective = sgGeneral.add(new BoolSetting.Builder()
        .name("切换视角")
        .description("切换时改变你的视角。")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> sensitivity = sgGeneral.add(new DoubleSetting.Builder()
        .name("摄像机灵敏度")
        .description("摄像机模式下摄像机移动的速度。")
        .defaultValue(8)
        .min(0)
        .sliderMax(10)
        .build()
    );

    // Arrows

    public final Setting<Boolean> arrows = sgArrows.add(new BoolSetting.Builder()
        .name("箭头控制相反")
        .description("允许你用箭头键控制其他实体的旋转。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> arrowSpeed = sgArrows.add(new DoubleSetting.Builder()
        .name("箭头速度")
        .description("用箭头键旋转的速度。")
        .defaultValue(4)
        .min(0)
        .build()
    );

    public float cameraYaw;
    public float cameraPitch;

    private Perspective prePers;

    public FreeLook() {
        super(Categories.Render, "free-lock|自由视角", "在第三人称中允许更多的旋转选项。");
    }

    @Override
    public void onActivate() {
        cameraYaw = mc.player.getYaw();
        cameraPitch = mc.player.getPitch();
        prePers = mc.options.getPerspective();

        if (prePers != Perspective.THIRD_PERSON_BACK &&  togglePerspective.get()) mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
    }

    @Override
    public void onDeactivate() {
        if (mc.options.getPerspective() != prePers && togglePerspective.get()) mc.options.setPerspective(prePers);
    }

    public boolean playerMode() {
        return isActive() && mc.options.getPerspective() == Perspective.THIRD_PERSON_BACK && mode.get() == Mode.Player;
    }

    public boolean cameraMode() {
        return isActive() && mode.get() == Mode.Camera;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (arrows.get()) {
            for (int i = 0; i < (arrowSpeed.get() * 2); i++) {
                switch (mode.get()) {
                    case Player -> {
                        if (Input.isKeyPressed(GLFW.GLFW_KEY_LEFT)) cameraYaw -= 0.5;
                        if (Input.isKeyPressed(GLFW.GLFW_KEY_RIGHT)) cameraYaw += 0.5;
                        if (Input.isKeyPressed(GLFW.GLFW_KEY_UP)) cameraPitch -= 0.5;
                        if (Input.isKeyPressed(GLFW.GLFW_KEY_DOWN)) cameraPitch += 0.5;
                    }
                    case Camera -> {
                        float yaw = mc.player.getYaw();
                        float pitch = mc.player.getPitch();

                        if (Input.isKeyPressed(GLFW.GLFW_KEY_LEFT)) yaw -= 0.5;
                        if (Input.isKeyPressed(GLFW.GLFW_KEY_RIGHT)) yaw += 0.5;
                        if (Input.isKeyPressed(GLFW.GLFW_KEY_UP)) pitch -= 0.5;
                        if (Input.isKeyPressed(GLFW.GLFW_KEY_DOWN)) pitch += 0.5;

                        mc.player.setYaw(yaw);
                        mc.player.setPitch(pitch);
                    }
                }
            }
        }

        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch(), -90, 90));
        cameraPitch = MathHelper.clamp(cameraPitch, -90, 90);
    }

    public enum Mode {
        Player,
        Camera
    }
}
