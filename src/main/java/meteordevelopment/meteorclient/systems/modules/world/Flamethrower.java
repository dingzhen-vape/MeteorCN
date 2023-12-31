/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

public class Flamethrower extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
        .name("距离")
        .description("动物要被烤的最大距离。")
        .min(0.0)
        .defaultValue(5.0)
        .build()
    );

    private final Setting<Boolean> antiBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("防止损坏")
        .description("防止打火石被损坏。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> putOutFire = sgGeneral.add(new BoolSetting.Builder()
        .name("扑灭火焰")
        .description("尝试在动物血量低时扑灭火焰，这样物品就不会烧掉。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> targetBabies = sgGeneral.add(new BoolSetting.Builder()
        .name("目标幼体")
        .description("如果勾选，幼体也会被杀死。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> tickInterval = sgGeneral.add(new IntSetting.Builder()
        .name("间隔刻")
        .defaultValue(5)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("旋转")
        .description("自动面向被烤的动物。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("实体")
        .description("要烹饪的实体。")
        .defaultValue(
            EntityType.PIG,
            EntityType.COW,
            EntityType.SHEEP,
            EntityType.CHICKEN,
            EntityType.RABBIT
        )
        .build()
    );

    private Entity entity;
    private int ticks = 0;
    private Hand hand;

    public Flamethrower() {
        super(Categories.World, "火焰喷射器", "点燃每一只活着的食物。");
    }

    @Override
    public void onDeactivate() {
        entity = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        entity = null;
        ticks++;
        for (Entity entity : mc.world.getEntities()) {
            if (!entities.get().contains(entity.getType()) || !PlayerUtils.isWithin(entity, distance.get())) continue;
            if (entity.isFireImmune()) continue;
            if (entity == mc.player) continue;
            if (!targetBabies.get() && entity instanceof LivingEntity && ((LivingEntity)entity).isBaby()) continue;

            FindItemResult findFlintAndSteel = InvUtils.findInHotbar(itemStack -> itemStack.getItem() == Items.FLINT_AND_STEEL && (!antiBreak.get() || itemStack.getDamage() < itemStack.getMaxDamage() - 1));
            if (!InvUtils.swap(findFlintAndSteel.slot(), true)) return;

            this.hand = findFlintAndSteel.getHand();
            this.entity = entity;

            if (rotate.get()) Rotations.rotate(Rotations.getYaw(entity.getBlockPos()), Rotations.getPitch(entity.getBlockPos()), -100, this::interact);
            else interact();

            return;
        }
    }

    private void interact() {
        Block block = mc.world.getBlockState(entity.getBlockPos()).getBlock();
        Block bottom = mc.world.getBlockState(entity.getBlockPos().down()).getBlock();
        if (block == Blocks.WATER || bottom == Blocks.WATER || bottom == Blocks.DIRT_PATH) return;
        if (block == Blocks.GRASS_BLOCK)  mc.interactionManager.attackBlock(entity.getBlockPos(), Direction.DOWN);

        if (putOutFire.get() && entity instanceof LivingEntity animal && animal.getHealth() < 1) {
            mc.interactionManager.attackBlock(entity.getBlockPos(), Direction.DOWN);
            mc.interactionManager.attackBlock(entity.getBlockPos().west(), Direction.DOWN);
            mc.interactionManager.attackBlock(entity.getBlockPos().east(), Direction.DOWN);
            mc.interactionManager.attackBlock(entity.getBlockPos().north(), Direction.DOWN);
            mc.interactionManager.attackBlock(entity.getBlockPos().south(), Direction.DOWN);
        } else {
            if (ticks >= tickInterval.get() && !entity.isOnFire()) {
                mc.interactionManager.interactBlock(mc.player, hand, new BlockHitResult(
                    entity.getPos().subtract(new Vec3d(0, 1, 0)), Direction.UP, entity.getBlockPos().down(), false));
                ticks = 0;
            }
        }

        InvUtils.swapBack();
    }
}
