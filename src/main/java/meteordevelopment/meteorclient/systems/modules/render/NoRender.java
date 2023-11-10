/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.world.ChunkOcclusionEvent;
import meteordevelopment.meteorclient.events.world.ParticleEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;

import java.util.List;
import java.util.Set;

public class NoRender extends Module {
    private final SettingGroup sgOverlay = settings.createGroup("覆盖");
    private final SettingGroup sgHUD = settings.createGroup("HUD");
    private final SettingGroup sgWorld = settings.createGroup("世界");
    private final SettingGroup sgEntity = settings.createGroup("实体");

    // Overlay

    private final Setting<Boolean> noPortalOverlay = sgOverlay.add(new BoolSetting.Builder()
        .name("传送门覆盖")
        .description("禁用下界传送门的覆盖效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noSpyglassOverlay = sgOverlay.add(new BoolSetting.Builder()
        .name("望远镜覆盖")
        .description("禁用望远镜的覆盖效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noNausea = sgOverlay.add(new BoolSetting.Builder()
        .name("恶心")
        .description("禁用恶心的覆盖效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noPumpkinOverlay = sgOverlay.add(new BoolSetting.Builder()
        .name("南瓜头覆盖")
        .description("禁用南瓜头的覆盖效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noPowderedSnowOverlay = sgOverlay.add(new BoolSetting.Builder()
        .name("粉雪覆盖")
        .description("禁用粉雪的覆盖效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noFireOverlay = sgOverlay.add(new BoolSetting.Builder()
        .name("火焰覆盖")
        .description("禁用火焰的覆盖效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noLiquidOverlay = sgOverlay.add(new BoolSetting.Builder()
        .name("液体覆盖")
        .description("禁用液体的覆盖效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noInWallOverlay = sgOverlay.add(new BoolSetting.Builder()
        .name("墙内覆盖")
        .description("禁用在方块内时的覆盖效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noVignette = sgOverlay.add(new BoolSetting.Builder()
        .name("晕影")
        .description("禁用晕影的覆盖效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noGuiBackground = sgOverlay.add(new BoolSetting.Builder()
        .name("GUI背景")
        .description("禁用GUI背景的覆盖效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noTotemAnimation = sgOverlay.add(new BoolSetting.Builder()
        .name("图腾动画")
        .description("禁用当你使用图腾时的动画效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noEatParticles = sgOverlay.add(new BoolSetting.Builder()
        .name("吃东西的粒子")
        .description("禁用吃东西的粒子效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noEnchantGlint = sgOverlay.add(new BoolSetting.Builder()
        .name("附魔光泽")
        .description("禁用附魔光泽的渲染效果。")
        .defaultValue(false)
        .build()
    );

    // HUD

    private final Setting<Boolean> noBossBar = sgHUD.add(new BoolSetting.Builder()
        .name("Boss条")
        .description("禁用Boss条的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noScoreboard = sgHUD.add(new BoolSetting.Builder()
        .name("计分板")
        .description("禁用计分板的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noCrosshair = sgHUD.add(new BoolSetting.Builder()
        .name("准星")
        .description("禁用准星的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noHeldItemName = sgHUD.add(new BoolSetting.Builder()
        .name("手持物品名称")
        .description("禁用手持物品名称的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noObfuscation = sgHUD.add(new BoolSetting.Builder()
        .name("混淆")
        .description("禁用字符的混淆样式。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noPotionIcons = sgHUD.add(new BoolSetting.Builder()
        .name("药水图标")
        .description("禁用状态效果图标的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noMessageSignatureIndicator = sgHUD.add(new BoolSetting.Builder()
        .name("聊天消息签名指示器")
        .description("禁用聊天消息左侧的签名指示器。")
        .defaultValue(false)
        .build()
    );

    // World

    private final Setting<Boolean> noWeather = sgWorld.add(new BoolSetting.Builder()
        .name("天气")
        .description("禁用天气的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noBlindness = sgWorld.add(new BoolSetting.Builder()
        .name("失明")
        .description("禁用失明的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noDarkness = sgWorld.add(new BoolSetting.Builder()
        .name("黑暗")
        .description("禁用黑暗的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noFog = sgWorld.add(new BoolSetting.Builder()
        .name("雾")
        .description("禁用雾的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noEnchTableBook = sgWorld.add(new BoolSetting.Builder()
        .name("附魔台书")
        .description("禁用附魔台上方的书的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noSignText = sgWorld.add(new BoolSetting.Builder()
        .name("告示牌文本")
        .description("禁用告示牌上的文本的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noBlockBreakParticles = sgWorld.add(new BoolSetting.Builder()
        .name("方块破坏粒子")
        .description("禁用方块破坏的粒子效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noBlockBreakOverlay = sgWorld.add(new BoolSetting.Builder()
        .name("方块破坏覆盖")
        .description("禁用方块破坏的覆盖效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noSkylightUpdates = sgWorld.add(new BoolSetting.Builder()
        .name("天光更新")
        .description("禁用天光更新的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noBeaconBeams = sgWorld.add(new BoolSetting.Builder()
        .name("信标光束")
        .description("禁用信标光束的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noFallingBlocks = sgWorld.add(new BoolSetting.Builder()
        .name("掉落的方块")
        .description("禁用掉落的方块的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noCaveCulling = sgWorld.add(new BoolSetting.Builder()
        .name("洞穴剔除")
        .description("禁用Minecraft的洞穴剔除算法。")
        .defaultValue(false)
        .onChanged(b -> mc.worldRenderer.reload())
        .build()
    );

    private final Setting<Boolean> noMapMarkers = sgWorld.add(new BoolSetting.Builder()
        .name("地图标记")
        .description("禁用地图上的标记。")
        .defaultValue(false)
        .build()
    );

    private final Setting<BannerRenderMode> bannerRender = sgWorld.add(new EnumSetting.Builder<BannerRenderMode>()
        .name("旗帜")
        .description("改变旗帜的渲染效果。")
        .defaultValue(BannerRenderMode.Everything)
        .build()
    );

    private final Setting<Boolean> noFireworkExplosions = sgWorld.add(new BoolSetting.Builder()
        .name("烟花爆炸")
        .description("禁用烟花爆炸的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<ParticleType<?>>> particles = sgWorld.add(new ParticleTypeListSetting.Builder()
        .name("粒子")
        .description("不渲染的粒子。")
        .build()
    );

    private final Setting<Boolean> noBarrierInvis = sgWorld.add(new BoolSetting.Builder()
        .name("屏障隐形")
        .description("禁用当不持有屏障时屏障隐形的效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noTextureRotations = sgWorld.add(new BoolSetting.Builder()
        .name("纹理旋转")
        .description("改变纹理旋转和模型偏移为使用随机值而不是方块位置。")
        .defaultValue(false)
        .onChanged(b -> mc.worldRenderer.reload())
        .build()
    );

    // Entity

    private final Setting<Set<EntityType<?>>> entities = sgEntity.add(new EntityTypeListSetting.Builder()
        .name("实体")
        .description("禁用选定实体的渲染效果。")
        .build()
    );

    private final Setting<Boolean> dropSpawnPacket = sgEntity.add(new BoolSetting.Builder()
        .name("丢弃生成包")
        .description("警告！丢弃选定实体的生成包。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noArmor = sgEntity.add(new BoolSetting.Builder()
        .name("盔甲")
        .description("禁用实体上的盔甲的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noInvisibility = sgEntity.add(new BoolSetting.Builder()
        .name("隐形")
        .description("显示隐形的实体。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noGlowing = sgEntity.add(new BoolSetting.Builder()
        .name("发光")
        .description("禁用发光效果的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noMobInSpawner = sgEntity.add(new BoolSetting.Builder()
        .name("刷怪笼实体")
        .description("禁用刷怪笼内旋转的怪物的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noDeadEntities = sgEntity.add(new BoolSetting.Builder()
        .name("死亡实体")
        .description("禁用死亡实体的渲染效果。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noNametags = sgEntity.add(new BoolSetting.Builder()
        .name("名牌")
        .description("禁用实体名牌的渲染效果。")
        .defaultValue(false)
        .build()
    );

    public NoRender() {
        super(Categories.Render, "不渲染", "禁用某些动画或覆盖的渲染效果。");
    }

    @Override
    public void onActivate() {
        if (noCaveCulling.get()) mc.worldRenderer.reload();
    }

    @Override
    public void onDeactivate() {
        if (noCaveCulling.get()) mc.worldRenderer.reload();
    }

    // Overlay

    public boolean noPortalOverlay() {
        return isActive() && noPortalOverlay.get();
    }

    public boolean noSpyglassOverlay() {
        return isActive() && noSpyglassOverlay.get();
    }

    public boolean noNausea() {
        return isActive() && noNausea.get();
    }

    public boolean noPumpkinOverlay() {
        return isActive() && noPumpkinOverlay.get();
    }

    public boolean noFireOverlay() {
        return isActive() && noFireOverlay.get();
    }

    public boolean noLiquidOverlay() {
        return isActive() && noLiquidOverlay.get();
    }

    public boolean noPowderedSnowOverlay() {
        return isActive() && noPowderedSnowOverlay.get();
    }

    public boolean noInWallOverlay() {
        return isActive() && noInWallOverlay.get();
    }

    public boolean noVignette() {
        return isActive() && noVignette.get();
    }

    public boolean noGuiBackground() {
        return isActive() && noGuiBackground.get();
    }

    public boolean noTotemAnimation() {
        return isActive() && noTotemAnimation.get();
    }

    public boolean noEatParticles() {
        return isActive() && noEatParticles.get();
    }

    public boolean noEnchantGlint() {
        return isActive() && noEnchantGlint.get();
    }

    // HUD

    public boolean noBossBar() {
        return isActive() && noBossBar.get();
    }

    public boolean noScoreboard() {
        return isActive() && noScoreboard.get();
    }

    public boolean noCrosshair() {
        return isActive() && noCrosshair.get();
    }

    public boolean noHeldItemName() {
        return isActive() && noHeldItemName.get();
    }

    public boolean noObfuscation() {
        return isActive() && noObfuscation.get();
    }

    public boolean noPotionIcons() {
        return isActive() && noPotionIcons.get();
    }

    public boolean noMessageSignatureIndicator() {
        return isActive() && noMessageSignatureIndicator.get();
    }

    // World

    public boolean noWeather() {
        return isActive() && noWeather.get();
    }

    public boolean noBlindness() {
        return isActive() && noBlindness.get();
    }

    public boolean noDarkness() {
        return isActive() && noDarkness.get();
    }

    public boolean noFog() {
        return isActive() && noFog.get();
    }

    public boolean noEnchTableBook() {
        return isActive() && noEnchTableBook.get();
    }

    public boolean noSignText() {
        return isActive() && noSignText.get();
    }

    public boolean noBlockBreakParticles() {
        return isActive() && noBlockBreakParticles.get();
    }

    public boolean noBlockBreakOverlay() {
        return isActive() && noBlockBreakOverlay.get();
    }

    public boolean noSkylightUpdates() {
        return isActive() && noSkylightUpdates.get();
    }

    public boolean noBeaconBeams() {
        return isActive() && noBeaconBeams.get();
    }

    public boolean noFallingBlocks() {
        return isActive() && noFallingBlocks.get();
    }

    @EventHandler
    private void onChunkOcclusion(ChunkOcclusionEvent event) {
        if (noCaveCulling.get()) event.cancel();
    }

    public boolean noMapMarkers() {
        return isActive() && noMapMarkers.get();
    }

    public BannerRenderMode getBannerRenderMode() {
        if (!isActive()) return BannerRenderMode.Everything;
        else return bannerRender.get();
    }

    public boolean noFireworkExplosions() {
        return isActive() && noFireworkExplosions.get();
    }

    @EventHandler
    private void onAddParticle(ParticleEvent event) {
        if (noWeather.get() && event.particle.getType() == ParticleTypes.RAIN) event.cancel();
        else if (noFireworkExplosions.get() && event.particle.getType() == ParticleTypes.FIREWORK) event.cancel();
        else if (particles.get().contains(event.particle.getType())) event.cancel();
    }

    public boolean noBarrierInvis() {
        return isActive() && noBarrierInvis.get();
    }

    public boolean noTextureRotations() {
        return isActive() && noTextureRotations.get();
    }

    // Entity

    public boolean noEntity(Entity entity) {
        return isActive() && entities.get().contains(entity.getType());
    }

    public boolean noEntity(EntityType<?> entity) {
        return isActive() && entities.get().contains(entity);
    }

    public boolean getDropSpawnPacket() {
        return isActive() && dropSpawnPacket.get();
    }

    public boolean noArmor() {
        return isActive() && noArmor.get();
    }

    public boolean noInvisibility() {
        return isActive() && noInvisibility.get();
    }

    public boolean noGlowing() {
        return isActive() && noGlowing.get();
    }

    public boolean noMobInSpawner() {
        return isActive() && noMobInSpawner.get();
    }

    public boolean noDeadEntities() {
        return isActive() && noDeadEntities.get();
    }

    public boolean noNametags() {
        return isActive() && noNametags.get();
    }

    public enum BannerRenderMode {
        Everything,
        Pillar,
        None
    }
}
