/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.proxies.Proxies;
import meteordevelopment.meteorclient.systems.proxies.Proxy;
import meteordevelopment.meteorclient.systems.proxies.ProxyType;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Matcher;

public class ProxiesImportScreen extends WindowScreen {

    private final File file;
    public ProxiesImportScreen(GuiTheme theme, File file) {
        super(theme, "导入代理");
        this.file = file;
        this.onClosed(() -> {
            if (parent instanceof ProxiesScreen screen) {
                screen.reload();
            }
        });
    }

    @Override
    public void initWidgets() {
        if (file.exists() && file.isFile()) {
            add(theme.label("正在导入代理：" + file.getName() + "...").color(Color.GREEN));
            WVerticalList list = add(theme.section("日志", false)).widget().add(theme.verticalList()).expandX().widget();
            Proxies proxies = Proxies.get();
            try {
                int pog = 0, bruh = 0;
                for (String line : Files.readAllLines(file.toPath())) {
                    Matcher matcher = Proxies.PROXY_PATTERN.matcher(line);

                    if (matcher.matches()) {
                        String address = matcher.group(2).replaceAll("\\b0+\\B", "");
                        int port = Integer.parseInt(matcher.group(3));

                        Proxy proxy = new Proxy.Builder()
                            .address(address)
                            .port(port)
                            .name(matcher.group(1) != null ? matcher.group(1) : address + ":" + port)
                            .type(matcher.group(4) != null ? ProxyType.parse(matcher.group(4)) : ProxyType.Socks4)
                            .build();

                        if (proxies.add(proxy)) {
                            list.add(theme.label("导入的代理：" + proxy.name.get()).color(Color.GREEN));
                            pog++;
                        }
                        else {
                            list.add(theme.label("代理已存在：" + proxy.name.get()).color(Color.ORANGE));
                            bruh++;
                        }
                    }
                    else {
                        list.add(theme.label("无效的代理：" + line).color(Color.RED));
                        bruh++;
                    }
                }
                add(theme
                    .label("成功导入 " + pog + "/" + (bruh + pog) + " 个代理。")
                    .color(Utils.lerp(Color.RED, Color.GREEN, (float) pog / (pog + bruh)))
                );
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            add(theme.label("无效文件！"));
        }
        add(theme.horizontalSeparator()).expandX();
        WButton btnBack = add(theme.button("返回")).expandX().widget();
        btnBack.action = this::close;
    }
}
