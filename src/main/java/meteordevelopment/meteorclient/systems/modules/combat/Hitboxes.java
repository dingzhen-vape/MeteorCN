/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.SwordItem;

import java.util.Set;

public class Hitboxes extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("实体")
        .description("要目标的实体。")
        .defaultValue(EntityType.PLAYER)
        .build()
    );

    private final Setting<Double> value = sgGeneral.add(new DoubleSetting.Builder()
        .name("扩展")
        .description("要扩展的实体的碰撞箱的大小。")
        .defaultValue(0.5)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("忽略好友")
        .description("不扩展好友的碰撞箱。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyOnWeapon = sgGeneral.add(new BoolSetting.Builder()
        .name("仅在武器")
        .description("只在手持武器时修改碰撞箱。")
        .defaultValue(false)
        .build()
    );

    public Hitboxes() {
        super(Categories.Combat, "碰撞箱", "扩展实体的碰撞箱。");
    }

    public double getEntityValue(Entity entity) {
        if (!(isActive() && testWeapon()) || (ignoreFriends.get() && entity instanceof PlayerEntity && Friends.get().isFriend((PlayerEntity) entity))) return 0;
        if (entities.get().contains(entity.getType())) return value.get();
        return 0;
    }

    private boolean testWeapon() {
        if (!onlyOnWeapon.get()) return true;
        return InvUtils.testInHands(itemStack -> itemStack.getItem() instanceof SwordItem || itemStack.getItem() instanceof AxeItem);
    }
}
