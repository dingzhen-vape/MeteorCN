/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc.swarm;

import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.block.Block;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class SwarmWorker extends Thread {
    private Socket socket;
    public Block target;

    public SwarmWorker(String ip, int port) {
        try {
            socket = new Socket(ip, port);
        } catch (Exception e) {
            socket = null;
            ChatUtils.warningPrefix("Swarm", "在端口%s上的%s找不到服务器。", ip, port);
            e.printStackTrace();
        }

        if (socket != null) start();
    }

    @Override
    public void run() {
        ChatUtils.infoPrefix("Swarm", "在端口%s上的%s连接到Swarm主机。", getIp(socket.getInetAddress().getHostAddress()), socket.getPort());

        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());


            while (!isInterrupted()) {
                String read = in.readUTF();

                if (!read.equals("")) {
                    ChatUtils.infoPrefix("Swarm", "收到命令：(高亮)%s", read);

                    try {
                        Commands.dispatch(read);
                    } catch (Exception e) {
                        ChatUtils.error("获取命令失败。");
                        e.printStackTrace();
                    }
                }
            }

            in.close();
        } catch (IOException e) {
            ChatUtils.errorPrefix("Swarm", "与主机的连接出错。");
            e.printStackTrace();
            disconnect();
        }
    }

    public void disconnect() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        PathManagers.get().stop();

        ChatUtils.infoPrefix("Swarm", "与主机断开连接。");

        interrupt();
    }

    public void tick() {
        if (target == null) return;

        PathManagers.get().stop();
        PathManagers.get().mine(target);

        target = null;
    }

    public String getConnection() {
        return getIp(socket.getInetAddress().getHostAddress()) + ":" + socket.getPort();
    }

    private String getIp(String ip) {
        return ip.equals("127.0.0.1") ? "localhost" : ip;
    }
}
