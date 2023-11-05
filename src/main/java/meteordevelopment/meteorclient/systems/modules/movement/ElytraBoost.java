/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.InteractItemEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;

import java.util.ArrayList;
import java.util.List;

public class ElytraBoost extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> dontConsumeFirework = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-consume")
        .description("使用鞘翅增强时防止烟花被消耗。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> fireworkLevel = sgGeneral.add(new IntSetting.Builder()
        .name("firework-duration")
        .description("烟花的持续时间。")
        .defaultValue(0)
        .range(0, 255)
        .sliderMax(255)
        .build()
    );

    private final Setting<Boolean> playSound = sgGeneral.add(new BoolSetting.Builder()
        .name("play-sound")
        .description("在增强时播放烟花声音触发。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Keybind> keybind = sgGeneral.add(new KeybindSetting.Builder()
        .name("按键绑定")
        .description("增强的按键绑定。")
        .action(this::boost)
        .build()
    );

    private final List<FireworkRocketEntity> fireworks = new ArrayList<>();

    public ElytraBoost() {
        super(Categories.Movement, "鞘翅增强", "像使用烟花一样增强你的鞘翅。");
    }

    @Override
    public void onDeactivate() {
        fireworks.clear();
    }

    @EventHandler
    private void onInteractItem(InteractItemEvent event) {
        ItemStack itemStack = mc.player.getStackInHand(event.hand);

        if (itemStack.getItem() instanceof FireworkRocketItem && dontConsumeFirework.get()) {
            event.toReturn = ActionResult.PASS;

            boost();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        fireworks.removeIf(Entity::isRemoved);
    }

    private void boost() {
        if (!Utils.canUpdate()) return;

        if (mc.player.isFallFlying() && mc.currentScreen == null) {
            ItemStack itemStack = Items.FIREWORK_ROCKET.getDefaultStack();
            itemStack.getOrCreateSubNbt("烟花").putByte("飞行", fireworkLevel.get().byteValue());

            FireworkRocketEntity entity = new FireworkRocketEntity(mc.world, itemStack, mc.player);
            fireworks.add(entity);
            if (playSound.get()) mc.world.playSoundFromEntity(mc.player, entity, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.AMBIENT, 3.0F, 1.0F);
            mc.world.addEntity(entity);
        }
    }

    public boolean isFirework(FireworkRocketEntity firework) {
        return isActive() && fireworks.contains(firework);
    }
}
