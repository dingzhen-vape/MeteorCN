/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3d;

import java.util.Set;

public class ESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("颜色");

    // General

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("模式")
        .description("渲染模式。")
        .defaultValue(Mode.Shader)
        .build()
    );

    public final Setting<Integer> outlineWidth = sgGeneral.add(new IntSetting.Builder()
        .name("轮廓宽度")
        .description("着色器轮廓的宽度。")
        .visible(() -> mode.get() == Mode.Shader)
        .defaultValue(2)
        .range(1, 10)
        .sliderRange(1, 5)
        .build()
    );

    public final Setting<Double> glowMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("发光倍数")
        .description("发光效果的倍数。")
        .visible(() -> mode.get() == Mode.Shader)
        .decimalPlaces(3)
        .defaultValue(3.5)
        .min(0)
        .sliderMax(10)
        .build()
    );

    public final Setting<Boolean> ignoreSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("忽略自己")
        .description("不在着色器中绘制自己。")
        .defaultValue(true)
        .build()
    );

    public final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("形状的渲染方式。")
        .visible(() -> mode.get() != Mode.Glow)
        .defaultValue(ShapeMode.Both)
        .build()
    );

    public final Setting<Double> fillOpacity = sgGeneral.add(new DoubleSetting.Builder()
        .name("填充不透明度")
        .description("形状填充的不透明度。")
        .visible(() -> shapeMode.get() != ShapeMode.Lines && mode.get() != Mode.Glow)
        .defaultValue(0.3)
        .range(0, 1)
        .sliderMax(1)
        .build()
    );

    private final Setting<Double> fadeDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("淡出距离")
        .description("实体离开颜色开始淡出的距离。")
        .defaultValue(3)
        .min(0)
        .sliderMax(12)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("实体")
        .description("选择特定的实体。")
        .defaultValue(EntityType.PLAYER)
        .build()
    );

    // Colors

    public final Setting<Boolean> distance = sgColors.add(new BoolSetting.Builder()
        .name("距离颜色")
        .description("根据距离改变追踪线的颜色。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> friendOverride = sgColors.add(new BoolSetting.Builder()
        .name("显示好友颜色")
        .description("是否用好友颜色覆盖好友的距离颜色。")
        .defaultValue(true)
        .visible(distance::get)
        .build()
    );

    private final Setting<SettingColor> playersColor = sgColors.add(new ColorSetting.Builder()
        .name("玩家颜色")
        .description("其他玩家的颜色。")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> !distance.get())
        .build()
    );

    private final Setting<SettingColor> animalsColor = sgColors.add(new ColorSetting.Builder()
        .name("动物颜色")
        .description("动物的颜色。")
        .defaultValue(new SettingColor(25, 255, 25, 255))
        .visible(() -> !distance.get())
        .build()
    );

    private final Setting<SettingColor> waterAnimalsColor = sgColors.add(new ColorSetting.Builder()
        .name("水生动物颜色")
        .description("水生动物的颜色。")
        .defaultValue(new SettingColor(25, 25, 255, 255))
        .visible(() -> !distance.get())
        .build()
    );

    private final Setting<SettingColor> monstersColor = sgColors.add(new ColorSetting.Builder()
        .name("怪物颜色")
        .description("怪物的颜色。")
        .defaultValue(new SettingColor(255, 25, 25, 255))
        .visible(() -> !distance.get())
        .build()
    );

    private final Setting<SettingColor> ambientColor = sgColors.add(new ColorSetting.Builder()
        .name("环境颜色")
        .description("环境的颜色。")
        .defaultValue(new SettingColor(25, 25, 25, 255))
        .visible(() -> !distance.get())
        .build()
    );

    private final Setting<SettingColor> miscColor = sgColors.add(new ColorSetting.Builder()
        .name("杂项颜色")
        .description("杂项的颜色。")
        .defaultValue(new SettingColor(175, 175, 175, 255))
        .visible(() -> !distance.get())
        .build()
    );

    private final Color lineColor = new Color();
    private final Color sideColor = new Color();
    private final Color baseColor = new Color();

    private final Vector3d pos1 = new Vector3d();
    private final Vector3d pos2 = new Vector3d();
    private final Vector3d pos = new Vector3d();

    private int count;

    public ESP() {
        super(Categories.Render, "esp", "透过墙壁渲染实体。");
    }

    // Box

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mode.get() == Mode._2D) return;

        count = 0;

        for (Entity entity : mc.world.getEntities()) {
            if (shouldSkip(entity)) continue;

            if (mode.get() == Mode.Box || mode.get() == Mode.Wireframe) drawBoundingBox(event, entity);
            count++;
        }
    }

    private void drawBoundingBox(Render3DEvent event, Entity entity) {
        Color color = getColor(entity);
        if (color != null) {
            lineColor.set(color);
            sideColor.set(color).a((int) (sideColor.a * fillOpacity.get()));
        }

        if (mode.get() == Mode.Box) {
            double x = MathHelper.lerp(event.tickDelta, entity.lastRenderX, entity.getX()) - entity.getX();
            double y = MathHelper.lerp(event.tickDelta, entity.lastRenderY, entity.getY()) - entity.getY();
            double z = MathHelper.lerp(event.tickDelta, entity.lastRenderZ, entity.getZ()) - entity.getZ();

            Box box = entity.getBoundingBox();
            event.renderer.box(x + box.minX, y + box.minY, z + box.minZ, x + box.maxX, y + box.maxY, z + box.maxZ, sideColor, lineColor, shapeMode.get(), 0);
        } else {
            WireframeEntityRenderer.render(event, entity, 1, sideColor, lineColor, shapeMode.get());
        }
    }

    // 2D

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mode.get() != Mode._2D) return;

        Renderer2D.COLOR.begin();
        count = 0;

        for (Entity entity : mc.world.getEntities()) {
            if (shouldSkip(entity)) continue;

            Box box = entity.getBoundingBox();

            double x = MathHelper.lerp(event.tickDelta, entity.lastRenderX, entity.getX()) - entity.getX();
            double y = MathHelper.lerp(event.tickDelta, entity.lastRenderY, entity.getY()) - entity.getY();
            double z = MathHelper.lerp(event.tickDelta, entity.lastRenderZ, entity.getZ()) - entity.getZ();

            // Check corners
            pos1.set(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
            pos2.set(0, 0, 0);

            //     Bottom
            if (checkCorner(box.minX + x, box.minY + y, box.minZ + z, pos1, pos2)) continue;
            if (checkCorner(box.maxX + x, box.minY + y, box.minZ + z, pos1, pos2)) continue;
            if (checkCorner(box.minX + x, box.minY + y, box.maxZ + z, pos1, pos2)) continue;
            if (checkCorner(box.maxX + x, box.minY + y, box.maxZ + z, pos1, pos2)) continue;

            //     Top
            if (checkCorner(box.minX + x, box.maxY + y, box.minZ + z, pos1, pos2)) continue;
            if (checkCorner(box.maxX + x, box.maxY + y, box.minZ + z, pos1, pos2)) continue;
            if (checkCorner(box.minX + x, box.maxY + y, box.maxZ + z, pos1, pos2)) continue;
            if (checkCorner(box.maxX + x, box.maxY + y, box.maxZ + z, pos1, pos2)) continue;

            // Setup color
            Color color = getColor(entity);
            if (color != null) {
                lineColor.set(color);
                sideColor.set(color).a((int) (sideColor.a * fillOpacity.get()));
            }

            // Render
            if (shapeMode.get() != ShapeMode.Lines && sideColor.a > 0) {
                Renderer2D.COLOR.quad(pos1.x, pos1.y, pos2.x - pos1.x, pos2.y - pos1.y, sideColor);
            }

            if (shapeMode.get() != ShapeMode.Sides) {
                Renderer2D.COLOR.line(pos1.x, pos1.y, pos1.x, pos2.y, lineColor);
                Renderer2D.COLOR.line(pos2.x, pos1.y, pos2.x, pos2.y, lineColor);
                Renderer2D.COLOR.line(pos1.x, pos1.y, pos2.x, pos1.y, lineColor);
                Renderer2D.COLOR.line(pos1.x, pos2.y, pos2.x, pos2.y, lineColor);
            }

            count++;
        }

        Renderer2D.COLOR.render(null);
    }

    private boolean checkCorner(double x, double y, double z, Vector3d min, Vector3d max) {
        pos.set(x, y, z);
        if (!NametagUtils.to2D(pos, 1)) return true;

        // Check Min
        if (pos.x < min.x) min.x = pos.x;
        if (pos.y < min.y) min.y = pos.y;
        if (pos.z < min.z) min.z = pos.z;

        // Check Max
        if (pos.x > max.x) max.x = pos.x;
        if (pos.y > max.y) max.y = pos.y;
        if (pos.z > max.z) max.z = pos.z;

        return false;
    }

    // Utils

    public boolean shouldSkip(Entity entity) {
        if (!entities.get().contains(entity.getType())) return true;
        if (entity == mc.player && ignoreSelf.get()) return true;
        if (entity == mc.cameraEntity && mc.options.getPerspective().isFirstPerson()) return true;
        return !EntityUtils.isInRenderDistance(entity);
    }

    public Color getColor(Entity entity) {
        if (!entities.get().contains(entity.getType())) return null;

        double alpha = getFadeAlpha(entity);
        if (alpha == 0) return null;

        Color color = getEntityTypeColor(entity);
        return baseColor.set(color.r, color.g, color.b, (int) (color.a * alpha));
    }

    private double getFadeAlpha(Entity entity) {
        double dist = PlayerUtils.squaredDistanceToCamera(entity.getX() + entity.getWidth() / 2, entity.getY() + entity.getEyeHeight(entity.getPose()), entity.getZ() + entity.getWidth() / 2);
        double fadeDist = Math.pow(fadeDistance.get(), 2);
        double alpha = 1;
        if (dist <= fadeDist * fadeDist) alpha = (float) (Math.sqrt(dist) / fadeDist);
        if (alpha <= 0.075) alpha = 0;
        return alpha;
    }

    public Color getEntityTypeColor(Entity entity) {
        if (distance.get()) {
            if (friendOverride.get() && entity instanceof PlayerEntity && Friends.get().isFriend((PlayerEntity) entity)) {
                return Config.get().friendColor.get();
            } else return EntityUtils.getColorFromDistance(entity);
        } else if (entity instanceof PlayerEntity) {
            return PlayerUtils.getPlayerColor(((PlayerEntity) entity), playersColor.get());
        } else {
            return switch (entity.getType().getSpawnGroup()) {
                case CREATURE -> animalsColor.get();
                case WATER_AMBIENT, WATER_CREATURE, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> waterAnimalsColor.get();
                case MONSTER -> monstersColor.get();
                case AMBIENT -> ambientColor.get();
                default -> miscColor.get();
            };
        }
    }

    @Override
    public String getInfoString() {
        return Integer.toString(count);
    }

    public boolean isShader() {
        return isActive() && mode.get() == Mode.Shader;
    }

    public boolean isGlow() {
        return isActive() && mode.get() == Mode.Glow;
    }

    public enum Mode {
        Box,
        Wireframe,
        _2D,
        Shader,
        Glow;

        @Override
        public String toString() {
            return this == _2D ? "2D" : super.toString();
        }
    }
}
