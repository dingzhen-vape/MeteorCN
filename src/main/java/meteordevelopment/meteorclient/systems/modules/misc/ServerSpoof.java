/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

public class ServerSpoof extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> spoofBrand = sgGeneral.add(new BoolSetting.Builder()
        .name("欺骗版本")
        .description("是否欺骗版本。")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> brand = sgGeneral.add(new StringSetting.Builder()
        .name("版本")
        .description("指定将发送到服务器的版本。")
        .defaultValue("原版")
        .visible(spoofBrand::get)
        .build()
    );

    private final Setting<Boolean> resourcePack = sgGeneral.add(new BoolSetting.Builder()
        .name("资源包")
        .description("欺骗接受服务器资源包。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> blockChannels = sgGeneral.add(new BoolSetting.Builder()
        .name("封锁频道")
        .description("是否封锁某些频道。")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> channels = sgGeneral.add(new StringListSetting.Builder()
        .name("频道")
        .description("如果频道包含关键词,则此出站频道将被封锁。")
        .defaultValue("fabric", "minecraft:register")
        .visible(blockChannels::get)
        .build()
    );

    private MutableText msg;
    public boolean silentAcceptResourcePack = false;

    public ServerSpoof() {
        super(Categories.Misc, "服务器欺骗", "欺骗客户端版本、资源包和频道。");

        runInMainMenu = true;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!isActive()) return;

        if (event.packet instanceof CustomPayloadC2SPacket) {
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

        // we want to accept the pack silently to prevent the server detecting you bypassed it when logging in
        if (silentAcceptResourcePack && event.packet instanceof ResourcePackStatusC2SPacket) event.cancel();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isActive() || !resourcePack.get()) return;
        if (!(event.packet instanceof ResourcePackSendS2CPacket packet)) return;

        event.cancel();
        event.connection.send(new ResourcePackStatusC2SPacket(packet.id(), ResourcePackStatusC2SPacket.Status.ACCEPTED));
        event.connection.send(new ResourcePackStatusC2SPacket(packet.id(), ResourcePackStatusC2SPacket.Status.DOWNLOADED));
        event.connection.send(new ResourcePackStatusC2SPacket(packet.id(), ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED));

        msg = Text.literal("此服务器有");
        msg.append(packet.required() ? "一个必需的" : "一个可选的").append("资源包。");

        MutableText link = Text.literal("[打开网址]");
        link.setStyle(link.getStyle()
            .withColor(Formatting.BLUE)
            .withUnderline(true)
            .withClickEvent(new ClickEvent.OpenUrl(URI.create(packet.url())))
            .withHoverEvent(new HoverEvent.ShowText(Text.literal("点击打开资源包网址")))
        );

        MutableText acceptance = Text.literal("[接受资源包]");
        acceptance.setStyle(acceptance.getStyle()
            .withColor(Formatting.DARK_GREEN)
            .withUnderline(true)
            .withClickEvent(new RunnableClickEvent(() -> {
                URL url = getParsedResourcePackUrl(packet.url());
                if (url == null) error("无效的资源包网址：" + packet.url());
                else {
                    silentAcceptResourcePack = true;
                    mc.getServerResourcePackProvider().addResourcePack(packet.id(), url, packet.hash());
                }
            }))
            .withHoverEvent(new HoverEvent.ShowText(Text.literal("点击接受并应用资源包。")))
        );

        msg.append(link).append(" ");
        msg.append(acceptance).append(".");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || !Utils.canUpdate() || msg == null) return;

        info(msg);
        msg = null;
    }

    private static URL getParsedResourcePackUrl(String url) {
        try {
            URL uRL = new URI(url).toURL();
            String string = uRL.getProtocol();
            return !"http".equals(string) && !"https".equals(string) ? null : uRL;
        } catch (MalformedURLException | URISyntaxException var3) {
            return null;
        }
    }
}
