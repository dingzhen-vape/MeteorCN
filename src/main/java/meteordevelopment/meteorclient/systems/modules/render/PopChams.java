/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PopChams extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyOne = sgGeneral.add(new BoolSetting.Builder()
        .name("仅一")
        .description("每个玩家只允许一个幽灵。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> renderTime = sgGeneral.add(new DoubleSetting.Builder()
        .name("渲染时间")
        .description("以秒为单位的幽灵渲染时间。")
        .defaultValue(1)
        .min(0.1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> yModifier = sgGeneral.add(new DoubleSetting.Builder()
        .name("y-修正")
        .description("幽灵的Y轴位置每秒改变的量。")
        .defaultValue(0.75)
        .sliderRange(-4, 4)
        .build()
    );

    private final Setting<Double> scaleModifier = sgGeneral.add(new DoubleSetting.Builder()
        .name("比例-修正")
        .description("幽灵的比例每秒改变的量。")
        .defaultValue(-0.25)
        .sliderRange(-4, 4)
        .build()
    );

    private final Setting<Boolean> fadeOut = sgGeneral.add(new BoolSetting.Builder()
        .name("淡出")
        .description("淡出颜色。")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("形状的渲染方式。")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("边缘颜色")
        .description("边缘的颜色。")
        .defaultValue(new SettingColor(255, 255, 255, 25))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("线条颜色")
        .description("线条的颜色。")
        .defaultValue(new SettingColor(255, 255, 255, 127))
        .build()
    );

    private final List<GhostPlayer> ghosts = new ArrayList<>();

    public PopChams() {
        super(Categories.Render, "图腾幽灵", "在玩家爆图腾的地方渲染幽灵。");
    }

    @Override
    public void onDeactivate() {
        synchronized (ghosts) {
            ghosts.clear();
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityStatusS2CPacket p)) return;
        if (p.getStatus() != 35) return;

        Entity entity = p.getEntity(mc.world);
        if (!(entity instanceof PlayerEntity player) || entity == mc.player) return;

        synchronized (ghosts) {
            if (onlyOne.get()) ghosts.removeIf(ghostPlayer -> ghostPlayer.uuid.equals(entity.getUuid()));

            ghosts.add(new GhostPlayer(player));
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        synchronized (ghosts) {
            ghosts.removeIf(ghostPlayer -> ghostPlayer.render(event));
        }
    }

    private class GhostPlayer extends FakePlayerEntity {
        private final UUID uuid;
        private double timer, scale = 1;

        public GhostPlayer(PlayerEntity player) {
            super(player, "幽灵", 20, false);

            uuid = player.getUuid();
        }

        public boolean render(Render3DEvent event) {
            // Increment timer
            timer += event.frameTime;
            if (timer > renderTime.get()) return true;

            // Y Modifier
            lastRenderY = getY();
            ((IVec3d) getPos()).setY(getY() + yModifier.get() * event.frameTime);

            // Scale Modifier
            scale += scaleModifier.get() * event.frameTime;

            // Fade out
            int preSideA = sideColor.get().a;
            int preLineA = lineColor.get().a;

            if (fadeOut.get()) {
                sideColor.get().a *= 1 - timer / renderTime.get();
                lineColor.get().a *= 1 - timer / renderTime.get();
            }

            // Render
            WireframeEntityRenderer.render(event, this, scale, sideColor.get(), lineColor.get(), shapeMode.get());

            // Restore colors
            sideColor.get().a = preSideA;
            lineColor.get().a = preLineA;

            return false;
        }
    }
}
