/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;


import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Xray;
import meteordevelopment.meteorclient.systems.modules.world.InfinityMiner;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.*;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.*;

import java.util.List;
import java.util.function.Predicate;

public class AutoTool extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWhitelist = settings.createGroup("白名单");

    // General

    private final Setting<EnchantPreference> prefer = sgGeneral.add(new EnumSetting.Builder<EnchantPreference>()
        .name("偏好")
        .description("选择偏好精准采集、时运、或者无。")
        .defaultValue(EnchantPreference.Fortune)
        .build()
    );

    private final Setting<Boolean> silkTouchForEnderChest = sgGeneral.add(new BoolSetting.Builder()
        .name("精准采集末影箱")
        .description("只用精准采集附魔来挖末影箱。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fortuneForOresCrops = sgGeneral.add(new BoolSetting.Builder()
        .name("时运矿石和作物")
        .description("只用时运附魔来挖矿石和作物。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> antiBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("防止损坏")
        .description("阻止你损坏你的工具。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> breakDurability = sgGeneral.add(new IntSetting.Builder()
        .name("防止损坏百分比")
        .description("停止使用工具的耐久度百分比。")
        .defaultValue(10)
        .range(1, 100)
        .sliderRange(1, 100)
        .visible(antiBreak::get)
        .build()
    );

    private final Setting<Boolean> switchBack = sgGeneral.add(new BoolSetting.Builder()
        .name("切换回去")
        .description("当你松开攻击键时，切换你的手到之前选择的物品。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> switchDelay = sgGeneral.add((new IntSetting.Builder()
        .name("切换延迟")
        .description("切换工具前的延迟，以刻为单位。")
        .defaultValue(0)
        .build()
    ));

    // Whitelist and blacklist

    private final Setting<ListMode> listMode = sgWhitelist.add(new EnumSetting.Builder<ListMode>()
        .name("列表模式")
        .description("选择模式。")
        .defaultValue(ListMode.Blacklist)
        .build()
    );

    private final Setting<List<Item>> whitelist = sgWhitelist.add(new ItemListSetting.Builder()
        .name("白名单")
        .description("你想要使用的工具。")
        .visible(() -> listMode.get() == ListMode.Whitelist)
        .filter(AutoTool::isTool)
        .build()
    );

    private final Setting<List<Item>> blacklist = sgWhitelist.add(new ItemListSetting.Builder()
        .name("黑名单")
        .description("你不想要使用的工具。")
        .visible(() -> listMode.get() == ListMode.Blacklist)
        .filter(AutoTool::isTool)
        .build()
    );

    private boolean wasPressed;
    private boolean shouldSwitch;
    private int ticks;
    private int bestSlot;

    public AutoTool() {
        super(Categories.Player, "自动工具", "当执行一个动作时，自动切换到最有效的工具。");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (Modules.get().isActive(InfinityMiner.class)) return;

        if (switchBack.get() && !mc.options.attackKey.isPressed() && wasPressed && InvUtils.previousSlot != -1) {
            InvUtils.swapBack();
            wasPressed = false;
            return;
        }

        if (ticks <= 0 && shouldSwitch && bestSlot != -1) {
            InvUtils.swap(bestSlot, switchBack.get());
            shouldSwitch = false;
        } else {
            ticks--;
        }

        wasPressed = mc.options.attackKey.isPressed();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (Modules.get().isActive(InfinityMiner.class)) return;

        // Get blockState
        BlockState blockState = mc.world.getBlockState(event.blockPos);
        if (!BlockUtils.canBreak(event.blockPos, blockState)) return;

        // Check if we should switch to a better tool
        ItemStack currentStack = mc.player.getMainHandStack();

        double bestScore = -1;
        bestSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);

            if (listMode.get() == ListMode.Whitelist && !whitelist.get().contains(itemStack.getItem())) continue;
            if (listMode.get() == ListMode.Blacklist && blacklist.get().contains(itemStack.getItem())) continue;

            double score = getScore(itemStack, blockState, silkTouchForEnderChest.get(), fortuneForOresCrops.get(), prefer.get(), itemStack2 -> !shouldStopUsing(itemStack2));
            if (score < 0) continue;

            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        if ((bestSlot != -1 && (bestScore > getScore(currentStack, blockState, silkTouchForEnderChest.get(), fortuneForOresCrops.get(), prefer.get(), itemStack -> !shouldStopUsing(itemStack))) || shouldStopUsing(currentStack) || !isTool(currentStack))) {
            ticks = switchDelay.get();

            if (ticks == 0) InvUtils.swap(bestSlot, true);
            else shouldSwitch = true;
        }

        // Anti break
        currentStack = mc.player.getMainHandStack();

        if (shouldStopUsing(currentStack) && isTool(currentStack)) {
            mc.options.attackKey.setPressed(false);
            event.cancel();
        }
    }

    private boolean shouldStopUsing(ItemStack itemStack) {
        return antiBreak.get() && (itemStack.getMaxDamage() - itemStack.getDamage()) < (itemStack.getMaxDamage() * breakDurability.get() / 100);
    }

    public static double getScore(ItemStack itemStack, BlockState state, boolean silkTouchEnderChest, boolean fortuneOre, EnchantPreference enchantPreference, Predicate<ItemStack> good) {
        if (!good.test(itemStack) || !isTool(itemStack)) return -1;
        if (!itemStack.isSuitableFor(state) && !(itemStack.getItem() instanceof SwordItem && (state.getBlock() instanceof BambooBlock || state.getBlock() instanceof BambooSaplingBlock))) return -1;

        if (silkTouchEnderChest
            && state.getBlock() == Blocks.ENDER_CHEST
            && EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, itemStack) == 0) {
            return -1;
        }

        if (fortuneOre
            && isFortunable(state.getBlock())
            && EnchantmentHelper.getLevel(Enchantments.FORTUNE, itemStack) == 0) {
            return -1;
        }

        double score = 0;

        score += itemStack.getMiningSpeedMultiplier(state) * 1000;
        score += EnchantmentHelper.getLevel(Enchantments.UNBREAKING, itemStack);
        score += EnchantmentHelper.getLevel(Enchantments.EFFICIENCY, itemStack);
        score += EnchantmentHelper.getLevel(Enchantments.MENDING, itemStack);

        if (enchantPreference == EnchantPreference.Fortune) score += EnchantmentHelper.getLevel(Enchantments.FORTUNE, itemStack);
        if (enchantPreference == EnchantPreference.SilkTouch) score += EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, itemStack);

        if (itemStack.getItem() instanceof SwordItem item && (state.getBlock() instanceof BambooBlock || state.getBlock() instanceof BambooSaplingBlock))
            score += 9000 + (item.getMaterial().getMiningLevel() * 1000);


        return score;
    }

    public static boolean isTool(Item item) {
        return item instanceof ToolItem || item instanceof ShearsItem;
    }
    public static boolean isTool(ItemStack itemStack) {
        return isTool(itemStack.getItem());
    }

    private static boolean isFortunable(Block block) {
        if (block == Blocks.ANCIENT_DEBRIS) return false;
        return Xray.ORES.contains(block) || block instanceof CropBlock;
    }

    public enum EnchantPreference {
        None,
        Fortune,
        SilkTouch
    }

    public enum ListMode {
        Whitelist,
        Blacklist
    }
}
