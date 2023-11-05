/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;

public class NoSlow extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> items = sgGeneral.add(new BoolSetting.Builder()
        .name("items")
        .description("使用物品是否会减慢你的速度。")
        .defaultValue(true)
        .build()
    );

    private final Setting<WebMode> web = sgGeneral.add(new EnumSetting.Builder<WebMode>()
        .name("web")
        .description("蜘蛛网是否不会减慢你的速度。")
        .defaultValue(WebMode.Vanilla)
        .build()
    );

    private final Setting<Double> webTimer = sgGeneral.add(new DoubleSetting.Builder()
        .name("web-timer")
        .description("WebMode Timer 的计时器值。")
        .defaultValue(10)
        .min(1)
        .sliderMin(1)
        .visible(() -> web.get() == WebMode.Timer)
        .build()
    );

    private final Setting<Boolean> honeyBlock = sgGeneral.add(new BoolSetting.Builder()
        .name("蜂蜜块")
        .description("蜂蜜块是否不会减慢你的速度。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> soulSand = sgGeneral.add(new BoolSetting.Builder()
        .name("灵魂沙")
        .description("灵魂沙是否不会减慢你的速度。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> slimeBlock = sgGeneral.add(new BoolSetting.Builder()
        .name("粘液块")
        .description("无论是否粘液块不会减慢你的速度。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> berryBush = sgGeneral.add(new BoolSetting.Builder()
        .name("浆果灌木丛")
        .description("浆果灌木丛是否不会减慢你的速度。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> airStrict = sgGeneral.add(new BoolSetting.Builder()
        .name("air-strict")
        .description("将尝试绕过 2b2t 等反作弊软件。仅在以下情况下有效在空气中。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> fluidDrag = sgGeneral.add(new BoolSetting.Builder()
        .name("流体阻力")
        .description("流体阻力是否不会减慢你的速度。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> sneaking = sgGeneral.add(new BoolSetting.Builder()
        .name("潜行")
        .description("潜行是否不会减慢你的速度。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> hunger = sgGeneral.add(new BoolSetting.Builder()
        .name("饥饿")
        .description("无论是否饥饿不会减慢你的速度。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> slowness = sgGeneral.add(new BoolSetting.Builder()
        .name("缓慢")
        .description("缓慢是否不会减慢你的速度。")
        .defaultValue(false)
        .build()
    );

    private boolean resetTimer;

    public NoSlow() {
        super(Categories.Movement, "不慢", "允许你在使用会减慢你速度的物体时正常移动。");
    }

    @Override
    public void onActivate() {
        resetTimer = false;
    }

    public boolean airStrict() {
        return isActive() && airStrict.get() && mc.player.isUsingItem();
    }

    public boolean items() {
        return isActive() && items.get();
    }

    public boolean honeyBlock() {
        return isActive() && honeyBlock.get();
    }

    public boolean soulSand() {
        return isActive() && soulSand.get();
    }

    public boolean slimeBlock() {
        return isActive() && slimeBlock.get();
    }

    public boolean cobweb() {
        return isActive() && web.get() == WebMode.Vanilla;
    }

    public boolean berryBush() {
        return isActive() && berryBush.get();
    }

    public boolean fluidDrag() {
        return isActive() && fluidDrag.get();
    }

    public boolean sneaking() {
        return isActive() && sneaking.get();
    }

    public boolean hunger() {
        return isActive() && hunger.get();
    }

    public boolean slowness() {
        return isActive() && slowness.get();
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        if (web.get() == WebMode.Timer) {
            if (mc.world.getBlockState(mc.player.getBlockPos()).getBlock() == Blocks.COBWEB && !mc.player.isOnGround()) {
                resetTimer = false;
                Modules.get().get(Timer.class).setOverride(webTimer.get());
            } else if (!resetTimer) {
                Modules.get().get(Timer.class).setOverride(Timer.OFF);
                resetTimer = true;
            }
        }
    }

    public enum WebMode {
        Vanilla,
        Timer,
        None
    }
}
