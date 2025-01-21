/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractEntityEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Set;

public class NoInteract extends Module {
    private final SettingGroup sgBlocks = settings.createGroup("方块");
    private final SettingGroup sgEntities = settings.createGroup("实体");

    // Blocks

    private final Setting<List<Block>> blockMine = sgBlocks.add(new BlockListSetting.Builder()
        .name("方块挖掘")
        .description("取消方块挖掘。")
        .build()
    );

    private final Setting<ListMode> blockMineMode = sgBlocks.add(new EnumSetting.Builder<ListMode>()
        .name("方块挖掘模式")
        .description("方块挖掘的列表模式。")
        .defaultValue(ListMode.BlackList)
        .build()
    );

    private final Setting<List<Block>> blockInteract = sgBlocks.add(new BlockListSetting.Builder()
        .name("方块交互")
        .description("取消方块交互。")
        .build()
    );

    private final Setting<ListMode> blockInteractMode = sgBlocks.add(new EnumSetting.Builder<ListMode>()
        .name("方块交互模式")
        .description("方块交互的列表模式。")
        .defaultValue(ListMode.BlackList)
        .build()
    );

    private final Setting<HandMode> blockInteractHand = sgBlocks.add(new EnumSetting.Builder<HandMode>()
        .name("方块交互手")
        .description("如果是这只手，取消方块交互。")
        .defaultValue(HandMode.None)
        .build()
    );

    // Entities

    private final Setting<Set<EntityType<?>>> entityHit = sgEntities.add(new EntityTypeListSetting.Builder()
        .name("实体攻击")
        .description("取消实体攻击。")
        .onlyAttackable()
        .build()
    );

    private final Setting<ListMode> entityHitMode = sgEntities.add(new EnumSetting.Builder<ListMode>()
        .name("实体攻击模式")
        .description("实体攻击的列表模式。")
        .defaultValue(ListMode.BlackList)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entityInteract = sgEntities.add(new EntityTypeListSetting.Builder()
        .name("实体交互")
        .description("取消实体交互。")
        .onlyAttackable()
        .build()
    );

    private final Setting<ListMode> entityInteractMode = sgEntities.add(new EnumSetting.Builder<ListMode>()
        .name("实体交互模式")
        .description("实体交互的列表模式。")
        .defaultValue(ListMode.BlackList)
        .build()
    );

    private final Setting<HandMode> entityInteractHand = sgEntities.add(new EnumSetting.Builder<HandMode>()
        .name("实体交互手")
        .description("如果是这只手，取消实体交互。")
        .defaultValue(HandMode.None)
        .build()
    );

    private final Setting<InteractMode> friends = sgEntities.add(new EnumSetting.Builder<InteractMode>()
        .name("好友")
        .description("好友取消模式。")
        .defaultValue(InteractMode.None)
        .build()
    );

    private final Setting<InteractMode> babies = sgEntities.add(new EnumSetting.Builder<InteractMode>()
        .name("婴儿")
        .description("婴儿实体取消模式。")
        .defaultValue(InteractMode.None)
        .build()
    );

    private final Setting<InteractMode> nametagged = sgEntities.add(new EnumSetting.Builder<InteractMode>()
        .name("命名")
        .description("命名实体取消模式。")
        .defaultValue(InteractMode.None)
        .build()
    );

    public NoInteract() {
        super(Categories.Player, "无交互", "阻止你与某些类型的输入交互。");
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onStartBreakingBlockEvent(StartBreakingBlockEvent event) {
        if (!shouldAttackBlock(event.blockPos)) event.cancel();
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (!shouldInteractBlock(event.result, event.hand)) event.cancel();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onAttackEntity(AttackEntityEvent event) {
        if (!shouldAttackEntity(event.entity)) event.cancel();
    }

    @EventHandler
    private void onInteractEntity(InteractEntityEvent event) {
       if (!shouldInteractEntity(event.entity, event.hand)) event.cancel();
    }

    private boolean shouldAttackBlock(BlockPos blockPos) {
        if (blockMineMode.get() == ListMode.WhiteList &&
            blockMine.get().contains(mc.world.getBlockState(blockPos).getBlock())) {
            return false;
        }

        return blockMineMode.get() != ListMode.BlackList ||
            !blockMine.get().contains(mc.world.getBlockState(blockPos).getBlock());
    }

    private boolean shouldInteractBlock(BlockHitResult hitResult, Hand hand) {
        // Hand Interactions
        if (blockInteractHand.get() == HandMode.Both ||
            (blockInteractHand.get() == HandMode.Mainhand && hand == Hand.MAIN_HAND) ||
            (blockInteractHand.get() == HandMode.Offhand && hand == Hand.OFF_HAND)) {
            return false;
        }

        // Blocks
        if (blockInteractMode.get() == ListMode.BlackList &&
            blockInteract.get().contains(mc.world.getBlockState(hitResult.getBlockPos()).getBlock())) {
            return false;
        }

        return blockInteractMode.get() != ListMode.WhiteList ||
            blockInteract.get().contains(mc.world.getBlockState(hitResult.getBlockPos()).getBlock());
    }

    private boolean shouldAttackEntity(Entity entity) {
        // Friends
        if ((friends.get() == InteractMode.Both || friends.get() == InteractMode.Hit) &&
            entity instanceof PlayerEntity && !Friends.get().shouldAttack((PlayerEntity) entity)) {
            return false;
        }

        // Babies
        if ((babies.get() == InteractMode.Both || babies.get() == InteractMode.Hit) &&
            entity instanceof AnimalEntity && ((AnimalEntity) entity).isBaby()) {
            return false;
        }

        // NameTagged
        if ((nametagged.get() == InteractMode.Both || nametagged.get() == InteractMode.Hit) && entity.hasCustomName()) return false;

        // Entities
        if (entityHitMode.get() == ListMode.BlackList &&
            entityHit.get().contains(entity.getType())) {
            return false;
        }

        else return entityHitMode.get() != ListMode.WhiteList ||
            entityHit.get().contains(entity.getType());
    }

    private boolean shouldInteractEntity(Entity entity, Hand hand) {
        // Hand Interactions
        if (entityInteractHand.get() == HandMode.Both ||
            (entityInteractHand.get() == HandMode.Mainhand && hand == Hand.MAIN_HAND) ||
            (entityInteractHand.get() == HandMode.Offhand && hand == Hand.OFF_HAND)) {
            return false;
        }

        // Friends
        if ((friends.get() == InteractMode.Both || friends.get() == InteractMode.Interact) &&
            entity instanceof PlayerEntity && !Friends.get().shouldAttack((PlayerEntity) entity)) {
            return false;
        }

        // Babies
        if ((babies.get() == InteractMode.Both || babies.get() == InteractMode.Interact) &&
            entity instanceof AnimalEntity && ((AnimalEntity) entity).isBaby()) {
            return false;
        }

        // NameTagged
        if ((nametagged.get() == InteractMode.Both || nametagged.get() == InteractMode.Interact) && entity.hasCustomName()) return false;

        // Entities
        if (entityInteractMode.get() == ListMode.BlackList &&
            entityInteract.get().contains(entity.getType())) {
            return false;
        }
        else return entityInteractMode.get() != ListMode.WhiteList ||
            entityInteract.get().contains(entity.getType());
    }

    public enum HandMode {
        Mainhand,
        Offhand,
        Both,
        None
    }

    public enum ListMode {
        WhiteList,
        BlackList
    }

    public enum InteractMode {
        Hit,
        Interact,
        Both,
        None
    }
}
