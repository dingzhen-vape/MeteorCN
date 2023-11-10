/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.util.math.Vec3d;

/**
 * @author Walaryne
 */
public class Ambience extends Module {
    private final SettingGroup sgSky = settings.createGroup("天空");
    private final SettingGroup sgWorld = settings.createGroup("世界");

    // Sky

    public final Setting<Boolean> endSky = sgSky.add(new BoolSetting.Builder()
        .name("末地天空")
        .description("使天空像末地一样。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> customSkyColor = sgSky.add(new BoolSetting.Builder()
        .name("自定义天空颜色")
        .description("是否应该改变天空的颜色。")
        .defaultValue(false)
        .build()
    );

    public final Setting<SettingColor> overworldSkyColor = sgSky.add(new ColorSetting.Builder()
        .name("主世界天空颜色")
        .description("主世界天空的颜色。")
        .defaultValue(new SettingColor(0, 125, 255))
        .visible(customSkyColor::get)
        .build()
    );

    public final Setting<SettingColor> netherSkyColor = sgSky.add(new ColorSetting.Builder()
        .name("下界天空颜色")
        .description("下界天空的颜色。")
        .defaultValue(new SettingColor(102, 0, 0))
        .visible(customSkyColor::get)
        .build()
    );

    public final Setting<SettingColor> endSkyColor = sgSky.add(new ColorSetting.Builder()
        .name("末地天空颜色")
        .description("末地天空的颜色。")
        .defaultValue(new SettingColor(65, 30, 90))
        .visible(customSkyColor::get)
        .build()
    );

    public final Setting<Boolean> customCloudColor = sgSky.add(new BoolSetting.Builder()
        .name("自定义云彩颜色")
        .description("是否应该改变云彩的颜色。")
        .defaultValue(false)
        .build()
    );

    public final Setting<SettingColor> cloudColor = sgSky.add(new ColorSetting.Builder()
        .name("云彩颜色")
        .description("云彩的颜色。")
        .defaultValue(new SettingColor(102, 0, 0))
        .visible(customCloudColor::get)
        .build()
    );

    public final Setting<Boolean> changeLightningColor = sgSky.add(new BoolSetting.Builder()
        .name("自定义闪电颜色")
        .description("是否应该改变闪电的颜色。")
        .defaultValue(false)
        .build()
    );

    public final Setting<SettingColor> lightningColor = sgSky.add(new ColorSetting.Builder()
        .name("闪电颜色")
        .description("闪电的颜色。")
        .defaultValue(new SettingColor(102, 0, 0))
        .visible(changeLightningColor::get)
        .build()
    );

    // World
    public final Setting<Boolean> customGrassColor = sgWorld.add(new BoolSetting.Builder()
        .name("自定义草色")
        .description("是否应该改变草的颜色。")
        .defaultValue(false)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<SettingColor> grassColor = sgWorld.add(new ColorSetting.Builder()
        .name("草色")
        .description("草的颜色。")
        .defaultValue(new SettingColor(102, 0, 0))
        .visible(customGrassColor::get)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<Boolean> customFoliageColor = sgWorld.add(new BoolSetting.Builder()
        .name("自定义树叶颜色")
        .description("是否应该改变树叶的颜色。")
        .defaultValue(false)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<SettingColor> foliageColor = sgWorld.add(new ColorSetting.Builder()
        .name("树叶颜色")
        .description("树叶的颜色。")
        .defaultValue(new SettingColor(102, 0, 0))
        .visible(customFoliageColor::get)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<Boolean> customWaterColor = sgWorld.add(new BoolSetting.Builder()
        .name("自定义水色")
        .description("是否应该改变水的颜色。")
        .defaultValue(false)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<SettingColor> waterColor = sgWorld.add(new ColorSetting.Builder()
        .name("水色")
        .description("水的颜色。")
        .defaultValue(new SettingColor(102, 0, 0))
        .visible(customWaterColor::get)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<Boolean> customLavaColor = sgWorld.add(new BoolSetting.Builder()
        .name("自定义岩浆颜色")
        .description("是否应该改变岩浆的颜色。")
        .defaultValue(false)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<SettingColor> lavaColor = sgWorld.add(new ColorSetting.Builder()
        .name("岩浆颜色")
        .description("岩浆的颜色。")
        .defaultValue(new SettingColor(102, 0, 0))
        .visible(customLavaColor::get)
        .onChanged(val -> reload())
        .build()
    );

    public Ambience() {
        super(Categories.World, "氛围", "改变环境中各个部分的颜色。");
    }

    @Override
    public void onActivate() {
        reload();
    }

    @Override
    public void onDeactivate() {
        reload();
    }

    private void reload() {
        if (mc.worldRenderer != null && isActive()) mc.worldRenderer.reload();
    }

    public static class Custom extends DimensionEffects {
        public Custom() {
            super(Float.NaN, true, DimensionEffects.SkyType.END, true, false);
        }

        @Override
        public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
            return color.multiply(0.15000000596046448D);
        }

        @Override
        public boolean useThickFog(int camX, int camY) {
            return false;
        }

        @Override
        public float[] getFogColorOverride(float skyAngle, float tickDelta) {
            return null;
        }
    }

    public SettingColor skyColor() {
        switch (PlayerUtils.getDimension()) {
            case Overworld -> {
                return overworldSkyColor.get();
            }
            case Nether -> {
                return netherSkyColor.get();
            }
            case End -> {
                return endSkyColor.get();
            }
        }

        return null;
    }
}
