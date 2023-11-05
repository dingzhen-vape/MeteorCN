/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.AbstractBlockAccessor;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.potion.PotionUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

public class Quiver extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSafety = settings.createGroup("安全");


    private final Setting<List<StatusEffect>> effects = sgGeneral.add(new StatusEffectListSetting.Builder()
        .name("效果")
        .description("使用哪种效果对你进行射击。")
        .defaultValue(StatusEffects.STRENGTH)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("冷却时间")
        .description("射击效果之间间隔多少时间(NCP 最少 19 个)。")
        .defaultValue(10)
        .range(0,40)
        .sliderRange(0,40)
        .build()
    );

    private final Setting<Boolean> checkEffects = sgGeneral.add(new BoolSetting.Builder()
        .name("检查效果")
        .description("不会射击你已经拥有的效果。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> silentBow = sgGeneral.add(new BoolSetting.Builder()
        .name("沉默弓")
        .description("从你的库存中取出弓来箭袋。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatInfo = sgGeneral.add(new BoolSetting.Builder()
        .name("聊天信息")
        .description("在聊天中发送有关箭袋检查的信息。")
        .defaultValue(false)
        .build()
    );

    // Safety

    private final Setting<Boolean> onlyInHoles = sgSafety.add(new BoolSetting.Builder()
        .name("only-in-holes ")
        .description("仅当你在洞里时才会颤抖。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgSafety.add(new BoolSetting.Builder()
        .name("仅在地面上")
        .description("仅当你在地面上时才会颤抖。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> minHealth = sgSafety.add(new DoubleSetting.Builder()
        .name("min-health")
        .description("你必须有多少生命值颤抖。")
        .defaultValue(10)
        .range(0,36)
        .sliderRange(0,36)
        .build()
    );

    private final List<Integer> arrowSlots = new ArrayList<>();
    private FindItemResult bow;
    private boolean wasMainhand, wasHotbar;
    private int timer, prevSlot;
    private final BlockPos.Mutable testPos = new BlockPos.Mutable();

    public Quiver() {
        super(Categories.Combat, "颤抖", "向自己射箭。");
    }

    @Override
    public void onActivate() {
        bow = InvUtils.find(Items.BOW);
        if (!shouldQuiver()) return;

        mc.options.useKey.setPressed(false);
        mc.interactionManager.stopUsingItem(mc.player);

        prevSlot = bow.slot();
        wasHotbar = bow.isHotbar();
        timer = 0;

        if (!bow.isMainHand()) {
            if (wasHotbar) InvUtils.swap(bow.slot(), true);
            else InvUtils.move().from(mc.player.getInventory().selectedSlot).to(prevSlot);
        } else wasMainhand = true;

        arrowSlots.clear();
        List<StatusEffect> usedEffects = new ArrayList<>();

        for (int i = mc.player.getInventory().size(); i > 0; i--) {
            if (i == mc.player.getInventory().selectedSlot) continue;

            ItemStack item = mc.player.getInventory().getStack(i);

            if (item.getItem() != Items.TIPPED_ARROW)  continue;

            List<StatusEffectInstance> effects = PotionUtil.getPotionEffects(item);

            if (effects.isEmpty()) continue;

            StatusEffect effect = effects.get(0).getEffectType();

            if (this.effects.get().contains(effect)
                && !usedEffects.contains(effect)
                && (!hasEffect(effect) || !checkEffects.get())) {
                usedEffects.add(effect);
                arrowSlots.add(i);
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (!wasMainhand) {
            if (wasHotbar) InvUtils.swapBack();
            else InvUtils.move().from(mc.player.getInventory().selectedSlot).to(prevSlot);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        bow = InvUtils.find(Items.BOW);
        if (!shouldQuiver()) return;
        if (arrowSlots.isEmpty()) {
            toggle();
            return;
        }

        if (timer > 0) {
            timer--;
            return;
        }

        boolean charging = mc.options.useKey.isPressed();

        if (!charging) {
            InvUtils.move().from(arrowSlots.get(0)).to(9);
            mc.options.useKey.setPressed(true);
        } else {
            if (BowItem.getPullProgress(mc.player.getItemUseTime()) >= 0.12) {
                int targetSlot = arrowSlots.get(0);
                arrowSlots.remove(0);

                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(), -90, mc.player.isOnGround()));
                mc.options.useKey.setPressed(false);
                mc.interactionManager.stopUsingItem(mc.player);
                if (targetSlot != 9) InvUtils.move().from(9).to(targetSlot);

                timer = cooldown.get();
            }
        }
    }

    private boolean shouldQuiver() {
        if (!bow.found() || !bow.isHotbar() && !silentBow.get()) {
            if (chatInfo.get()) error("找不到可用的弓,致残。");
            toggle();
            return false;
        }

        if (!headIsOpen()) {
            if (chatInfo.get()) error("没有足够的空间颤抖,致残。");
            toggle();
            return false;
        }

        if (EntityUtils.getTotalHealth(mc.player) < minHealth.get()) {
            if (chatInfo.get()) error("生命值不足,无法颤抖,致残。");
            toggle();
            return false;
        }

        if (onlyOnGround.get() && !mc.player.isOnGround()) {
            if (chatInfo.get()) error("你不在地面上,致残。");
            toggle();
            return false;
        }

        if (onlyInHoles.get() && !isSurrounded(mc.player)) {
            if (chatInfo.get()) error("你不在洞里,致残。");
            toggle();
            return false;
        }

        return true;
    }

    private boolean headIsOpen() {
        testPos.set(mc.player.getBlockPos().add(0, 1, 0));
        BlockState pos1 = mc.world.getBlockState(testPos);
        if (((AbstractBlockAccessor) pos1.getBlock()).isCollidable())  return false;

        testPos.add(0, 1, 0);
        BlockState pos2 = mc.world.getBlockState(testPos);
        return !((AbstractBlockAccessor) pos2.getBlock()).isCollidable();
    }

    private boolean hasEffect(StatusEffect effect) {
        for (StatusEffectInstance statusEffect : mc.player.getStatusEffects()) {
            if (statusEffect.getEffectType() == effect) return true;
        }

        return false;
    }

    private boolean isSurrounded(PlayerEntity target) {
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;

            testPos.set(target.getBlockPos()).offset(dir);
            Block block = mc.world.getBlockState(testPos).getBlock();

            if (block != Blocks.OBSIDIAN && block != Blocks.BEDROCK && block != Blocks.RESPAWN_ANCHOR
                && block != Blocks.CRYING_OBSIDIAN && block != Blocks.NETHERITE_BLOCK) {
                return false;
            }
        }

        return true;
    }
}
