/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.*;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class StashFinder extends Module {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<BlockEntityType<?>>> storageBlocks = sgGeneral.add(new StorageBlockListSetting.Builder()
        .name("storage-blocks")
        .description("选择要搜索的存储方块。")
        .defaultValue(StorageBlockListSetting.STORAGE_BLOCKS)
        .build()
    );

    private final Setting<Integer> minimumStorageCount = sgGeneral.add(new IntSetting.Builder()
        .name("最小存储数量")
        .description("一个区块中需要有的最小存储方块数量才能记录该区块。")
        .defaultValue(4)
        .min(1)
        .sliderMin(1)
        .build()
    );

    private final Setting<Integer> minimumDistance = sgGeneral.add(new IntSetting.Builder()
        .name("最小距离")
        .description("你必须离出生点多远才能记录某个区块。")
        .defaultValue(0)
        .min(0)
        .sliderMax(10000)
        .build()
    );

    private final Setting<Boolean> sendNotifications = sgGeneral.add(new BoolSetting.Builder()
        .name("通知")
        .description("当发现新的储藏点时,发送Minecraft通知。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Mode> notificationMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("通知模式")
        .description("通知使用的模式。")
        .defaultValue(Mode.Both)
        .visible(sendNotifications::get)
        .build()
    );

    public List<Chunk> chunks = new ArrayList<>();

    public StashFinder() {
        super(Categories.World, "储藏点寻找器", "搜索加载的区块中的存储方块。保存到<你的minecraft文件夹>/meteor-client");
    }

    @Override
    public void onActivate() {
        load();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        // Check the distance.
        double chunkXAbs = Math.abs(event.chunk().getPos().x * 16);
        double chunkZAbs = Math.abs(event.chunk().getPos().z * 16);
        if (Math.sqrt(chunkXAbs * chunkXAbs + chunkZAbs * chunkZAbs) < minimumDistance.get()) return;

        Chunk chunk = new Chunk(event.chunk().getPos());

        for (BlockEntity blockEntity : event.chunk().getBlockEntities().values()) {
            if (!storageBlocks.get().contains(blockEntity.getType())) continue;

            if (blockEntity instanceof ChestBlockEntity) chunk.chests++;
            else if (blockEntity instanceof BarrelBlockEntity) chunk.barrels++;
            else if (blockEntity instanceof ShulkerBoxBlockEntity) chunk.shulkers++;
            else if (blockEntity instanceof EnderChestBlockEntity) chunk.enderChests++;
            else if (blockEntity instanceof AbstractFurnaceBlockEntity) chunk.furnaces++;
            else if (blockEntity instanceof DispenserBlockEntity) chunk.dispensersDroppers++;
            else if (blockEntity instanceof HopperBlockEntity) chunk.hoppers++;
        }

        if (chunk.getTotal() >= minimumStorageCount.get()) {
            Chunk prevChunk = null;
            int i = chunks.indexOf(chunk);

            if (i < 0) chunks.add(chunk);
            else prevChunk = chunks.set(i, chunk);

            saveJson();
            saveCsv();

            if (sendNotifications.get() && (!chunk.equals(prevChunk) || !chunk.countsEqual(prevChunk))) {
                switch (notificationMode.get()) {
                    case Chat -> info("在 (highlight)%s(default), (highlight)%s(default) 发现储藏点。", chunk.x, chunk.z);
                    case Toast -> {
                        MeteorToast toast = new MeteorToast.Builder(title).icon(Items.CHEST).text("发现储藏点！").build();
                        mc.getToastManager().add(toast);
                    }
                    case Both -> {
                        info("在 (highlight)%s(default), (highlight)%s(default) 发现储藏点。", chunk.x, chunk.z);
                        MeteorToast toast = new MeteorToast.Builder(title).icon(Items.CHEST).text("发现储藏点！").build();
                        mc.getToastManager().add(toast);
                    }
                }
            }
        }
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        // Sort
        chunks.sort(Comparator.comparingInt(value -> -value.getTotal()));

        WVerticalList list = theme.verticalList();

        // Clear
        WButton clear = list.add(theme.button("清除")).widget();

        WTable table = new WTable();
        if (!chunks.isEmpty()) list.add(table);

        clear.action = () -> {
            chunks.clear();
            table.clear();
        };

        // Chunks
        fillTable(theme, table);

        return list;
    }

    private void fillTable(GuiTheme theme, WTable table) {
        for (Chunk chunk : chunks) {
            table.add(theme.label("位置: " + chunk.x + ", " + chunk.z));
            table.add(theme.label("总数: " + chunk.getTotal()));

            WButton open = table.add(theme.button("打开")).widget();
            open.action = () -> mc.setScreen(new ChunkScreen(theme, chunk));

            WButton gotoBtn = table.add(theme.button("前往")).widget();
            gotoBtn.action = () -> PathManagers.get().moveTo(new BlockPos(chunk.x, 0, chunk.z), true);

            WMinus delete = table.add(theme.minus()).widget();
            delete.action = () -> {
                if (chunks.remove(chunk)) {
                    table.clear();
                    fillTable(theme, table);

                    saveJson();
                    saveCsv();
                }
            };

            table.row();
        }
    }

    private void load() {
        boolean loaded = false;

        // Try to load json
        File file = getJsonFile();
        if (file.exists()) {
            try {
                FileReader reader = new FileReader(file);
                chunks = GSON.fromJson(reader, new TypeToken<List<Chunk>>() {}.getType());
                reader.close();

                for (Chunk chunk : chunks) chunk.calculatePos();

                loaded = true;
            } catch (Exception ignored) {
                if (chunks == null) chunks = new ArrayList<>();
            }
        }

        // Try to load csv
        file = getCsvFile();
        if (!loaded && file.exists()) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                reader.readLine();

                String line;
                while ((line = reader.readLine()) != null) {
                    String[] values = line.split(" ");
                    Chunk chunk = new Chunk(new ChunkPos(Integer.parseInt(values[0]), Integer.parseInt(values[1])));

                    chunk.chests = Integer.parseInt(values[2]);
                    chunk.shulkers = Integer.parseInt(values[3]);
                    chunk.enderChests = Integer.parseInt(values[4]);
                    chunk.furnaces = Integer.parseInt(values[5]);
                    chunk.dispensersDroppers = Integer.parseInt(values[6]);
                    chunk.hoppers = Integer.parseInt(values[7]);

                    chunks.add(chunk);
                }

                reader.close();
            } catch (Exception ignored) {
                if (chunks == null) chunks = new ArrayList<>();
            }
        }
    }

    private void saveCsv() {
        try {
            File file = getCsvFile();
            file.getParentFile().mkdirs();
            Writer writer = new FileWriter(file);

            writer.write("X,Z,箱子,桶,潜影盒,末影箱,熔炉,发射器投掷器,漏斗\n");
            for (Chunk chunk : chunks) chunk.write(writer);

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveJson() {
        try {
            File file = getJsonFile();
            file.getParentFile().mkdirs();
            Writer writer = new FileWriter(file);
            GSON.toJson(chunks, writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File getJsonFile() {
        return new File(new File(new File(MeteorClient.FOLDER, "储藏点"), Utils.getFileWorldName()), "stashes.json");
    }

    private File getCsvFile() {
        return new File(new File(new File(MeteorClient.FOLDER, "储藏点"), Utils.getFileWorldName()), "stashes.csv");
    }

    @Override
    public String getInfoString() {
        return String.valueOf(chunks.size());
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }

    public static class Chunk {
        private static final StringBuilder sb = new StringBuilder();

        public ChunkPos chunkPos;
        public transient int x, z;
        public int chests, barrels, shulkers, enderChests, furnaces, dispensersDroppers, hoppers;

        public Chunk(ChunkPos chunkPos) {
            this.chunkPos = chunkPos;

            calculatePos();
        }

        public void calculatePos() {
            x = chunkPos.x * 16 + 8;
            z = chunkPos.z * 16 + 8;
        }

        public int getTotal() {
            return chests + barrels + shulkers + enderChests + furnaces + dispensersDroppers + hoppers;
        }

        public void write(Writer writer) throws IOException {
            sb.setLength(0);
            sb.append(x).append(',').append(z).append(',');
            sb.append(chests).append(',').append(barrels).append(',').append(shulkers).append(',').append(enderChests).append(',').append(furnaces).append(',').append(dispensersDroppers).append(',').append(hoppers).append('\n');
            writer.write(sb.toString());
        }

        public boolean countsEqual(Chunk c) {
            if (c == null) return false;
            return chests != c.chests || barrels != c.barrels || shulkers != c.shulkers || enderChests != c.enderChests || furnaces != c.furnaces || dispensersDroppers != c.dispensersDroppers || hoppers != c.hoppers;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Chunk chunk = (Chunk) o;
            return Objects.equals(chunkPos, chunk.chunkPos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chunkPos);
        }
    }

    private static class ChunkScreen extends WindowScreen {
        private final Chunk chunk;

        public ChunkScreen(GuiTheme theme, Chunk chunk) {
            super(theme, "区块在 " + chunk.x + ", " + chunk.z);

            this.chunk = chunk;
        }

        @Override
        public void initWidgets() {
            WTable t = add(theme.table()).expandX().widget();

            // Total
            t.add(theme.label("总数:"));
            t.add(theme.label(chunk.getTotal() + ""));
            t.row();

            t.add(theme.horizontalSeparator()).expandX();
            t.row();

            // Separate
            t.add(theme.label("箱子:"));
            t.add(theme.label(chunk.chests + ""));
            t.row();

            t.add(theme.label("桶:"));
            t.add(theme.label(chunk.barrels + ""));
            t.row();

            t.add(theme.label("潜影盒:"));
            t.add(theme.label(chunk.shulkers + ""));
            t.row();

            t.add(theme.label("末影箱:"));
            t.add(theme.label(chunk.enderChests + ""));
            t.row();

            t.add(theme.label("熔炉:"));
            t.add(theme.label(chunk.furnaces + ""));
            t.row();

            t.add(theme.label("发射器和投掷器:"));
            t.add(theme.label(chunk.dispensersDroppers + ""));
            t.row();

            t.add(theme.label("漏斗:"));
            t.add(theme.label(chunk.hoppers + ""));
        }
    }
}
