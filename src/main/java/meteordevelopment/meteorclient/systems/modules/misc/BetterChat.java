/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.chars.Char2CharMap;
import it.unimi.dsi.fastutil.chars.Char2CharOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.mixin.ChatHudAccessor;
import meteordevelopment.meteorclient.mixininterface.IChatHudLine;
import meteordevelopment.meteorclient.mixininterface.IChatHudLineVisible;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.MeteorIdentifier;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class BetterChat extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("过滤器");
    private final SettingGroup sgLongerChat = settings.createGroup("更长的聊天");
    private final SettingGroup sgPrefix = settings.createGroup("前缀");
    private final SettingGroup sgSuffix = settings.createGroup("后缀");

    private final Setting<Boolean> annoy = sgGeneral.add(new BoolSetting.Builder()
        .name("恼人")
        .description("让你的消息aNnOyInG。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> fancy = sgGeneral.add(new BoolSetting.Builder()
        .name("精致聊天")
        .description("让你的消息 ғᴀɴᴄʏ!")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> timestamps = sgGeneral.add(new BoolSetting.Builder()
        .name("时间戳")
        .description("在聊天消息的开头添加客户端时间戳。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> playerHeads = sgGeneral.add(new BoolSetting.Builder()
        .name("玩家头像")
        .description("在他们的消息旁边显示玩家头像。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> coordsProtection = sgGeneral.add(new BoolSetting.Builder()
        .name("坐标保护")
        .description("防止你在聊天中发送可能包含坐标的消息。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> keepHistory = sgGeneral.add(new BoolSetting.Builder()
        .name("保留历史")
        .description("断开连接时防止聊天历史被清除。")
        .defaultValue(true)
        .build()
    );

    // Filter

    private final Setting<Boolean> antiSpam = sgFilter.add(new BoolSetting.Builder()
        .name("防刷屏")
        .description("阻止重复的消息填满你的聊天。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> antiSpamDepth = sgFilter.add(new IntSetting.Builder()
        .name("深度")
        .description("要过滤的消息数量。")
        .defaultValue(20)
        .min(1)
        .sliderMin(1)
        .visible(antiSpam::get)
        .build()
    );

    private final Setting<Boolean> filterRegex = sgFilter.add(new BoolSetting.Builder()
        .name("过滤正则表达式")
        .description("过滤掉与正则表达式过滤器匹配的聊天消息。")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<String>> regexFilters = sgFilter.add(new StringListSetting.Builder()
        .name("正则表达式过滤器")
        .description("用于过滤聊天消息的正则表达式过滤器。")
        .visible(filterRegex::get)
        .onChanged(strings -> compileFilterRegexList())
        .build()
    );


    // Longer chat

    private final Setting<Boolean> infiniteChatBox = sgLongerChat.add(new BoolSetting.Builder()
        .name("无限聊天框")
        .description("让你可以输入无限长的消息。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> longerChatHistory = sgLongerChat.add(new BoolSetting.Builder()
        .name("更长的聊天历史")
        .description("延长聊天长度。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> longerChatLines = sgLongerChat.add(new IntSetting.Builder()
        .name("额外的行数")
        .description("额外的聊天行数。")
        .defaultValue(1000)
        .min(0)
        .sliderRange(0, 1000)
        .visible(longerChatHistory::get)
        .build()
    );

    // Prefix

    private final Setting<Boolean> prefix = sgPrefix.add(new BoolSetting.Builder()
        .name("前缀")
        .description("在你的聊天消息前添加一个前缀。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> prefixRandom = sgPrefix.add(new BoolSetting.Builder()
        .name("随机")
        .description("使用一个随机数作为你的前缀。")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> prefixText = sgPrefix.add(new StringSetting.Builder()
        .name("文本")
        .description("要添加为前缀的文本。")
        .defaultValue("> ")
        .visible(() -> !prefixRandom.get())
        .build()
    );

    private final Setting<Boolean> prefixSmallCaps = sgPrefix.add(new BoolSetting.Builder()
        .name("小写字母")
        .description("在前缀中使用小写字母。")
        .defaultValue(false)
        .visible(() -> !prefixRandom.get())
        .build()
    );

    // Suffix

    private final Setting<Boolean> suffix = sgSuffix.add(new BoolSetting.Builder()
        .name("后缀")
        .description("在你的聊天消息后添加一个后缀。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> suffixRandom = sgSuffix.add(new BoolSetting.Builder()
        .name("随机")
        .description("使用一个随机数作为你的后缀。")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> suffixText = sgSuffix.add(new StringSetting.Builder()
        .name("文本")
        .description("要添加为后缀的文本。")
        .defaultValue(" | meteor on crack!")
        .visible(() -> !suffixRandom.get())
        .build()
    );

    private final Setting<Boolean> suffixSmallCaps = sgSuffix.add(new BoolSetting.Builder()
        .name("小写字母")
        .description("在后缀中使用小写字母。")
        .defaultValue(true)
        .visible(() -> !suffixRandom.get())
        .build()
    );

    private static final Pattern antiSpamRegex = Pattern.compile(" \\(([0-9]+)\\)$");
    private static final Pattern timestampRegex = Pattern.compile("^(<[0-9]{2}:[0-9]{2}>\\s)");

    private final Char2CharMap SMALL_CAPS = new Char2CharOpenHashMap();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
    public final IntList lines = new IntArrayList();

    public BetterChat() {
        super(Categories.Misc, "更好的聊天", "以各种方式改善你的聊天体验。");

        String[] a = "abcdefghijklmnopqrstuvwxyz".split("");
        String[] b = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴩqʀꜱᴛᴜᴠᴡxyᴢ".split("");
        for (int i = 0; i < a.length; i++) SMALL_CAPS.put(a[i].charAt(0), b[i].charAt(0));
        compileFilterRegexList();
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        Text message = event.getMessage();

        if (filterRegex.get()) {
            String messageString = message.getString();
            for (Pattern pattern : filterRegexList) {
                if (pattern.matcher(messageString).find()) {
                    event.cancel();
                    return;
                }
            }
        }

        if (antiSpam.get()) {
            Text antiSpammed = appendAntiSpam(message);

            if (antiSpammed != null) {
                message = antiSpammed;
            }
        }

        if (timestamps.get()) {
            Text timestamp = Text.literal("<" + dateFormat.format(new Date()) + "> ").formatted(Formatting.GRAY);

            message = Text.empty().append(timestamp).append(message);
        }

        event.setMessage(message);
    }


    private Text appendAntiSpam(Text text) {
        String textString = text.getString();
        Text returnText = null;
        int messageIndex = -1;

        List<ChatHudLine> messages = ((ChatHudAccessor) mc.inGameHud.getChatHud()).getMessages();
        if (messages.isEmpty()) return null;

        for (int i = 0; i < Math.min(antiSpamDepth.get(), messages.size()); i++) {
            String stringToCheck = messages.get(i).content().getString();

            Matcher timestampMatcher = timestampRegex.matcher(stringToCheck);
            if (timestampMatcher.find()) {
                stringToCheck = stringToCheck.substring(8);
            }

            if (textString.equals(stringToCheck)) {
                messageIndex = i;
                returnText = text.copy().append(Text.literal(" (2)").formatted(Formatting.GRAY));
                break;
            } else {
                Matcher matcher = antiSpamRegex.matcher(stringToCheck);
                if (!matcher.find()) continue;

                String group = matcher.group(matcher.groupCount());
                int number = Integer.parseInt(group);

                if (stringToCheck.substring(0, matcher.start()).equals(textString)) {
                    messageIndex = i;
                    returnText = text.copy().append(Text.literal(" (" + (number + 1) + ")").formatted(Formatting.GRAY));
                    break;
                }
            }
        }

        if (returnText != null) {
            List<ChatHudLine.Visible> visible = ((ChatHudAccessor) mc.inGameHud.getChatHud()).getVisibleMessages();

            int start = -1;
            for (int i = 0; i < messageIndex; i++) {
                start += lines.getInt(i);
            }

            int i = lines.getInt(messageIndex);
            while (i > 0) {
                visible.remove(start + 1);
                i--;
            }

            messages.remove(messageIndex);
            lines.removeInt(messageIndex);
        }

        return returnText;
    }

    @EventHandler
    private void onMessageSend(SendMessageEvent event) {
        String message = event.message;

        if (annoy.get()) message = applyAnnoy(message);

        if (fancy.get()) message = applyFancy(message);

        message = getPrefix() + message + getSuffix();

        if (coordsProtection.get() && containsCoordinates(message)) {
            MutableText warningMessage = Text.literal("你的消息中似乎有坐标！ ");

            MutableText sendButton = getSendButton(message);
            warningMessage.append(sendButton);

            ChatUtils.sendMsg(warningMessage);

            event.cancel();
            return;
        }

        event.message = message;
    }

    // Player Heads

    private record CustomHeadEntry(String prefix, Identifier texture) {}

    private static final List<CustomHeadEntry> CUSTOM_HEAD_ENTRIES = new ArrayList<>();

    private static final Pattern TIMESTAMP_REGEX = Pattern.compile("^<\\d{1,2}:\\d{1,2}>");

    /** Registers a custom player head to render based on a message prefix */
    public static void registerCustomHead(String prefix, Identifier texture) {
        CUSTOM_HEAD_ENTRIES.add(new CustomHeadEntry(prefix, texture));
    }

    static {
        registerCustomHead("[Meteor]", new MeteorIdentifier("textures/icons/chat/meteor.png"));
        registerCustomHead("[Baritone]", new MeteorIdentifier("textures/icons/chat/baritone.png"));
    }

    public int modifyChatWidth(int width) {
        if (isActive() && playerHeads.get()) return width + 10;
        return width;
    }

    public void drawPlayerHead(DrawContext context, ChatHudLine.Visible line, int y, int color) {
        if (!isActive() || !playerHeads.get()) return;

        // Only draw the first line of multi line messages
        if (((IChatHudLineVisible) (Object) line).meteor$isStartOfEntry())  {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1, 1, 1, Color.toRGBAA(color) / 255f);

            drawTexture(context, (IChatHudLine) (Object) line, y);

            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.disableBlend();
        }

        // Offset
        context.getMatrices().translate(10, 0, 0);
    }

    private void drawTexture(DrawContext context, IChatHudLine line, int y) {
        String text = line.meteor$getText().trim();

        // Custom
        int startOffset = 0;

        try {
            Matcher m = TIMESTAMP_REGEX.matcher(text);
            if (m.find()) startOffset = m.end() + 1;
        }
        catch (IllegalStateException ignored) {}

        for (CustomHeadEntry entry : CUSTOM_HEAD_ENTRIES) {
            // Check prefix
            if (text.startsWith(entry.prefix(), startOffset)) {
                context.drawTexture(entry.texture(), 0, y, 8, 8, 0, 0, 64, 64, 64, 64);
                return;
            }
        }

        // Player
        GameProfile sender = getSender(line, text);
        if (sender == null) return;

        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(sender.getId());
        if (entry == null) return;

        Identifier skin = entry.getSkinTextures().texture();

        context.drawTexture(skin, 0, y, 8, 8, 8, 8, 8, 8, 64, 64);
        context.drawTexture(skin, 0, y, 8, 8, 40, 8, 8, 8, 64, 64);
    }

    private GameProfile getSender(IChatHudLine line, String text) {
        GameProfile sender = line.meteor$getSender();

        // If the packet did not contain a sender field then try to get the sender from the message
        if (sender == null) {
            int openingI = text.indexOf('<');
            int closingI = text.indexOf('>');

            if (openingI != -1 && closingI != -1 && closingI > openingI) {
                String username = text.substring(openingI + 1, closingI);

                PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(username);
                if (entry != null) sender = entry.getProfile();
            }
        }

        return sender;
    }

    // Annoy

    private String applyAnnoy(String message) {
        StringBuilder sb = new StringBuilder(message.length());
        boolean upperCase = true;
        for (int cp : message.codePoints().toArray()) {
            if (upperCase) sb.appendCodePoint(Character.toUpperCase(cp));
            else sb.appendCodePoint(Character.toLowerCase(cp));
            upperCase = !upperCase;
        }
        message = sb.toString();
        return message;
    }

    // Fancy

    private String applyFancy(String message) {
        StringBuilder sb = new StringBuilder();

        for (char ch : message.toCharArray()) {
            sb.append(SMALL_CAPS.getOrDefault(ch, ch));
        }

        return sb.toString();
    }

    // Filter Regex

    private final List<Pattern> filterRegexList = new ArrayList<>();

    private void compileFilterRegexList() {
        filterRegexList.clear();

        for (int i = 0; i < regexFilters.get().size(); i++) {
            try {
                filterRegexList.add(Pattern.compile(regexFilters.get().get(i)));
            } catch (PatternSyntaxException e) {
                String removed = regexFilters.get().remove(i);
                error("移除无效的正则表达式: %s", removed);
            }
        }
    }

    // Prefix and Suffix

    private String getPrefix() {
        return prefix.get() ? getAffix(prefixText.get(), prefixSmallCaps.get(), prefixRandom.get()) : "";
    }

    private String getSuffix() {
        return suffix.get() ? getAffix(suffixText.get(), suffixSmallCaps.get(), suffixRandom.get()) : "";
    }

    private String getAffix(String text, boolean smallcaps, boolean random) {
        if (random) return String.format("(%03d) ", Utils.random(0, 1000));
        else if (smallcaps) return applyFancy(text);
        else return text;
    }

    // Coords Protection

    private static final Pattern coordRegex = Pattern.compile("(?<x>-?\\d{3,}(?:\\.\\d*)?)(?:\\s+(?<y>-?\\d{1,3}(?:\\.\\d*)?))?\\s+(?<z>-?\\d{3,}(?:\\.\\d*)?)");

    private boolean containsCoordinates(String message) {
        return coordRegex.matcher(message).find();
    }

    private MutableText getSendButton(String message) {
        MutableText sendButton = Text.literal("[无视坐标发送]");
        MutableText hintBaseText = Text.literal("");

        MutableText hintMsg = Text.literal("即使消息中有坐标，也将其发送到全局聊天：");
        hintMsg.setStyle(hintBaseText.getStyle().withFormatting(Formatting.GRAY));
        hintBaseText.append(hintMsg);

        hintBaseText.append(Text.literal('\n' + message));

        sendButton.setStyle(sendButton.getStyle()
            .withFormatting(Formatting.DARK_RED)
            .withClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                Commands.get("say").toString(message)
            ))
            .withHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                hintBaseText
            )));
        return sendButton;
    }

    // Longer chat

    public boolean isInfiniteChatBox() {
        return isActive() && infiniteChatBox.get();
    }

    public boolean isLongerChat() {
        return isActive() && longerChatHistory.get();
    }

    public boolean keepHistory() { return isActive() && keepHistory.get(); }

    public int getExtraChatLines() {
        return longerChatLines.get();
    }
}
