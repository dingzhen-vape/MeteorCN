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
    private final SettingGroup sgThroughWalls = settings.createGroup("穿墙");
    private final SettingGroup sgPlayers = settings.createGroup("玩家");
    private final SettingGroup sgCrystals = settings.createGroup("水晶");
    private final SettingGroup sgHand = settings.createGroup("手");

    // Through walls

    public final Setting<Set<EntityType<?>>> entities = sgThroughWalls.add(new EntityTypeListSetting.Builder()
        .name("实体")
        .description("选择要穿墙显示的实体。")
        .build()
    );

    public final Setting<Shader> shader = sgThroughWalls.add(new EnumSetting.Builder<Shader>()
        .name("着色器")
        .description("在实体上渲染一个着色器。")
        .defaultValue(Shader.Image)
        .onModuleActivated(setting -> updateShader(setting.get()))
        .onChanged(this::updateShader)
        .build()
    );

    public final Setting<SettingColor> shaderColor = sgThroughWalls.add(new ColorSetting.Builder()
        .name("颜色")
        .description("着色器绘制的颜色。")
        .defaultValue(new SettingColor(255, 255, 255, 150))
        .visible(() -> shader.get() != Shader.None)
        .build()
    );

    public final Setting<Boolean> ignoreSelfDepth = sgThroughWalls.add(new BoolSetting.Builder()
        .name("忽略自己")
        .description("忽略自己绘制玩家。")
        .defaultValue(true)
        .build()
    );

    // Players

    public final Setting<Boolean> players = sgPlayers.add(new BoolSetting.Builder()
        .name("玩家")
        .description("启用玩家模型的微调。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> ignoreSelf = sgPlayers.add(new BoolSetting.Builder()
        .name("忽略自己")
        .description("在微调玩家模型时忽略自己。")
        .defaultValue(false)
        .visible(players::get)
        .build()
    );

    public final Setting<Boolean> playersTexture = sgPlayers.add(new BoolSetting.Builder()
        .name("纹理")
        .description("启用玩家模型的纹理。")
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
        .name("比例")
        .description("玩家的比例。")
        .defaultValue(1.0)
        .min(0.0)
        .visible(players::get)
        .build()
    );

    // Crystals

    public final Setting<Boolean> crystals = sgCrystals.add(new BoolSetting.Builder()
        .name("水晶")
        .description("启用末影水晶的模型微调。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> crystalsScale = sgCrystals.add(new DoubleSetting.Builder()
        .name("比例")
        .description("水晶的比例。")
        .defaultValue(0.6)
        .min(0)
        .visible(crystals::get)
        .build()
    );

    public final Setting<Double> crystalsBounce = sgCrystals.add(new DoubleSetting.Builder()
        .name("弹跳")
        .description("水晶弹跳的高度。")
        .defaultValue(0.6)
        .min(0.0)
        .visible(crystals::get)
        .build()
    );

    public final Setting<Double> crystalsRotationSpeed = sgCrystals.add(new DoubleSetting.Builder()
        .name("旋转速度")
        .description("水晶旋转速度的倍数。")
        .defaultValue(0.3)
        .min(0)
        .visible(crystals::get)
        .build()
    );

    public final Setting<Boolean> crystalsTexture = sgCrystals.add(new BoolSetting.Builder()
        .name("纹理")
        .description("是否渲染水晶模型的纹理。")
        .defaultValue(true)
        .visible(crystals::get)
        .build()
    );

    public final Setting<Boolean> renderCore = sgCrystals.add(new BoolSetting.Builder()
        .name("渲染核心")
        .description("启用水晶核心的渲染。")
        .defaultValue(false)
        .visible(crystals::get)
        .build()
    );

    public final Setting<SettingColor> crystalsCoreColor = sgCrystals.add(new ColorSetting.Builder()
        .name("核心颜色")
        .description("水晶核心的颜色。")
        .defaultValue(new SettingColor(198, 135, 254, 255))
        .visible(() -> crystals.get() && renderCore.get())
        .build()
    );

    public final Setting<Boolean> renderFrame1 = sgCrystals.add(new BoolSetting.Builder()
        .name("渲染内框架")
        .description("启用水晶内框架的渲染。")
        .defaultValue(true)
        .visible(crystals::get)
        .build()
    );

    public final Setting<SettingColor> crystalsFrame1Color = sgCrystals.add(new ColorSetting.Builder()
        .name("内框架颜色")
        .description("水晶内框架的颜色。")
        .defaultValue(new SettingColor(198, 135, 254, 255))
        .visible(() -> crystals.get() && renderFrame1.get())
        .build()
    );

    public final Setting<Boolean> renderFrame2 = sgCrystals.add(new BoolSetting.Builder()
        .name("渲染外框架")
        .description("启用水晶外框架的渲染。")
        .defaultValue(true)
        .visible(crystals::get)
        .build()
    );

    public final Setting<SettingColor> crystalsFrame2Color = sgCrystals.add(new ColorSetting.Builder()
        .name("外框架颜色")
        .description("水晶外框架的颜色。")
        .defaultValue(new SettingColor(198, 135, 254, 255))
        .visible(() -> crystals.get() && renderFrame2.get())
        .build()
    );

    // Hand

    public final Setting<Boolean> hand = sgHand.add(new BoolSetting.Builder()
        .name("启用")
        .description("启用手渲染的微调。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> handTexture = sgHand.add(new BoolSetting.Builder()
        .name("纹理")
        .description("是否渲染手的纹理。")
        .defaultValue(false)
        .visible(hand::get)
        .build()
    );

    public final Setting<SettingColor> handColor = sgHand.add(new ColorSetting.Builder()
        .name("手颜色")
        .description("你的手的颜色。")
        .defaultValue(new SettingColor(198, 135, 254, 150))
        .visible(hand::get)
        .build()
    );

    public static final Identifier BLANK = new MeteorIdentifier("textures/blank.png");

    public Chams() {
        super(Categories.Render, "变色龙", "微调实体的渲染。");
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
