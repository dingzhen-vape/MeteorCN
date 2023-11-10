/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import com.google.common.collect.Streams;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.DamageUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;

import java.util.Comparator;
import java.util.Optional;

public class Step extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Double> height = sgGeneral.add(new DoubleSetting.Builder()
        .name("高度")
        .description("步高。")
        .defaultValue(1)
        .min(0)
        .build()
    );

    private final Setting<ActiveWhen> activeWhen = sgGeneral.add(new EnumSetting.Builder<ActiveWhen>()
        .name("激活条件")
        .description("当你满足这些条件时，步高才会激活。")
        .defaultValue(ActiveWhen.Always)
        .build()
    );

    private final Setting<Boolean> safeStep = sgGeneral.add(new BoolSetting.Builder()
        .name("安全步高")
        .description("如果你的生命值很低或者附近有水晶，就不会让你从洞里走出去。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> stepHealth = sgGeneral.add(new IntSetting.Builder()
        .name("步高生命值")
        .description("你不能步高的生命值。")
        .defaultValue(5)
        .range(1, 36)
        .sliderRange(1, 36)
        .visible(safeStep::get)
        .build()
    );

    private float prevStepHeight;
    private boolean prevPathManagerStep;

    public Step() {
        super(Categories.Movement, "步高", "让你可以立刻走上整个方块。");
    }

    @Override
    public void onActivate() {
        prevStepHeight = mc.player.getStepHeight();

        prevPathManagerStep = PathManagers.get().getSettings().getStep().get();
        PathManagers.get().getSettings().getStep().set(true);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        boolean work = (activeWhen.get() == ActiveWhen.Always) || (activeWhen.get() == ActiveWhen.Sneaking && mc.player.isSneaking()) || (activeWhen.get() == ActiveWhen.NotSneaking && !mc.player.isSneaking());
        mc.player.setBoundingBox(mc.player.getBoundingBox().offset(0, 1, 0));
        if (work && (!safeStep.get() || (getHealth() > stepHealth.get() && getHealth() - getExplosionDamage() > stepHealth.get()))){
            mc.player.setStepHeight(height.get().floatValue());
        } else {
            mc.player.setStepHeight(prevStepHeight);
        }
        mc.player.setBoundingBox(mc.player.getBoundingBox().offset(0, -1, 0));
    }

    @Override
    public void onDeactivate() {
        mc.player.setStepHeight(prevStepHeight);

        PathManagers.get().getSettings().getStep().set(prevPathManagerStep);
    }

    private float getHealth(){
        return mc.player.getHealth() + mc.player.getAbsorptionAmount();
    }

    private double getExplosionDamage(){
        Optional<EndCrystalEntity> crystal = Streams.stream(mc.world.getEntities())
                .filter(entity -> entity instanceof EndCrystalEntity)
                .filter(Entity::isAlive)
                .max(Comparator.comparingDouble(o -> DamageUtils.crystalDamage(mc.player, o.getPos())))
                .map(entity -> (EndCrystalEntity) entity);
        return crystal.map(endCrystalEntity -> DamageUtils.crystalDamage(mc.player, endCrystalEntity.getPos())).orElse(0.0);
    }

    public enum ActiveWhen {
        Always,
        Sneaking,
        NotSneaking
    }
}
