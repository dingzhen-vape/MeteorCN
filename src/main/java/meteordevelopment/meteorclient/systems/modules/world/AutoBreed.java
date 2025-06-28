/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AutoBreed extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("实体")
        .description("要繁殖的实体。")
        .defaultValue(EntityType.HORSE, EntityType.DONKEY, EntityType.COW,
            EntityType.MOOSHROOM, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN, EntityType.WOLF,
            EntityType.CAT, EntityType.OCELOT, EntityType.RABBIT, EntityType.LLAMA, EntityType.TURTLE,
            EntityType.PANDA, EntityType.FOX, EntityType.BEE, EntityType.STRIDER, EntityType.HOGLIN)
        .onlyAttackable()
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("范围")
        .description("动物可以被繁殖的距离。")
        .min(0)
        .defaultValue(4.5)
        .build()
    );

    private final Setting<Hand> hand = sgGeneral.add(new EnumSetting.Builder<Hand>()
        .name("繁殖用的手")
        .description("用于繁殖的手。")
        .defaultValue(Hand.MAIN_HAND)
        .build()
    );

    private final Setting<EntityAge> mobAgeFilter = sgGeneral.add(new EnumSetting.Builder<EntityAge>()
        .name("忽略幼体")
        .description("是否攻击实体的幼体变种。")
        .defaultValue(EntityAge.Adult)
        .build()
    );

    private final List<Entity> animalsFed = new ArrayList<>();

    public AutoBreed() {
        super(Categories.World, "自动繁殖", "自动繁殖指定的动物。");
    }

    @Override
    public void onActivate() {
        animalsFed.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof AnimalEntity animal)) continue;

            if (!entities.get().contains(animal.getType())
                || !switch (mobAgeFilter.get()) {
                case Baby -> animal.isBaby();
                case Adult -> !animal.isBaby();
                case Both -> true;
            }
                || animalsFed.contains(animal)
                || !PlayerUtils.isWithin(animal, range.get())
                || !animal.isBreedingItem(hand.get() == Hand.MAIN_HAND ? mc.player.getMainHandStack() : mc.player.getOffHandStack()))
                continue;

            Rotations.rotate(Rotations.getYaw(entity), Rotations.getPitch(entity), -100, () -> {
                mc.interactionManager.interactEntity(mc.player, animal, hand.get());
                mc.player.swingHand(hand.get());
                animalsFed.add(animal);
            });

            return;
        }
    }

    public enum EntityAge {
        Baby,
        Adult,
        Both
    }
}
