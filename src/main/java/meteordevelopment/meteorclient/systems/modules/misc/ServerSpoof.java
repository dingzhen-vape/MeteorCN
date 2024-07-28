/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import io.netty.buffer.Unpooled;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixin.CustomPayloadC2SPacketAccessor;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.network.packet.s2c.play.ResourcePackSendS2CPacket;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class ServerSpoof extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> spoofBrand = sgGeneral.add(new BoolSetting.Builder()
        .name("伪装品牌")
        .description("是否伪装品牌。")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> brand = sgGeneral.add(new StringSetting.Builder()
        .name("品牌")
        .description("指定要发送给服务器的品牌。")
        .defaultValue("原版")
        .visible(spoofBrand::get)
        .build()
    );

    private final Setting<Boolean> resourcePack = sgGeneral.add(new BoolSetting.Builder()
        .name("资源包")
        .description("伪装接受服务器资源包。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> blockChannels = sgGeneral.add(new BoolSetting.Builder()
        .name("屏蔽频道")
        .description("是否屏蔽一些频道。")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> channels = sgGeneral.add(new StringListSetting.Builder()
        .name("频道")
        .description("如果频道包含关键字，这个输出频道将被屏蔽。")
        .defaultValue("minecraft:register")
        .visible(blockChannels::get)
        .build()
    );

    public ServerSpoof() {
        super(Categories.Misc, "服务器伪装", "伪装客户端品牌，资源包和频道。");

        MeteorClient.EVENT_BUS.subscribe(new Listener());
    }

    private class Listener {
        @EventHandler
        private void onPacketSend(PacketEvent.Send event) {
            if (!isActive()) return;
            if (!(event.packet instanceof CustomPayloadC2SPacket)) return;
            CustomPayloadC2SPacketAccessor packet = (CustomPayloadC2SPacketAccessor) event.packet;
            Identifier id = packet.getChannel();

            if (spoofBrand.get() && id.equals(CustomPayloadC2SPacket.BRAND))
                packet.setData(new PacketByteBuf(Unpooled.buffer()).writeString(brand.get()));

            if (blockChannels.get()) {
                for (String channel : channels.get()) {
                    if (StringUtils.containsIgnoreCase(channel, id.toString())) {
                        event.cancel();
                        return;
                    }
                }
            }
        }

        @EventHandler
        private void onPacketRecieve(PacketEvent.Receive event) {
            if (!isActive()) return;

            if (resourcePack.get()) {
                if (!(event.packet instanceof ResourcePackSendS2CPacket packet)) return;
                event.cancel();
                MutableText msg = Text.literal("这个服务器有 ");
                msg.append(packet.isRequired() ? "一个必须的 " : "一个可选的 ");
                MutableText link = Text.literal("资源包");
                link.setStyle(link.getStyle()
                    .withColor(Formatting.BLUE)
                    .withUnderline(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, packet.getURL()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("点击下载")))
                );
                msg.append(link);
                msg.append("。");
                info(msg);
            }
        }
    }
}
