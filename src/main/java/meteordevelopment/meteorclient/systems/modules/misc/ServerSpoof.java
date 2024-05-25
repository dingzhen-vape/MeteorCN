/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.text.RunnableClickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.BrandCustomPayload;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
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
        .description("指定将发送到服务器的品牌。")
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
        .name("阻止频道")
        .description("是否阻止一些频道。")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> channels = sgGeneral.add(new StringListSetting.Builder()
        .name("频道")
        .description("如果频道包含关键词，这个传出频道将被阻止。")
        .defaultValue("fabric", "minecraft:register")
        .visible(blockChannels::get)
        .build()
    );

    public ServerSpoof() {
        super(Categories.Misc, "服务器伪装", "伪装客户端品牌、资源包和频道。");

        runInMainMenu = true;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!isActive() || !(event.packet instanceof CustomPayloadC2SPacket)) return;
        Identifier id = ((CustomPayloadC2SPacket) event.packet).payload().getId().id();

        if (blockChannels.get()) {
            for (String channel : channels.get()) {
                if (StringUtils.containsIgnoreCase(id.toString(), channel)) {
                    event.cancel();
                    return;
                }
            }
        }

        if (spoofBrand.get() && id.equals(BrandCustomPayload.ID.id())) {
            CustomPayloadC2SPacket spoofedPacket = new CustomPayloadC2SPacket(new BrandCustomPayload(brand.get()));

            // PacketEvent.Send doesn't trigger if we send the packet like this
            event.connection.send(spoofedPacket, null, true);
            event.cancel();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isActive()) return;

        if (resourcePack.get()) {
            if (!(event.packet instanceof ResourcePackSendS2CPacket packet)) return;
            event.cancel();

            MutableText msg = Text.literal("这个服务器有 ");
            msg.append(packet.required() ? "一个必需的 " : "一个可选的 ").append("资源包。 ");

            MutableText link = Text.literal("[下载]");
            link.setStyle(link.getStyle()
                .withColor(Formatting.BLUE)
                .withUnderline(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, packet.url()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("点击下载")))
            );

            MutableText acceptance = Text.literal("[伪装接受]");
            acceptance.setStyle(acceptance.getStyle()
                .withColor(Formatting.DARK_GREEN)
                .withUnderline(true)
                .withClickEvent(new RunnableClickEvent(() -> {
                    event.connection.send(new ResourcePackStatusC2SPacket(packet.id(), ResourcePackStatusC2SPacket.Status.ACCEPTED));
                    event.connection.send(new ResourcePackStatusC2SPacket(packet.id(), ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED));
                }))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("点击伪装接受资源包。")))
            );

            msg.append(link).append(" ");
            msg.append(acceptance).append(".");
            info(msg);
        }
    }
}
