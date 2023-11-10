/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.process.IMineProcess;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.function.Predicate;

public class InfinityMiner extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWhenFull = settings.createGroup("当满时");

    // General

    public final Setting<List<Block>> targetBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("目标方块")
        .description("要挖掘的目标方块。")
        .defaultValue(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE)
        .filter(this::filterBlocks)
        .build()
    );

    public final Setting<List<Item>> targetItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("目标物品")
        .description("要收集的目标物品。")
        .defaultValue(Items.DIAMOND)
        .build()
    );

    public final Setting<List<Block>> repairBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("修复方块")
        .description("要挖掘的修复方块。")
        .defaultValue(Blocks.COAL_ORE, Blocks.REDSTONE_ORE, Blocks.NETHER_QUARTZ_ORE)
        .filter(this::filterBlocks)
        .build()
    );

    public final Setting<Double> startRepairing = sgGeneral.add(new DoubleSetting.Builder()
        .name("修复阈值")
        .description("开始修复的耐久度百分比。")
        .defaultValue(20)
        .range(1, 99)
        .sliderRange(1, 99)
        .build()
    );

    public final Setting<Double> startMining = sgGeneral.add(new DoubleSetting.Builder()
        .name("挖掘阈值")
        .description("开始挖掘的耐久度百分比。")
        .defaultValue(70)
        .range(1, 99)
        .sliderRange(1, 99)
        .build()
    );

    // When Full

    public final Setting<Boolean> walkHome = sgWhenFull.add(new BoolSetting.Builder()
        .name("走回家")
        .description("当你的背包满了时，会走回“家”。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> logOut = sgWhenFull.add(new BoolSetting.Builder()
        .name("登出")
        .description("当你的背包满了时，会登出。如果启用了走回家，会先走回家再登出。")
        .defaultValue(false)
        .build()
    );

    private final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
    private final Settings baritoneSettings = BaritoneAPI.getSettings();

    private final BlockPos.Mutable homePos = new BlockPos.Mutable();

    private boolean prevMineScanDroppedItems;
    private boolean repairing;

    public InfinityMiner() {
        super(Categories.World, "无限矿工", "通过在耐久度低时挖掘修复方块，让你可以无限地挖掘。需要一个有经验修补附魔的镐子。");
    }

    @Override
    public void onActivate() {
        prevMineScanDroppedItems = baritoneSettings.mineScanDroppedItems.value;
        baritoneSettings.mineScanDroppedItems.value = true;
        homePos.set(mc.player.getBlockPos());
        repairing = false;
    }

    @Override
    public void onDeactivate() {
        baritone.getPathingBehavior().cancelEverything();
        baritoneSettings.mineScanDroppedItems.value = prevMineScanDroppedItems;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (isFull()) {
            if (walkHome.get()) {
                if (isBaritoneNotWalking()) {
                    info("走回家中。");
                    baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(homePos));
                }
                else if (mc.player.getBlockPos().equals(homePos) && logOut.get()) logOut();
            }
            else if (logOut.get()) logOut();
            else {
                info("背包满了，停止进程。");
                toggle();
            }

            return;
        }

        if (!findPickaxe()) {
            error("找不到可用的有经验修补附魔的镐子。");
            toggle();
            return;
        }

        if (!checkThresholds()) {
            error("开始挖掘的值不能低于开始修复的值。");
            toggle();
            return;
        }

        if (repairing) {
            if (!needsRepair()) {
                warning("修复完成，回到挖掘。");
                repairing = false;
                mineTargetBlocks();
                return;
            }

            if (isBaritoneNotMining()) mineRepairBlocks();
        }
        else {
            if (needsRepair()) {
                warning("镐子需要修复，开始修复进程");
                repairing = true;
                mineRepairBlocks();
                return;
            }

            if (isBaritoneNotMining()) mineTargetBlocks();
        }
    }

    private boolean needsRepair() {
        ItemStack itemStack = mc.player.getMainHandStack();
        double toolPercentage = ((itemStack.getMaxDamage() - itemStack.getDamage()) * 100f) / (float) itemStack.getMaxDamage();
        return !(toolPercentage > startMining.get() || (toolPercentage > startRepairing.get() && !repairing));
    }

    private boolean findPickaxe() {
        Predicate<ItemStack> pickaxePredicate = (stack -> stack.getItem() instanceof PickaxeItem
            && Utils.hasEnchantments(stack, Enchantments.MENDING)
            && !Utils.hasEnchantments(stack, Enchantments.SILK_TOUCH));
        FindItemResult bestPick = InvUtils.findInHotbar(pickaxePredicate);

        if (bestPick.isOffhand()) InvUtils.shiftClick().fromOffhand().toHotbar(mc.player.getInventory().selectedSlot);
        else if (bestPick.isHotbar()) InvUtils.swap(bestPick.slot(), false);

        return InvUtils.testInMainHand(pickaxePredicate);
    }

    private boolean checkThresholds() {
        return startRepairing.get() < startMining.get();
    }

    private void mineTargetBlocks() {
        Block[] array = new Block[targetBlocks.get().size()];

        baritone.getPathingBehavior().cancelEverything();
        baritone.getMineProcess().mine(targetBlocks.get().toArray(array));
    }

    private void mineRepairBlocks() {
        Block[] array = new Block[repairBlocks.get().size()];

        baritone.getPathingBehavior().cancelEverything();
        baritone.getMineProcess().mine(repairBlocks.get().toArray(array));
    }

    private void logOut() {
        toggle();
        mc.player.networkHandler.sendPacket(new DisconnectS2CPacket(Text.literal("[无限矿工] 背包满了。")));
    }

    private boolean isBaritoneNotMining() {
        return !(baritone.getPathingControlManager().mostRecentInControl().orElse(null) instanceof IMineProcess);
    }

    private boolean isBaritoneNotWalking() {
        return !(baritone.getPathingControlManager().mostRecentInControl().orElse(null) instanceof ICustomGoalProcess);
    }

    private boolean filterBlocks(Block block) {
        return block != Blocks.AIR && block.getDefaultState().getHardness(mc.world, null) != -1 && !(block instanceof FluidBlock);
    }

    private boolean isFull() {
        for (int i = 0; i <= 35; i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (itemStack.isEmpty()) return false;

            for (Item item : targetItems.get()) {
                if (itemStack.getItem() == item && itemStack.getCount() < itemStack.getMaxCount()) {
                    return false;
                }
            }
        }

        return true;
    }
}
