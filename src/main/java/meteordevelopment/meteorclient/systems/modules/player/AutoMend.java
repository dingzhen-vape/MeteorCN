/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;

public class AutoMend extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Item>> blacklist = sgGeneral.add(new ItemListSetting.Builder()
        .name("黑名单")
        .description("物品黑名单。")
        .filter(Item::isDamageable)
        .build()
    );

    private final Setting<Boolean> force = sgGeneral.add(new BoolSetting.Builder()
        .name("强制")
        .description("即使副手中有其他不可修复的物品，也替换副手中的物品。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("自动关闭")
        .description("当没有更多要修复的物品时自动关闭。")
        .defaultValue(true)
        .build()
    );

    private boolean didMove;

    public AutoMend() {
        super(Categories.Player, "自动修复", "当完全修复时，自动用有经验修补附魔的物品替换副手中的物品。");
    }

    @Override
    public void onActivate() {
        didMove = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (shouldWait()) return;

        int slot = getSlot();

        if (slot == -1) {
            if (autoDisable.get()) {
                info("修复了所有物品，关闭中");

                if (didMove) {
                    int emptySlot = getEmptySlot();
                    InvUtils.move().fromOffhand().to(emptySlot);
                }

                toggle();
            }
        }
        else {
            InvUtils.move().from(slot).toOffhand();
            didMove = true;
        }
    }

    private boolean shouldWait() {
        ItemStack itemStack = mc.player.getOffHandStack();

        if (itemStack.isEmpty()) return false;

        if (EnchantmentHelper.getLevel(Enchantments.MENDING, itemStack) > 0) {
            return itemStack.getDamage() != 0;
        }

        return !force.get();
    }

    private int getSlot() {
        for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (blacklist.get().contains(itemStack.getItem())) continue;

            if (EnchantmentHelper.getLevel(Enchantments.MENDING, itemStack) > 0 && itemStack.getDamage() > 0) {
                return i;
            }
        }

        return -1;
    }

    private int getEmptySlot() {
        for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }

        return -1;
    }
}
