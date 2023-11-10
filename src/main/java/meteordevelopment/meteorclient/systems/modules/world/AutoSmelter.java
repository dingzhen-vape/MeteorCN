/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.mixininterface.IAbstractFurnaceScreenHandler;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.AbstractFurnaceScreenHandler;

import java.util.List;
import java.util.Map;

public class AutoSmelter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Item>> fuelItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("燃料物品")
        .description("用作燃料的物品")
        .defaultValue(Items.COAL, Items.CHARCOAL)
        .filter(this::fuelItemFilter)
        .bypassFilterWhenSavingAndLoading()
        .build()
    );

    private final Setting<List<Item>> smeltableItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("可熔炼物品")
        .description("要熔炼的物品")
        .defaultValue(Items.IRON_ORE, Items.GOLD_ORE, Items.COPPER_ORE, Items.RAW_IRON, Items.RAW_COPPER, Items.RAW_GOLD)
        .filter(this::smeltableItemFilter)
        .bypassFilterWhenSavingAndLoading()
        .build()
    );

    private final Setting<Boolean> disableWhenOutOfItems = sgGeneral.add(new BoolSetting.Builder()
        .name("当物品用完时禁用")
        .description("当你的物品用完时禁用模块")
        .defaultValue(true)
        .build()
    );

    private Map<Item, Integer> fuelTimeMap;

    public AutoSmelter() {
        super(Categories.World, "自动熔炼器", "自动从你的背包中熔炼物品");
    }

    private boolean fuelItemFilter(Item item) {
        if (!Utils.canUpdate() && fuelTimeMap == null) return false;

        if (fuelTimeMap == null) fuelTimeMap = AbstractFurnaceBlockEntity.createFuelTimeMap();
        return fuelTimeMap.containsKey(item);
    }

    private boolean smeltableItemFilter(Item item) {
        return mc.world != null && mc.world.getRecipeManager().getFirstMatch(RecipeType.SMELTING, new SimpleInventory(item.getDefaultStack()), mc.world).isPresent();
    }

    public void tick(AbstractFurnaceScreenHandler c) {
        // Limit actions to happen every n ticks
        if (mc.player.age % 10 == 0) return;

        // Check for fuel
        checkFuel(c);

        // Take the smelted results
        takeResults(c);

        // Insert new items
        insertItems(c);
    }

    private void insertItems(AbstractFurnaceScreenHandler c) {
        ItemStack inputItemStack = c.slots.get(0).getStack();
        if (!inputItemStack.isEmpty()) return;

        int slot = -1;

        for (int i = 3; i < c.slots.size(); i++) {
            ItemStack item = c.slots.get(i).getStack();
            if (!((IAbstractFurnaceScreenHandler) c).isItemSmeltable(item)) continue;
            if (!smeltableItems.get().contains(item.getItem())) continue;
            if (!smeltableItemFilter(item.getItem())) continue;

            slot = i;
            break;
        }

        if (disableWhenOutOfItems.get() && slot == -1) {
            error("你的背包里没有可以熔炼的物品。禁用。");
            toggle();
            return;
        }

        InvUtils.move().fromId(slot).toId(0);
    }

    private void checkFuel(AbstractFurnaceScreenHandler c) {
        ItemStack fuelStack = c.slots.get(1).getStack();

        if (c.getFuelProgress() > 0) return;
        if (!fuelStack.isEmpty()) return;

        int slot = -1;
        for (int i = 3; i < c.slots.size(); i++) {
            ItemStack item = c.slots.get(i).getStack();
            if (!fuelItems.get().contains(item.getItem())) continue;
            if (!fuelItemFilter(item.getItem())) continue;

            slot = i;
            break;
        }

        if (disableWhenOutOfItems.get() && slot == -1) {
            error("你的背包里没有燃料。禁用。");
            toggle();
            return;
        }

        InvUtils.move().fromId(slot).toId(1);
    }

    private void takeResults(AbstractFurnaceScreenHandler c) {
        ItemStack resultStack = c.slots.get(2).getStack();
        if (resultStack.isEmpty()) return;

        InvUtils.shiftClick().slotId(2);

        if (!resultStack.isEmpty()) {
            error("你的背包已满。禁用。");
            toggle();
        }
    }
}
