/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.MeteorIdentifier;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.render.postprocess.PostProcessShaders;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;

import java.util.Set;

public class Chams extends Module {
    private final SettingGroup sgThroughWalls = settings.createGroup("穿过墙壁");
    private final SettingGroup sgPlayers = settings.createGroup("玩家");
    private final SettingGroup sgCrystals = settings.createGroup("水晶");
    private final SettingGroup sgHand = settings.createGroup("手");

    // Through walls

    public final Setting<Set<EntityType<?>>> entities = sgThroughWalls.add(new EntityTypeListSetting.Builder()
        .name("实体")
        .description("选择要穿过墙壁显示的实体。")
        .build()
    );

    public final Setting<Shader> shader = sgThroughWalls.add(new EnumSetting.Builder<Shader>()
        .name("着色器")
        .description("在实体上渲染着色器。")
        .defaultValue(Shader.Image)
        .onModuleActivated(setting -> updateShader(setting.get()))
        .onChanged(this::updateShader)
        .build()
    );

    public final Setting<SettingColor> shaderColor = sgThroughWalls.add(new ColorSetting.Builder()
        .name("颜色")
        .description("绘制着色器所用的颜色。")
        .defaultValue(new SettingColor(255, 255, 255, 150))
        .visible(() -> shader.get() != Shader.None)
        .build()
    );

    public final Setting<Boolean> ignoreSelfDepth = sgThroughWalls.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("忽略你自己绘制玩家。")
        .defaultValue(true)
        .build()
    );

    // Players

    public final Setting<Boolean> players = sgPlayers.add(new BoolSetting.Builder()
        .name("玩家")
        .description("为玩家启用模型调整。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> ignoreSelf = sgPlayers.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("忽略你自己调整玩家模型时。")
        .defaultValue(false)
        .visible(players::get)
        .build()
    );

    public final Setting<Boolean> playersTexture = sgPlayers.add(new BoolSetting.Builder()
        .name("texture")
        .description("启用玩家模型纹理。")
        .defaultValue(false)
        .visible(players::get)
        .build()
    );

    public final Setting<SettingColor> playersColor = sgPlayers.add(new ColorSetting.Builder()
        .name("颜色")
        .description("玩家模型的颜色。")
        .defaultValue(new SettingColor(198, 135, 254, 150))
        .visible(players::get)
        .build()
    );

    public final Setting<Double> playersScale = sgPlayers.add(new DoubleSetting.Builder()
        .name("scale")
        .description("玩家比例。")
        .defaultValue(1.0)
        .min(0.0)
        .visible(players::get)
        .build()
    );

    // Crystals

    public final Setting<Boolean> crystals = sgCrystals.add(new BoolSetting.Builder()
        .name("crystals")
        .description("启用模型调整对于最终晶体。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> crystalsScale = sgCrystals.add(new DoubleSetting.Builder()
        .name("scale")
        .description("晶体规模。")
        .defaultValue(0.6)
        .min(0)
        .visible(crystals::get)
        .build()
    );

    public final Setting<Double> crystalsBounce = sgCrystals.add(new DoubleSetting.Builder()
        .name("bounce")
        .description("晶体弹跳有多高。")
        .defaultValue(0.6)
        .min(0.0)
        .visible(crystals::get)
        .build()
    );

    public final Setting<Double> crystalsRotationSpeed = sgCrystals.add(new DoubleSetting.Builder()
        .name("rotation-speed")
        .description("乘以晶体的旋转速度。")
        .defaultValue(0.3)
        .min(0)
        .visible(crystals::get)
        .build()
    );

    public final Setting<Boolean> crystalsTexture = sgCrystals.add(new BoolSetting.Builder()
        .name("texture")
        .description("是否渲染水晶模型纹理。")
        .defaultValue(true)
        .visible(crystals::get)
        .build()
    );

    public final Setting<Boolean> renderCore = sgCrystals.add(new BoolSetting.Builder()
        .name("render-core")
        .description("启用水晶核心的渲染。")
        .defaultValue(false)
        .visible(crystals::get)
        .build()
    );

    public final Setting<SettingColor> crystalsCoreColor = sgCrystals.add(new ColorSetting.Builder()
        .name("core-color")
        .description("水晶核心的颜色。")
        .defaultValue(new SettingColor(198, 135, 254, 255))
        .visible(() -> crystals.get() && renderCore.get())
        .build()
    );

    public final Setting<Boolean> renderFrame1 = sgCrystals.add(new BoolSetting.Builder()
        .name("render-inner- frame")
        .description("启用水晶内部框架的渲染。")
        .defaultValue(true)
        .visible(crystals::get)
        .build()
    );

    public final Setting<SettingColor> crystalsFrame1Color = sgCrystals.add(new ColorSetting.Builder()
        .name("inner-frame-color")
        .description("水晶内部框架的颜色。")
        .defaultValue(new SettingColor(198, 135, 254, 255))
        .visible(() -> crystals.get() && renderFrame1.get())
        .build()
    );

    public final Setting<Boolean> renderFrame2 = sgCrystals.add(new BoolSetting.Builder()
        .name("render-outer-frame")
        .description("启用水晶内部框架的渲染晶体的外框。")
        .defaultValue(true)
        .visible(crystals::get)
        .build()
    );

    public final Setting<SettingColor> crystalsFrame2Color = sgCrystals.add(new ColorSetting.Builder()
        .name("outer-frame-color")
        .description("晶体外框的颜色。")
        .defaultValue(new SettingColor(198, 135, 254, 255))
        .visible(() -> crystals.get() && renderFrame2.get())
        .build()
    );

    // Hand

    public final Setting<Boolean> hand = sgHand.add(new BoolSetting.Builder()
        .name("enabled")
        .description("启用手动渲染调整。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> handTexture = sgHand.add(new BoolSetting.Builder()
        .name("texture")
        .description("是否渲染手纹理。")
        .defaultValue(false)
        .visible(hand::get)
        .build()
    );

    public final Setting<SettingColor> handColor = sgHand.add(new ColorSetting.Builder()
        .name("hand-color")
        .description("你手的颜色。")
        .defaultValue(new SettingColor(198, 135, 254, 150))
        .visible(hand::get)
        .build()
    );

    public static final Identifier BLANK = new MeteorIdentifier("textures/blank.png");

    public Chams() {
        super(Categories.Render, "chams", "调整实体的渲染。");
    }

    public boolean shouldRender(Entity entity) {
        return isActive() && !isShader() && entities.get().contains(entity.getType()) && (entity != mc.player || ignoreSelfDepth.get());
    }

    public boolean isShader() {
        return isActive() && shader.get() != Shader.None;
    }

    public void updateShader(Shader value) {
        if (value == Shader.None) return;
        PostProcessShaders.CHAMS.init(Utils.titleToName(value.name()));
    }

    public enum Shader {
        Image,
        None
    }
}
