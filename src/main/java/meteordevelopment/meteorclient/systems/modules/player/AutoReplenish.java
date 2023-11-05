/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.ItemStackAccessor;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTotem;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.List;

public class AutoReplenish extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("threshold")
        .description("留下此活动的物品阈值。")
        .defaultValue(8)
        .min(1)
        .sliderRange(1, 63)
        .build()
    );

    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("补充热键栏的滴答延迟。")
        .defaultValue(1)
        .min(0)
        .build()
    );

    private final Setting<Boolean> offhand = sgGeneral.add(new BoolSetting.Builder()
        .name("offhand")
        .description("是否用物品重新填充你的副手。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> unstackable = sgGeneral.add(new BoolSetting.Builder()
        .name(" unstackable")
        .description("补充不可堆叠的物品。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> searchHotbar = sgGeneral.add(new BoolSetting.Builder()
        .name("search-hotbar")
        .description("使用快捷栏中的物品来补充(如果它们是唯一剩下的物品)。")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Item>> excludedItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("exclusion-items")
        .description("不会补充的物品。")
        .build()
    );

    private final ItemStack[] items = new ItemStack[10];
    private boolean prevHadOpenScreen;
    private int tickDelayLeft;

    public AutoReplenish() {
        super(Categories.Player, "自动补充", "自动补充你的快捷栏,主手或副手中的物品。");

        for (int i = 0; i < items.length; i++) items[i] = new ItemStack(Items.AIR);
    }

    @Override
    public void onActivate() {
        fillItems();
        tickDelayLeft = tickDelay.get();
        prevHadOpenScreen = mc.currentScreen != null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.currentScreen == null && prevHadOpenScreen) {
            fillItems();
        }

        prevHadOpenScreen = mc.currentScreen != null;
        if (mc.player.currentScreenHandler.getStacks().size() != 46 || mc.currentScreen != null) return;

        if (tickDelayLeft <= 0) {
            tickDelayLeft = tickDelay.get();

            // Hotbar
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                checkSlot(i, stack);
            }

            // Offhand
            if (offhand.get() && !Modules.get().get(AutoTotem.class).isLocked()) {
                ItemStack stack = mc.player.getOffHandStack();
                checkSlot(SlotUtils.OFFHAND, stack);
            }
        }
        else {
            tickDelayLeft--;
        }
    }

    private void checkSlot(int slot, ItemStack stack) {
        ItemStack prevStack = getItem(slot);

        // Stackable items 1
        if (!stack.isEmpty() && stack.isStackable() && !excludedItems.get().contains(stack.getItem())) {
            if (stack.getCount() <= threshold.get()) {
                addSlots(slot, findItem(stack, slot, threshold.get() - stack.getCount() + 1));
            }
        }

        if (stack.isEmpty() && !prevStack.isEmpty() && !excludedItems.get().contains(prevStack.getItem())) {
            // Stackable items 2
            if (prevStack.isStackable()) {
                addSlots(slot, findItem(prevStack, slot, threshold.get() - stack.getCount() + 1));
            }
            // Unstackable items
            else {
                if (unstackable.get()) {
                    addSlots(slot, findItem(prevStack, slot, 1));
                }
            }
        }

        setItem(slot, stack);
    }

    private int findItem(ItemStack itemStack, int excludedSlot, int goodEnoughCount) {
        int slot = -1;
        int count = 0;

        for (int i = mc.player.getInventory().size() - 2; i >= (searchHotbar.get() ? 0 : 9); i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (i != excludedSlot && stack.getItem() == itemStack.getItem() && ItemStack.canCombine(itemStack, stack)) {
                if (stack.getCount() > count) {
                    slot = i;
                    count = stack.getCount();

                    if (count >= goodEnoughCount) break;
                }
            }
        }

        return slot;
    }

    private void addSlots(int to, int from) {
        InvUtils.move().from(from).to(to);
    }

    private void fillItems() {
        for (int i = 0; i < 9; i++) {
            setItem(i, mc.player.getInventory().getStack(i));
        }

        setItem(SlotUtils.OFFHAND, mc.player.getOffHandStack());
    }

    private ItemStack getItem(int slot) {
        if (slot == SlotUtils.OFFHAND) slot = 9;

        return items[slot];
    }

    private void setItem(int slot, ItemStack stack) {
        if (slot == SlotUtils.OFFHAND) slot = 9;

        ItemStack s = items[slot];
        ((ItemStackAccessor) (Object) s).setItem(stack.getItem());
        s.setCount(stack.getCount());
        s.setNbt(stack.getNbt());
        if (stack.isEmpty()) ((ItemStackAccessor) (Object) s).setItem(Items.AIR);
    }
}
