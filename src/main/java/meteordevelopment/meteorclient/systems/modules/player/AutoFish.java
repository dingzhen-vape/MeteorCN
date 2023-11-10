/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.Items;

public class AutoFish extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSplashRangeDetection = settings.createGroup("飞溅检测");

    // General

    private final Setting<Boolean> autoCast = sgGeneral.add(new BoolSetting.Builder()
        .name("自动投掷")
        .description("当不在钓鱼时自动投掷。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> ticksAutoCast = sgGeneral.add(new IntSetting.Builder()
        .name("投掷刻数")
        .description("自动投掷前等待的刻数。")
        .defaultValue(10)
        .min(0)
        .sliderMax(60)
        .build()
    );

    private final Setting<Integer> ticksCatch = sgGeneral.add(new IntSetting.Builder()
        .name("捕获延迟")
        .description("捕获鱼前等待的刻数。")
        .defaultValue(6)
        .min(0)
        .sliderMax(60)
        .build()
    );

    private final Setting<Integer> ticksThrow = sgGeneral.add(new IntSetting.Builder()
        .name("投掷延迟")
        .description("投掷鱼钩前等待的刻数。")
        .defaultValue(14)
        .min(0)
        .sliderMax(60)
        .build()
    );

    private final Setting<Boolean> antiBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("防破")
        .description("防止钓鱼竿破损。")
        .defaultValue(false)
        .build()
    );

    // Splash Detection

    private final Setting<Boolean> splashDetectionRangeEnabled = sgSplashRangeDetection.add(new BoolSetting.Builder()
        .name("飞溅检测范围-启用")
        .description("让你可以在彼此旁边使用多个账号。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> splashDetectionRange = sgSplashRangeDetection.add(new DoubleSetting.Builder()
        .name("飞溅检测范围")
        .description("飞溅的检测范围。低的值在TPS低时不会工作。")
        .defaultValue(10)
        .min(0)
        .build()
    );

    private boolean ticksEnabled;
    private int ticksToRightClick;
    private int ticksData;

    private int autoCastTimer;
    private boolean autoCastEnabled;

    private int autoCastCheckTimer;

    public AutoFish() {
        super(Categories.Player, "自动钓鱼", "自动为你钓鱼。");
    }

    @Override
    public void onActivate() {
        ticksEnabled = false;
        autoCastEnabled = false;
        autoCastCheckTimer = 0;
    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        SoundInstance p = event.sound;
        FishingBobberEntity b = mc.player.fishHook;

        if (p.getId().getPath().equals("entity.fishing_bobber.splash")) {
            if (!splashDetectionRangeEnabled.get() || Utils.distance(b.getX(), b.getY(), b.getZ(), p.getX(), p.getY(), p.getZ()) <= splashDetectionRange.get()) {
                ticksEnabled = true;
                ticksToRightClick = ticksCatch.get();
                ticksData = 0;
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // Auto cast
        if (autoCastCheckTimer <= 0) {
            autoCastCheckTimer = 30;

            if (autoCast.get() && !ticksEnabled && !autoCastEnabled && mc.player.fishHook == null && hasFishingRod()) {
                autoCastTimer = 0;
                autoCastEnabled = true;
            }
        } else {
            autoCastCheckTimer--;
        }

        // Check for auto cast timer
        if (autoCastEnabled) {
            autoCastTimer++;

            if (autoCastTimer > ticksAutoCast.get()) {
                autoCastEnabled = false;
                Utils.rightClick();
            }
        }

        // Handle logic
        if (ticksEnabled && ticksToRightClick <= 0) {
            if (ticksData == 0) {
                Utils.rightClick();
                ticksToRightClick = ticksThrow.get();
                ticksData = 1;
            }
            else if (ticksData == 1) {
                Utils.rightClick();
                ticksEnabled = false;
            }
        }

        ticksToRightClick--;
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        if (mc.options.useKey.isPressed()) ticksEnabled = false;
    }

    private boolean hasFishingRod() {
        return InvUtils.swap(InvUtils.findInHotbar(itemStack -> itemStack.getItem() == Items.FISHING_ROD && (!antiBreak.get() || itemStack.getDamage() < itemStack.getMaxDamage() - 1)).slot(), false);
    }
}
