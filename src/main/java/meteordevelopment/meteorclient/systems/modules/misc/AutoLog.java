/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;

import java.util.Set;

public class AutoLog extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgEntities = settings.createGroup("实体");

    private final Setting<Integer> health = sgGeneral.add(new IntSetting.Builder()
        .name("生命值")
        .description("当健康值低于或等于此值时自动断开连接。设置为0以禁用。")
        .defaultValue(6)
        .range(0, 19)
        .sliderMax(19)
        .build()
    );

    private final Setting<Boolean> smart = sgGeneral.add(new BoolSetting.Builder()
        .name("预测即将受到的伤害")
        .description("当检测到你即将受到足够的伤害导致健康值低于'health'设置时断开连接。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> totemPops = sgGeneral.add(new IntSetting.Builder()
        .name("图腾弹出")
        .description("Disconnects when you have popped this many totems. Set to 0 to disable.")
        .defaultValue(0)
        .min(0)
        .build()
    );

    private final Setting<Boolean> onlyTrusted = sgGeneral.add(new BoolSetting.Builder()
        .name("仅信任")
        .description("当渲染距离内出现未在朋友列表中的玩家时断开连接。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> instantDeath = sgGeneral.add(new BoolSetting.Builder()
        .name("32K")
        .description("当附近的玩家能够瞬间击杀你时断开连接。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> smartToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("智能切换")
        .description("在低健康退出后禁用自动登录。恢复健康后将重新启用。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> toggleOff = sgGeneral.add(new BoolSetting.Builder()
        .name("切换关闭")
        .description("使用后禁用自动登录。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> toggleAutoReconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("切换自动重连")
        .description("是否在退出后禁用自动重连。")
        .defaultValue(true)
        .build()
    );

    // Entities

    private final Setting<Set<EntityType<?>>> entities = sgEntities.add(new EntityTypeListSetting.Builder()
        .name("实体")
        .description("当指定实体出现在指定范围内时断开连接。")
        .defaultValue(EntityType.END_CRYSTAL)
        .build()
    );

    private final Setting<Boolean> useTotalCount = sgEntities.add(new BoolSetting.Builder()
        .name("使用总计数")
        .description("在统计所有选定实体的总数或每个实体分别计数之间切换。")
        .defaultValue(true)
        .visible(() -> !entities.get().isEmpty())
        .build());

    private final Setting<Integer> combinedEntityThreshold = sgEntities.add(new IntSetting.Builder()
        .name("组合实体阈值")
        .description("在断开连接之前,必须有足够多的选定实体靠近你。")
        .defaultValue(10)
        .min(1)
        .sliderMax(32)
        .visible(() -> useTotalCount.get() && !entities.get().isEmpty())
        .build()
    );

    private final Setting<Integer> individualEntityThreshold = sgEntities.add(new IntSetting.Builder()
        .name("单个实体阈值")
        .description("在断开连接之前,必须有足够多的单个实体靠近你。")
        .defaultValue(2)
        .min(1)
        .sliderMax(16)
        .visible(() -> !useTotalCount.get() && !entities.get().isEmpty())
        .build()
    );

    private final Setting<Integer> range = sgEntities.add(new IntSetting.Builder()
        .name("范围")
        .description("实体必须离你多近才能断开连接。")
        .defaultValue(5)
        .min(1)
        .sliderMax(16)
        .visible(() -> !entities.get().isEmpty())
        .build()
    );

    // Declaring variables outside the loop for better efficiency
    private final Object2IntMap<EntityType<?>> entityCounts = new Object2IntOpenHashMap<>();

    private int pops;

    public AutoLog() {
        super(Categories.Combat, "自动断开", "当满足某些要求时自动断开连接。");
    }

    @Override
    public void onActivate() {
        pops = 0;
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityStatusS2CPacket p)) return;
        if (p.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) return;

        Entity entity = p.getEntity(mc.world);
        if (entity == null || !entity.equals(mc.player)) return;

        pops++;
        if (totemPops.get() > 0 && pops >= totemPops.get()) {
            disconnect("Popped " + pops + " totems.");
            if (toggleOff.get()) this.toggle();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        float playerHealth = mc.player.getHealth();
        if (playerHealth <= 0) {
            this.toggle();
            return;
        }
        if (playerHealth <= health.get()) {
            disconnect("健康低于" + health.get() + ".");
            if (smartToggle.get()) {
                if (isActive()) this.toggle();
                enableHealthListener();
            } else if (toggleOff.get()) this.toggle();
            return;
        }

        if (smart.get() && playerHealth + mc.player.getAbsorptionAmount() - PlayerUtils.possibleHealthReductions() < health.get()) {
            disconnect("健康将低于" + health.get() + ".");
            if (toggleOff.get()) this.toggle();
            return;
        }

        if (!onlyTrusted.get() && !instantDeath.get() && entities.get().isEmpty())
            return; // only check all entities if needed

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity player && player.getUuid() != mc.player.getUuid()) {
                if (onlyTrusted.get() && player != mc.player && !Friends.get().isFriend(player)) {
                    disconnect(Text.literal("非信任玩家 '" + Formatting.RED + player.getName().getString() + Formatting.WHITE + "' 出现在你的渲染距离内。"));
                    if (toggleOff.get()) this.toggle();
                    return;
                }

                if (instantDeath.get() && PlayerUtils.isWithin(entity, 8) && DamageUtils.getAttackDamage(player, mc.player)
                    > playerHealth + mc.player.getAbsorptionAmount()) {
                    disconnect("反32K措施。");
                    if (toggleOff.get()) this.toggle();
                    return;
                }
            }
        }

        // Entities detection Logic
        if (!entities.get().isEmpty()) {
            // Reset totalEntities count and clear the entityCounts map
            int totalEntities = 0;
            entityCounts.clear();

            // Iterate through all entities in the world and count the ones that match the selected types and are within range
            for (Entity entity : mc.world.getEntities()) {
                if (PlayerUtils.isWithin(entity, range.get()) && entities.get().contains(entity.getType())) {
                    totalEntities++;
                    if (!useTotalCount.get()) {
                        entityCounts.put(entity.getType(), entityCounts.getOrDefault(entity.getType(), 0) + 1);
                    }
                }
            }

            if (useTotalCount.get() && totalEntities >= combinedEntityThreshold.get()) {
                disconnect("选定实体的总数超出范围限制。");
                if (toggleOff.get()) this.toggle();
            }
            else if (!useTotalCount.get()) {
                // Check if the count of each entity type exceeds the specified limit
                for (Object2IntMap.Entry<EntityType<?>> entry : entityCounts.object2IntEntrySet()) {
                    if (entry.getIntValue() >= individualEntityThreshold.get()) {
                        disconnect("数量" + entry.getKey().getName().getString() + " 超出范围限制。");
                        if (toggleOff.get()) this.toggle();
                        return;
                    }
                }
            }
        }
    }

    private void disconnect(String reason) {
        disconnect(Text.literal(reason));
    }

    private void disconnect(Text reason) {
        MutableText text = Text.literal("[自动登录] ");
        text.append(reason);

        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect.isActive() && toggleAutoReconnect.get()) {
            text.append(Text.literal("\n\nINFO - 自动重连已禁用").withColor(Colors.GRAY));
            autoReconnect.toggle();
        }

        mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(text));
    }

    private class StaticListener {
        @EventHandler
        private void healthListener(TickEvent.Post event) {
            if (isActive()) disableHealthListener();

            else if (Utils.canUpdate()
                && !mc.player.isDead()
                && mc.player.getHealth() > health.get()) {
                info("玩家健康值高于最低值,重新启用模块。");
                toggle();
                disableHealthListener();
            }
        }
    }

    private final StaticListener staticListener = new StaticListener();

    private void enableHealthListener() {
        MeteorClient.EVENT_BUS.subscribe(staticListener);
    }

    private void disableHealthListener() {
        MeteorClient.EVENT_BUS.unsubscribe(staticListener);
    }
}
