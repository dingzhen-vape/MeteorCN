/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.mixin.CreativeInventoryScreenAccessor;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemGroups;
import net.minecraft.util.math.MathHelper;

import static org.lwjgl.glfw.GLFW.*;

public class GUIMove extends Module {
    public enum Screens {
        GUI,
        Inventory,
        Both
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Screens> screens = sgGeneral.add(new EnumSetting.Builder<Screens>()
        .name("界面")
        .description("可以在其中移动的界面.")
        .defaultValue(Screens.Inventory)
        .build()
    );

    public final Setting<Boolean> jump = sgGeneral.add(new BoolSetting.Builder()
        .name("jump")
        .description("在界面中允许你跳跃.")
        .defaultValue(true)
        .onChanged(aBoolean -> {
            if (isActive() && !aBoolean) mc.options.jumpKey.setPressed(false);
        })
        .build()
    );

    public final Setting<Boolean> sneak = sgGeneral.add(new BoolSetting.Builder()
        .name("潜行")
        .description("在界面中允许你潜行.")
        .defaultValue(true)
        .onChanged(aBoolean -> {
            if (isActive() && !aBoolean) mc.options.sneakKey.setPressed(false);
        })
        .build()
    );

    public final Setting<Boolean> sprint = sgGeneral.add(new BoolSetting.Builder()
        .name("冲刺")
        .description("在界面中允许你冲刺.")
        .defaultValue(true)
        .onChanged(aBoolean -> {
            if (isActive() && !aBoolean) mc.options.sprintKey.setPressed(false);
        })
        .build()
    );

    private final Setting<Boolean> arrowsRotate = sgGeneral.add(new BoolSetting.Builder()
        .name("箭头旋转")
        .description("在界面中允许你用箭头键旋转.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> rotateSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("旋转速度")
        .description("在界面中的旋转速度.")
        .defaultValue(4)
        .min(0)
        .build()
    );

    public GUIMove() {
        super(Categories.Movement, "界面移动", "在界面中允许你执行各种动作.");
    }

    @Override
    public void onDeactivate() {
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);

        if (jump.get()) mc.options.jumpKey.setPressed(false);
        if (sneak.get()) mc.options.sneakKey.setPressed(false);
        if (sprint.get()) mc.options.sprintKey.setPressed(false);
    }

    public boolean disableSpace() {
        return isActive() && jump.get() && mc.options.jumpKey.isDefault();
    }
    public boolean disableArrows() {
        return isActive() && arrowsRotate.get();
    }

    private void onInput(int key, KeyAction action, boolean mouse) {
        if (skip()) return;
        if (screens.get() == Screens.GUI && !(mc.currentScreen instanceof WidgetScreen)) return;
        if (screens.get() == Screens.Inventory && mc.currentScreen instanceof WidgetScreen) return;

        pass(mc.options.forwardKey, key, action, mouse);
        pass(mc.options.backKey, key, action, mouse);
        pass(mc.options.leftKey, key, action, mouse);
        pass(mc.options.rightKey, key, action, mouse);

        if (jump.get()) pass(mc.options.jumpKey, key, action, mouse);
        if (sneak.get()) pass(mc.options.sneakKey, key, action, mouse);
        if (sprint.get()) pass(mc.options.sprintKey, key, action, mouse);
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        onInput(event.key, event.action, false);
    }

    @EventHandler
    private void onButton(MouseButtonEvent event) {
        onInput(event.button, event.action, true);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (skip()) return;
        if (screens.get() == Screens.GUI && !(mc.currentScreen instanceof WidgetScreen)) return;
        if (screens.get() == Screens.Inventory && mc.currentScreen instanceof WidgetScreen) return;

        float rotationDelta = Math.min((float) (rotateSpeed.get() * event.frameTime * 20f), 100);

        Freecam freecam = Modules.get().get(Freecam.class);

        if (arrowsRotate.get()) {
            if (!freecam.isActive()) {
                float yaw = mc.player.getYaw();
                float pitch = mc.player.getPitch();

                if (Input.isKeyPressed(GLFW_KEY_LEFT)) yaw -= rotationDelta;
                if (Input.isKeyPressed(GLFW_KEY_RIGHT)) yaw += rotationDelta;
                if (Input.isKeyPressed(GLFW_KEY_UP)) pitch -= rotationDelta;
                if (Input.isKeyPressed(GLFW_KEY_DOWN)) pitch += rotationDelta;

                pitch = MathHelper.clamp(pitch, -90, 90);

                mc.player.setYaw(yaw);
                mc.player.setPitch(pitch);
            } else {
                double dy = 0, dx = 0;

                if (Input.isKeyPressed(GLFW_KEY_LEFT)) dy = -rotationDelta;
                if (Input.isKeyPressed(GLFW_KEY_RIGHT)) dy = rotationDelta;
                if (Input.isKeyPressed(GLFW_KEY_UP)) dx = -rotationDelta;
                if (Input.isKeyPressed(GLFW_KEY_DOWN)) dx = rotationDelta;

                freecam.changeLookDirection(dy, dx);
            }
        }
    }

    private void pass(KeyBinding bind, int key, KeyAction action, boolean mouse) {
        if (!mouse && !bind.matchesKey(key, 0)) return;
        if (mouse && !bind.matchesMouse(key)) return;
        if (action == KeyAction.Press) bind.setPressed(true);
        if (action == KeyAction.Release) bind.setPressed(false);
    }

    public boolean skip() {
        return mc.currentScreen == null || (mc.currentScreen instanceof CreativeInventoryScreen && CreativeInventoryScreenAccessor.getSelectedTab() == ItemGroups.getSearchGroup()) || mc.currentScreen instanceof ChatScreen || mc.currentScreen instanceof SignEditScreen || mc.currentScreen instanceof AnvilScreen || mc.currentScreen instanceof AbstractCommandBlockScreen || mc.currentScreen instanceof StructureBlockScreen;
    }
}
