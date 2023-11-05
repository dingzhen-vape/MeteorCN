/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.ArmRenderEvent;
import meteordevelopment.meteorclient.events.render.HeldItemRendererEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3d;

public class HandView extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMainHand = settings.createGroup("主手");
    private final SettingGroup sgOffHand = settings.createGroup("副手");
    private final SettingGroup sgArm = settings.createGroup("手臂");

    // General

    private final Setting<Boolean> followRotations = sgGeneral.add(new BoolSetting.Builder()
        .name("服务器旋转")
        .description("让你的手跟随服务器端旋转。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> oldAnimations = sgGeneral.add(new BoolSetting.Builder()
        .name("旧动画")
        .description("将点击动画更改为1.8")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> showSwapping = sgGeneral.add(new BoolSetting.Builder()
        .name("显示-swapping")
        .description("是否显示物品交换动画")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableFoodAnimation = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-eating-animation")
        .description("禁用吃动画。如果它超出屏幕,则可能是理想的。")
        .defaultValue(false)
        .build()
    );

    public final Setting<SwingMode> swingMode = sgGeneral.add(new EnumSetting.Builder<SwingMode>()
        .name("swing-mode")
        .description("修改你的客户端和服务器的手部摆动。")
        .defaultValue(SwingMode.None)
        .build()
    );

    public final Setting<Integer> swingSpeed = sgGeneral.add(new IntSetting.Builder()
        .name("swing-speed")
        .description("你的手部摆动速度。")
        .defaultValue(6)
        .range(0, 20)
        .sliderMax(20)
        .build()
    );

    public final Setting<Double> mainSwing = sgGeneral.add(new DoubleSetting.Builder()
        .name("main-hand-progress")
        .description("你的主手的挥杆进度。")
        .defaultValue(0)
        .range(0, 1)
        .sliderMax(1)
        .build()
    );

    public final Setting<Double> offSwing = sgGeneral.add(new DoubleSetting.Builder()
        .name("副手进度")
        .description("你的副手的挥杆进度。")
        .defaultValue(0)
        .range(0, 1)
        .sliderMax(1)
        .build()
    );

    // Main Hand

    private final Setting<Vector3d> scaleMain = sgMainHand.add(new Vector3dSetting.Builder()
        .name("缩放")
        .description("你的主手的挥杆进度。")
        .defaultValue(1, 1, 1)
        .sliderMax(5)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Vector3d> posMain = sgMainHand.add(new Vector3dSetting.Builder()
        .name("位置")
        .description("你的主手的位置。")
        .defaultValue(0, 0, 0)
        .sliderRange(-3, 3)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Vector3d> rotMain = sgMainHand.add(new Vector3dSetting.Builder()
        .name("旋转")
        .description("你的主手的旋转。")
        .defaultValue(0, 0, 0)
        .sliderRange(-180, 180)
        .decimalPlaces(0)
        .build()
    );

    // Offhand

    private final Setting<Vector3d> scaleOff = sgOffHand.add(new Vector3dSetting.Builder()
        .name("缩放")
        .description("你的副手的比例。")
        .defaultValue(1, 1, 1)
        .sliderMax(5)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Vector3d> posOff = sgOffHand.add(new Vector3dSetting.Builder()
        .name("位置")
        .description("你的副手的位置。")
        .defaultValue(0, 0, 0)
        .sliderRange(-3, 3)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Vector3d> rotOff = sgOffHand.add(new Vector3dSetting.Builder()
        .name("旋转")
        .description("副手的旋转。")
        .defaultValue(0, 0, 0)
        .sliderRange(-180, 180)
        .decimalPlaces(0)
        .build()
    );

    // Arm

    private final Setting<Vector3d> scaleArm = sgArm.add(new Vector3dSetting.Builder()
        .name("缩放")
        .defaultValue(1, 1, 1)
        .sliderMax(5)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Vector3d> posArm = sgArm.add(new Vector3dSetting.Builder()
        .name("位置")
        .defaultValue(0, 0, 0)
        .sliderRange(-3, 3)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Vector3d> rotArm = sgArm.add(new Vector3dSetting.Builder()
        .name("旋转")
        .defaultValue(0, 0, 0)
        .sliderRange(-180, 180)
        .decimalPlaces(0)
        .build()
    );

    public HandView() {
        super(Categories.Render, "手视图", "改变物品在你手中的渲染方式。");
    }

    @EventHandler
    private void onHeldItemRender(HeldItemRendererEvent event) {
        if (Rotations.rotating && followRotations.get()) {
            applyServerRotations(event.matrix);
        }

        if (event.hand == Hand.MAIN_HAND) {
            rotate(event.matrix, rotMain.get());
            scale(event.matrix, scaleMain.get());
            translate(event.matrix, posMain.get());
        }
        else {
            rotate(event.matrix, rotOff.get());
            scale(event.matrix, scaleOff.get());
            translate(event.matrix, posOff.get());
        }
    }

    @EventHandler
    private void onRenderArm(ArmRenderEvent event) {
        rotate(event.matrix, rotArm.get());
        scale(event.matrix, scaleArm.get());
        translate(event.matrix, posArm.get());
    }

    private void rotate(MatrixStack matrix, Vector3d rotation) {
        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) rotation.x));
        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) rotation.y));
        matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rotation.z));
    }

    private void scale(MatrixStack matrix, Vector3d scale) {
        matrix.scale((float) scale.x, (float) scale.y, (float) scale.z);
    }

    private void translate(MatrixStack matrix, Vector3d translation) {
        matrix.translate((float) translation.x, (float) translation.y, (float) translation.z);
    }

    private void applyServerRotations(MatrixStack matrix) {
        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.player.getPitch() - Rotations.serverPitch));
        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(mc.player.getYaw() - Rotations.serverYaw));
    }

    public boolean oldAnimations() {
        return isActive() && oldAnimations.get();
    }

    public boolean showSwapping() {
        return isActive() && showSwapping.get();
    }

    public boolean disableFoodAnimation() {
        return isActive() && disableFoodAnimation.get();
    }

    public enum SwingMode {
        Offhand,
        Mainhand,
        None
    }
}
