/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.utils.BetterBlockPos;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.glfw.GLFW;

public class Excavator extends Module {
    private final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRendering = settings.createGroup("渲染");

    // Keybindings
    private final Setting<Keybind> selectionKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("选择键")
        .description("绘制选择区域的键。")
        .defaultValue(Keybind.fromButton(GLFW.GLFW_MOUSE_BUTTON_RIGHT))
        .build()
    );

    // Logging
    private final Setting<Boolean> logSelection = sgGeneral.add(new BoolSetting.Builder()
        .name("记录选择")
        .description("将选择区域的坐标记录到聊天框。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> keepActive = sgGeneral.add(new BoolSetting.Builder()
        .name("保持激活")
        .description("完成挖掘后保持模块激活。")
        .defaultValue(false)
        .build()
    );

    // Rendering
    private final Setting<ShapeMode> shapeMode = sgRendering.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("形状的渲染方式。")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRendering.add(new ColorSetting.Builder()
        .name("边缘颜色")
        .description("边缘的颜色。")
        .defaultValue(new SettingColor(255, 255, 255, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRendering.add(new ColorSetting.Builder()
        .name("线条颜色")
        .description("线条的颜色。")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private enum Status {
        SEL_START,
        SEL_END,
        WORKING
    }

    private Status status = Status.SEL_START;
    private BetterBlockPos start, end;

    public Excavator() {
        super(Categories.World, "挖掘器", "挖掘一个选择区域。");
    }

    @Override
    public void onDeactivate() {
        baritone.getSelectionManager().removeSelection(baritone.getSelectionManager().getLastSelection());
        if (baritone.getBuilderProcess().isActive()) baritone.getCommandManager().execute("停止");
        status = Status.SEL_START;
    }

    @EventHandler
    private void onMouseButton(MouseButtonEvent event) {
        if (event.action != KeyAction.Press || event.button != selectionKey.get().getValue() || mc.currentScreen != null) {
            return;
        }
        selectCorners();
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        if (event.action != KeyAction.Press || event.key != selectionKey.get().getValue() || mc.currentScreen != null) {
            return;
        }
        selectCorners();
    }

    private void selectCorners() {
        if (!(mc.crosshairTarget instanceof BlockHitResult result)) return;

        if (status == Status.SEL_START) {
            start = BetterBlockPos.from(result.getBlockPos());
            status = Status.SEL_END;
            if (logSelection.get()) {
                info("开始角设置为: (%d, %d, %d)".formatted(start.getX(), start.getY(), start.getZ()));
            }
        } else if (status == Status.SEL_END) {
            end = BetterBlockPos.from(result.getBlockPos());
            status = Status.WORKING;
            if (logSelection.get()) {
                info("结束角设置为: (%d, %d, %d)".formatted(end.getX(), end.getY(), end.getZ()));
            }
            baritone.getSelectionManager().addSelection(start, end);
            baritone.getBuilderProcess().clearArea(start, end);
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (status == Status.SEL_START || status == Status.SEL_END) {
            if (!(mc.crosshairTarget instanceof BlockHitResult result)) return;
            event.renderer.box(result.getBlockPos(), sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        } else if (status == Status.WORKING && !baritone.getBuilderProcess().isActive()) {
            if (keepActive.get()) {
                baritone.getSelectionManager().removeSelection(baritone.getSelectionManager().getLastSelection());
                status = Status.SEL_START;
            } else toggle();
        }
    }
}
