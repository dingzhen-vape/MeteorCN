/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.DamageUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;

public class AutoLog extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> health = sgGeneral.add(new IntSetting.Builder()
        .name("生命值")
        .description("当生命值低于或等于这个值时自动断开连接。")
        .defaultValue(6)
        .range(0, 19)
        .sliderMax(19)
        .build()
    );

    private final Setting<Boolean> smart = sgGeneral.add(new BoolSetting.Builder()
        .name("智能")
        .description("当你即将受到足以杀死你的伤害时断开连接。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyTrusted = sgGeneral.add(new BoolSetting.Builder()
        .name("仅信任")
        .description("当你的朋友列表之外的玩家出现在渲染距离内时断开连接。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> instantDeath = sgGeneral.add(new BoolSetting.Builder()
        .name("32K")
        .description("当你附近的玩家可以瞬间杀死你时断开连接。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> crystalLog = sgGeneral.add(new BoolSetting.Builder()
        .name("水晶附近")
        .description("当水晶出现在你附近时断开连接。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("范围")
        .description("水晶离你多近时才断开连接。")
        .defaultValue(4)
        .range(1, 10)
        .sliderMax(5)
        .visible(crystalLog::get)
        .build()
    );

    private final Setting<Boolean> smartToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("智能切换")
        .description("在低生命值登出后禁用自动登出。当你恢复后会重新启用。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> toggleOff = sgGeneral.add(new BoolSetting.Builder()
        .name("切换关闭")
        .description("使用后禁用自动登出。")
        .defaultValue(true)
        .build()
    );

    public AutoLog() {
        super(Categories.Combat, "自动登出", "当满足某些条件时自动断开连接。");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        float playerHealth = mc.player.getHealth();
        if (playerHealth <= 0) {
            this.toggle();
            return;
        }
        if (playerHealth <= health.get()) {
            mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[自动登出] 生命值低于 " + health.get() + "。")));
            if(smartToggle.get()) {
                this.toggle();
                enableHealthListener();
            }
        }

        if(smart.get() && playerHealth + mc.player.getAbsorptionAmount() - PlayerUtils.possibleHealthReductions() < health.get()){
            mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[自动登出] 生命值将低于 " + health.get() + "。")));
            if (toggleOff.get()) this.toggle();
        }

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity && entity.getUuid() != mc.player.getUuid()) {
                if (onlyTrusted.get() && entity != mc.player && !Friends.get().isFriend((PlayerEntity) entity)) {
                        mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[自动登出] 一个不信任的玩家出现在你的渲染距离内。")));
                        if (toggleOff.get()) this.toggle();
                        break;
                }
                if (PlayerUtils.isWithin(entity, 8) && instantDeath.get() && DamageUtils.getSwordDamage((PlayerEntity) entity, true)
                        > playerHealth + mc.player.getAbsorptionAmount()) {
                    mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[自动登出] 反32k措施。")));
                    if (toggleOff.get()) this.toggle();
                    break;
                }
            }
            if (entity instanceof EndCrystalEntity && PlayerUtils.isWithin(entity, range.get()) && crystalLog.get()) {
                mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[自动登出] 水晶出现在指定范围内。")));
                if (toggleOff.get()) this.toggle();
            }
        }
    }

    private class StaticListener {
        @EventHandler
        private void healthListener(TickEvent.Post event) {
            if (isActive()) disableHealthListener();

            else if (Utils.canUpdate()
                    && !mc.player.isDead()
                    && mc.player.getHealth() > health.get()) {
                toggle();
                disableHealthListener();
           }
        }
    }

    private final StaticListener staticListener = new StaticListener();

    private void enableHealthListener(){
        MeteorClient.EVENT_BUS.subscribe(staticListener);
    }
    private void disableHealthListener(){
        MeteorClient.EVENT_BUS.unsubscribe(staticListener);
    }
}
