/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import java.util.Set;

public class AutoNametag extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("实体")
        .description("要命名的实体。")
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("范围")
        .description("实体可以被命名的最大范围。")
        .defaultValue(5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("优先级")
        .description("优先级排序")
        .defaultValue(SortPriority.LowestDistance)
        .build()
    );

    private final Setting<Boolean> renametag = sgGeneral.add(new BoolSetting.Builder()
        .name("重命名")
        .description("允许已经命名的实体被重命名。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("旋转")
        .description("自动面向被命名的实体。")
        .defaultValue(true)
        .build()
    );

    private Entity target;
    private boolean offHand;

    public AutoNametag() {
        super(Categories.World, "自动命名", "自动对没有命名的实体使用命名牌。将命名指定距离内的所有实体。");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Nametag in hobar
        FindItemResult findNametag = InvUtils.findInHotbar(Items.NAME_TAG);

        if (!findNametag.found()) {
            error("热栏中没有命名牌");
            toggle();
            return;
        }


        // Target
        target = TargetUtils.get(entity -> {
            if (!PlayerUtils.isWithin(entity, range.get())) return false;
            if (!entities.get().contains(entity.getType())) return false;
            if (entity.hasCustomName()) {
                return renametag.get() && !entity.getCustomName().equals(mc.player.getInventory().getStack(findNametag.slot()).getName());
            }
            return true;
        }, priority.get());

        if (target == null) return;


        // Swapping slots
        InvUtils.swap(findNametag.slot(), true);

        offHand = findNametag.isOffhand();


        // Interaction
        if (rotate.get()) Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), -100, this::interact);
        else interact();
    }

    private void interact() {
        mc.interactionManager.interactEntity(mc.player, target, offHand ? Hand.OFF_HAND : Hand.MAIN_HAND);
        InvUtils.swapBack();
    }
}
