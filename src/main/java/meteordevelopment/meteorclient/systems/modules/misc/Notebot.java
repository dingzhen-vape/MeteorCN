/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.notebot.NotebotUtils;
import meteordevelopment.meteorclient.utils.notebot.decoder.SongDecoder;
import meteordevelopment.meteorclient.utils.notebot.decoder.SongDecoders;
import meteordevelopment.meteorclient.utils.notebot.instrumentdetect.InstrumentDetectMode;
import meteordevelopment.meteorclient.utils.notebot.song.Note;
import meteordevelopment.meteorclient.utils.notebot.song.Song;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.io.File;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class Notebot extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgNoteMap = settings.createGroup("音符地图", false);
    private final SettingGroup sgRender = settings.createGroup("渲染", true);

    public final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("tick-delay")
        .description("加载歌曲时的延迟。")
        .defaultValue(1)
        .sliderRange(1, 20)
        .min(1)
        .build()
    );

    public final Setting<Integer> concurrentTuneBlocks = sgGeneral.add(new IntSetting.Builder()
        .name("并发音符块")
        .description("同一时间可以调整多少个音符块。在Paper上建议设置为1以避免出现bug。")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    public final Setting<NotebotUtils.NotebotMode> mode = sgGeneral.add(new EnumSetting.Builder<NotebotUtils.NotebotMode>()
        .name("模式")
        .description("选择音符机器的模式")
        .defaultValue(NotebotUtils.NotebotMode.ExactInstruments)
        .build()
    );

    public final Setting<InstrumentDetectMode> instrumentDetectMode = sgGeneral.add(new EnumSetting.Builder<InstrumentDetectMode>()
        .name("乐器检测模式")
        .description("选择一个乐器检测模式。当服务器有修改音符块状态的插件时（例如ItemsAdder），但音符块仍能播放正确的音符时，这个设置可能有用")
        .defaultValue(InstrumentDetectMode.BlockState)
        .build()
    );

    public final Setting<Boolean> polyphonic = sgGeneral.add(new BoolSetting.Builder()
        .name("复音")
        .description("是否允许同时播放多个音符")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> autoRotate = sgGeneral.add(new BoolSetting.Builder()
        .name("自动旋转")
        .description("客户端是否应该查看音符块，当它想要击打它时")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> autoPlay = sgGeneral.add(new BoolSetting.Builder()
        .name("自动播放")
        .description("自动播放随机歌曲")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> roundOutOfRange = sgGeneral.add(new BoolSetting.Builder()
        .name("超出范围四舍五入")
        .description("将超出范围的音符四舍五入")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> swingArm = sgGeneral.add(new BoolSetting.Builder()
        .name("摆臂")
        .description("击打时是否摆动手臂")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> checkNoteblocksAgainDelay = sgGeneral.add(new IntSetting.Builder()
        .name("检查音符块再次延迟")
        .description("调整和检查之间的延迟时间")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    public final Setting<Boolean> renderText = sgRender.add(new BoolSetting.Builder()
        .name("渲染文本")
        .description("是否渲染音符块上方的文本。")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> renderBoxes = sgRender.add(new BoolSetting.Builder()
        .name("渲染盒子")
        .description("是否渲染音符块周围的轮廓。")
        .defaultValue(true)
        .build()
    );

    public final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("如何渲染形状。")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    public final Setting<SettingColor> untunedSideColor = sgRender.add(new ColorSetting.Builder()
        .name("未调整侧边颜色")
        .description("未调整块的侧边颜色。")
        .defaultValue(new SettingColor(204, 0, 0, 10))
        .build()
    );

    public final Setting<SettingColor> untunedLineColor = sgRender.add(new ColorSetting.Builder()
        .name("未调整线条颜色")
        .description("未调整块的线条颜色。")
        .defaultValue(new SettingColor(204, 0, 0, 255))
        .build()
    );

    public final Setting<SettingColor> tunedSideColor = sgRender.add(new ColorSetting.Builder()
        .name("已调整侧边颜色")
        .description("已调整块的侧边颜色。")
        .defaultValue(new SettingColor(0, 204, 0, 10))
        .build()
    );

    public final Setting<SettingColor> tunedLineColor = sgRender.add(new ColorSetting.Builder()
        .name("已调整线条颜色")
        .description("已调整块的线条颜色。")
        .defaultValue(new SettingColor(0, 204, 0, 255))
        .build()
    );

    public final Setting<SettingColor> tuneHitSideColor = sgRender.add(new ColorSetting.Builder()
        .name("击打侧边颜色")
        .description("音符块击打时渲染的侧边颜色。")
        .defaultValue(new SettingColor(255, 153, 0, 10))
        .build()
    );

    private final Setting<SettingColor> tuneHitLineColor = sgRender.add(new ColorSetting.Builder()
        .name("击打线条颜色")
        .description("音符块击打时渲染的线条颜色。")
        .defaultValue(new SettingColor(255, 153, 0, 255))
        .build()
    );

    public final Setting<SettingColor> scannedNoteblockSideColor = sgRender.add(new ColorSetting.Builder()
        .name("扫描音符块侧边颜色")
        .description("扫描音符块渲染的侧边颜色。")
        .defaultValue(new SettingColor(255, 255, 0, 30))
        .build()
    );

    private final Setting<SettingColor> scannedNoteblockLineColor = sgRender.add(new ColorSetting.Builder()
        .name("扫描音符块线条颜色")
        .description("扫描音符块渲染的线条颜色。")
        .defaultValue(new SettingColor(255, 255, 0, 255))
        .build()
    );

    public final Setting<Double> noteTextScale = sgRender.add(new DoubleSetting.Builder()
        .name("音符文本缩放")
        .description("缩放。")
        .defaultValue(1.5)
        .min(0)
        .build()
    );

    public final Setting<Boolean> showScannedNoteblocks = sgRender.add(new BoolSetting.Builder()
        .name("显示扫描音符块")
        .description("显示扫描的音符块")
        .defaultValue(false)
        .build()
    );

    private CompletableFuture<Song> loadingSongFuture = null;

    private Song song; // Loaded song
    private final Map<Note, BlockPos> noteBlockPositions = new HashMap<>(); // Currently used noteblocks by the song
    private final Multimap<Note, BlockPos> scannedNoteblocks = MultimapBuilder.linkedHashKeys().arrayListValues().build(); // Found noteblocks
    private final List<BlockPos> clickedBlocks = new ArrayList<>();
    private Stage stage = Stage.None;
    private PlayingMode playingMode = PlayingMode.None;
    private boolean isPlaying = false;
    private int currentTick = 0;
    private int ticks = 0;
    private WLabel status;

    private boolean anyNoteblockTuned = false;
    private final Map<BlockPos, Integer> tuneHits = new HashMap<>(); // noteblock -> target hits number

    private int waitTicks = -1;


    public Notebot() {
        super(Categories.Misc, "音符机器人", "优美地播放音符块");

        for (NoteBlockInstrument inst : NoteBlockInstrument.values()) {
            NotebotUtils.OptionalInstrument optionalInstrument = NotebotUtils.OptionalInstrument.fromMinecraftInstrument(inst);
            if (optionalInstrument != null) {
                sgNoteMap.add(new EnumSetting.Builder<NotebotUtils.OptionalInstrument>()
                    .name(beautifyText(inst.name()))
                    .defaultValue(optionalInstrument)
                    .visible(() -> mode.get() == NotebotUtils.NotebotMode.ExactInstruments)
                    .build()
                );
            }
        }
    }

    @Override
    public String getInfoString() {
        if (stage == Stage.None) {
            return "无";
        } else {
            return playingMode.toString() + " | " + stage.toString();
        }
    }

    @Override
    public void onActivate() {
        ticks = 0;
        resetVariables();
    }

    private void resetVariables() {
        if (loadingSongFuture != null) {
            loadingSongFuture.cancel(true);
            loadingSongFuture = null;
        }
        clickedBlocks.clear();
        tuneHits.clear();
        anyNoteblockTuned = false;
        currentTick = 0;
        playingMode = PlayingMode.None;
        isPlaying = false;
        stage = Stage.None;
        song = null;
        noteBlockPositions.clear();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderBoxes.get()) return;

        if (stage != Stage.SetUp && stage != Stage.Tune && stage != Stage.WaitingToCheckNoteblocks && !isPlaying) return;

        if (showScannedNoteblocks.get()) {
            for (BlockPos blockPos : scannedNoteblocks.values()) {
                double x1 = blockPos.getX();
                double y1 = blockPos.getY();
                double z1 = blockPos.getZ();
                double x2 = blockPos.getX() + 1;
                double y2 = blockPos.getY() + 1;
                double z2 = blockPos.getZ() + 1;

                event.renderer.box(x1, y1, z1, x2, y2, z2, scannedNoteblockSideColor.get(), scannedNoteblockLineColor.get(), shapeMode.get(), 0);
            }
        } else {
            for (var entry : noteBlockPositions.entrySet()) {
                Note note = entry.getKey();
                BlockPos blockPos = entry.getValue();

                BlockState state = mc.world.getBlockState(blockPos);
                if (state.getBlock() != Blocks.NOTE_BLOCK) continue;

                int level = state.get(NoteBlock.NOTE);

                double x1 = blockPos.getX();
                double y1 = blockPos.getY();
                double z1 = blockPos.getZ();
                double x2 = blockPos.getX() + 1;
                double y2 = blockPos.getY() + 1;
                double z2 = blockPos.getZ() + 1;

                // Render boxes around noteblocks in use

                Color sideColor;
                Color lineColor;
                if (clickedBlocks.contains(blockPos)) {
                    sideColor = tuneHitSideColor.get();
                    lineColor = tuneHitLineColor.get();
                } else {
                    if (note.getNoteLevel() == level) {
                        sideColor = tunedSideColor.get();
                        lineColor = tunedLineColor.get();
                    } else {
                        sideColor = untunedSideColor.get();
                        lineColor = untunedLineColor.get();
                    }
                }

                event.renderer.box(x1, y1, z1, x2, y2, z2, sideColor, lineColor, shapeMode.get(), 0);
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!renderText.get()) return;

        if (stage != Stage.SetUp && stage != Stage.Tune && stage != Stage.WaitingToCheckNoteblocks && !isPlaying) return;

        Vector3d pos = new Vector3d();

        for (BlockPos blockPos : noteBlockPositions.values()) {
            BlockState state = mc.world.getBlockState(blockPos);
            if (state.getBlock() != Blocks.NOTE_BLOCK) continue;

            double x = blockPos.getX() + 0.5;
            double y = blockPos.getY() + 1;
            double z = blockPos.getZ() + 0.5;

            pos.set(x, y, z);

            // Render level text logic

            String levelText = String.valueOf(state.get(NoteBlock.NOTE));
            String tuneHitsText = null;
            if (tuneHits.containsKey(blockPos)) {
                tuneHitsText = " -" + tuneHits.get(blockPos);
            }

            if (!NametagUtils.to2D(pos, noteTextScale.get(), true)) {
                continue;
            }

            TextRenderer text = TextRenderer.get();

            NametagUtils.begin(pos);
            text.beginBig();

            double xScreen = text.getWidth(levelText) / 2.0;
            if (tuneHitsText != null) {
                xScreen += text.getWidth(tuneHitsText) / 2.0;
            }

            double hX = text.render(levelText, -xScreen, 0, Color.GREEN);
            if (tuneHitsText != null) {
                text.render(tuneHitsText, hX, 0, Color.RED);
            }
            text.end();

            NametagUtils.end();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        ticks++;
        clickedBlocks.clear();

        if (stage == Stage.WaitingToCheckNoteblocks) {
            waitTicks--;
            if (waitTicks == 0) {
                waitTicks = -1;
                info("正在再次检查音符块...");

                setupTuneHitsMap();
                stage = Stage.Tune;
            }
        }
        else if (stage == Stage.SetUp) {
            scanForNoteblocks();
            if (scannedNoteblocks.isEmpty()) {
                error("找不到任何附近的音符块！");
                stop();
                return;
            }

            setupNoteblocksMap();
            if (noteBlockPositions.isEmpty()) {
                error("找不到有效的音符块来播放歌曲。");
                stop();
                return;
            }
            setupTuneHitsMap();
            stage = Stage.Tune;
        }
        else if (stage == Stage.Tune) {
            tune();
        }
        else if (stage == Stage.Playing) {
            if (!isPlaying) return;

            if (mc.player == null || currentTick > song.getLastTick()) {
                // Stop the song after it is finished
                onSongEnd();
                return;
            }

            if (song.getNotesMap().containsKey(currentTick)) {
                if (playingMode == PlayingMode.Preview) onTickPreview();
                else if (mc.player.getAbilities().creativeMode) {
                    error("你需要处于生存模式。");
                    stop();
                    return;
                }
                else onTickPlay();
            }

            currentTick++;

            updateStatus();
        }
    }

    /**
     * Set up a map of noteblocks positions
     */
    private void setupNoteblocksMap() {
        noteBlockPositions.clear();

        // Modifiable list of unique notes
        List<Note> uniqueNotesToUse = new ArrayList<>(song.getRequirements());
        // A map with noteblocks that have incorrect note level
        Map<NoteBlockInstrument, List<BlockPos>> incorrectNoteBlocks = new HashMap<>();

        // Check if there are already tuned noteblocks
        for (var entry : scannedNoteblocks.asMap().entrySet()) {
            Note note = entry.getKey();
            List<BlockPos> noteblocks = new ArrayList<>(entry.getValue());

            if (uniqueNotesToUse.contains(note)) {
                // Add correct noteblock position to a noteBlockPositions
                noteBlockPositions.put(note, noteblocks.removeFirst());
                uniqueNotesToUse.remove(note);
            }

            if (!noteblocks.isEmpty()) {
                // Add excess noteblocks for mapping process [note -> block pos]

                if (!incorrectNoteBlocks.containsKey(note.getInstrument())) {
                    incorrectNoteBlocks.put(note.getInstrument(), new ArrayList<>());
                }

                incorrectNoteBlocks.get(note.getInstrument()).addAll(noteblocks);
            }
        }

        // Map [note -> block pos]
        for (var entry : incorrectNoteBlocks.entrySet()) {
            List<BlockPos> positions = entry.getValue();

            if (mode.get() == NotebotUtils.NotebotMode.ExactInstruments) {
                NoteBlockInstrument inst = entry.getKey();

                List<Note> foundNotes = uniqueNotesToUse.stream()
                    .filter(note -> note.getInstrument() == inst)
                    .collect(Collectors.toList());

                if (foundNotes.isEmpty()) continue;

                for (BlockPos pos : positions) {
                    if (foundNotes.isEmpty()) break;

                    Note note = foundNotes.removeFirst();
                    noteBlockPositions.put(note, pos);

                    uniqueNotesToUse.remove(note);
                }
            } else {
                for (BlockPos pos : positions) {
                    if (uniqueNotesToUse.isEmpty()) break;

                    Note note = uniqueNotesToUse.removeFirst();
                    noteBlockPositions.put(note, pos);
                }
            }
        }

        if (!uniqueNotesToUse.isEmpty()) {
            for (Note note : uniqueNotesToUse) {
                warning("缺少音符: "+note.getInstrument()+", "+note.getNoteLevel());
            }
            warning(uniqueNotesToUse.size()+" 缺少音符！");
        }
    }

    /**
     * Set up a tune hits map which tells how many times player needs to
     * hit noteblock to obtain desired note level
     */
    private void setupTuneHitsMap() {
        tuneHits.clear();

        for (var entry : noteBlockPositions.entrySet()) {
            int targetLevel = entry.getKey().getNoteLevel();
            BlockPos blockPos = entry.getValue();

            BlockState blockState = mc.world.getBlockState(blockPos);
            int currentLevel = blockState.get(NoteBlock.NOTE);

            if (targetLevel != currentLevel) {
                tuneHits.put(blockPos, calcNumberOfHits(currentLevel, targetLevel));
            }
        }
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();

        // Open Song GUI
        WButton openSongGUI = table.add(theme.button("打开歌曲界面")).expandX().minWidth(100).widget();
        openSongGUI.action = () -> mc.setScreen(theme.notebotSongs());

        table.row();

        // Align Center
        WButton alignCenter = table.add(theme.button("居中对齐")).expandX().minWidth(100).widget();
        alignCenter.action = () -> {
            if (mc.player == null) return;
            Vec3d pos = Vec3d.ofBottomCenter(mc.player.getBlockPos());
            mc.player.setPosition(pos.x, mc.player.getY(), pos.z);
        };

        table.row();

        // Label
        status = table.add(theme.label(getStatus())).expandCellX().widget();

        // Pause
        WButton pause = table.add(theme.button(isPlaying ? "暂停" : "继续")).right().widget();
        pause.action = () -> {
            pause();
            pause.set(isPlaying ? "暂停" : "继续");
            updateStatus();
        };

        // Stop
        WButton stop = table.add(theme.button("停止")).right().widget();
        stop.action = this::stop;

        return table;
    }

    /**
     * Gets status for GUI
     *
     * @return A status
     */
    public String getStatus() {
        if (!this.isActive()) return "模块已禁用。";
        if (song == null) return "没有加载歌曲。";
        if (isPlaying) return String.format("正在播放歌曲。 %d/%d", currentTick, song.getLastTick());
        if (stage == Stage.Playing) return "准备播放。";
        if (stage == Stage.SetUp || stage == Stage.Tune || stage == Stage.WaitingToCheckNoteblocks) return "正在设置音符块。";
        else return String.format("阶段: %s。", stage.toString());
    }

    /**
     * Plays a song after loading and tuning
     */
    public void play() {
        if (mc.player == null) return;
        if (mc.player.getAbilities().creativeMode && playingMode != PlayingMode.Preview) {
            error("你需要处于生存模式。");
        } else if (stage == Stage.Playing) {
            isPlaying = true;
            info("播放中。");
        } else {
            error("没有加载歌曲。");
        }
    }

    public void pause() {
        if (!isActive()) toggle();
        if (isPlaying) {
            info("暂停中。");
            isPlaying = false;
        } else {
            info("继续中。");
            isPlaying = true;
        }
    }

    public void stop() {
        info("停止中。");
        disable();
        updateStatus();
    }

    public void onSongEnd() {
        if (autoPlay.get() && playingMode != PlayingMode.Preview) {
            playRandomSong();
        } else {
            stop();
        }
    }

    public void playRandomSong() {
        File[] files = MeteorClient.FOLDER.toPath().resolve("音符机器人").toFile().listFiles();
        if (files == null) return;

        File randomSong = files[ThreadLocalRandom.current().nextInt(files.length)];
        if (SongDecoders.hasDecoder(randomSong)) {
            loadSong(randomSong);
        } else {
            playRandomSong();
        }
    }

    public void disable() {
        resetVariables();
        if (!isActive()) toggle();
    }

    /**
     * Loads and plays song
     *
     * @param file Song supported by one of {@link SongDecoder}
     */
    public void loadSong(File file) {
        if (!isActive()) toggle();
        resetVariables();

        this.playingMode = PlayingMode.Noteblocks;
        if (!loadFileToMap(file, () -> stage = Stage.SetUp)) {
            onSongEnd();
            return;
        }
        updateStatus();
    }

    /**
     * Loads and previews the song
     *
     * @param file Song supported by one of {@link SongDecoder}
     */
    public void previewSong(File file) {
        if (!isActive()) toggle();
        resetVariables();

        this.playingMode = PlayingMode.Preview;
        loadFileToMap(file, () -> {
            stage = Stage.Playing;
            play();
        });
        updateStatus();
    }

    /**
     * Loads and plays song directly
     *
     * @param file Song supported by one of {@link SongDecoder}
     * @param callback Callback that is run when song has been loaded
     * @return Success
     */
    public boolean loadFileToMap(File file, Runnable callback) {
        if (!file.exists() || !file.isFile()) {
            error("文件未找到");
            return false;
        }

        if (!SongDecoders.hasDecoder(file)) {
            error("文件格式错误。解码器未找到。");
            return false;
        }

        info("加载歌曲 \"%s\".", FilenameUtils.getBaseName(file.getName()));

        // Start loading song
        loadingSongFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return SongDecoders.parse(file);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        loadingSongFuture.completeOnTimeout(null, 60, TimeUnit.SECONDS);

        stage = Stage.LoadingSong;
        long time1 = System.currentTimeMillis();
        loadingSongFuture.whenComplete((song ,ex) -> {
            if (ex == null) {
                // Song is null only when it times out
                if (song == null) {
                    error("加载歌曲 '" + FilenameUtils.getBaseName(file.getName()) + "' 超时。");
                    onSongEnd();
                    return;
                }

                this.song = song;
                long time2 = System.currentTimeMillis();
                long diff = time2 - time1;

                info("歌曲 '" + FilenameUtils.getBaseName(file.getName()) + "' 已加载到内存中！耗时 "+diff+"毫秒");
                callback.run();
            } else {
                if (ex instanceof CancellationException) {
                    error("加载歌曲 '" + FilenameUtils.getBaseName(file.getName()) + "' 被取消。");
                } else {
                    error("加载歌曲时发生错误 '" + FilenameUtils.getBaseName(file.getName()) + "'. 查看日志获取更多详情");
                    MeteorClient.LOG.error("加载歌曲时发生错误 '{}'", FilenameUtils.getBaseName(file.getName()), ex);
                    onSongEnd();
                }
            }
        });
        return true;
    }

    /**
     * Scans noteblocks nearby and adds them to the map
     */
    private void scanForNoteblocks() {
        if (mc.interactionManager == null || mc.world == null || mc.player == null) return;
        scannedNoteblocks.clear();
        int min = (int) (-mc.player.getBlockInteractionRange()) - 2;
        int max = (int) mc.player.getBlockInteractionRange() + 2;

        // Scan for noteblocks horizontally
        // 6^3 kek
        for (int y = min; y < max; y++) {
            for (int x = min; x < max; x++) {
                for (int z = min; z < max; z++) {
                    BlockPos pos = mc.player.getBlockPos().add(x, y + 1, z);

                    BlockState blockState = mc.world.getBlockState(pos);
                    if (blockState.getBlock() != Blocks.NOTE_BLOCK) continue;

                    // Copied from ServerPlayNetworkHandler#onPlayerInteractBlock
                    if (!mc.player.canInteractWithBlockAt(pos, 1)) continue;

                    if (!isValidScanSpot(pos)) continue;

                    Note note = NotebotUtils.getNoteFromNoteBlock(blockState, pos, mode.get(), instrumentDetectMode.get().getInstrumentDetectFunction());
                    scannedNoteblocks.put(note, pos);
                }
            }
        }
    }

    private void onTickPreview() {
        for (Note note : song.getNotesMap().get(currentTick)) {
            if (mode.get() == NotebotUtils.NotebotMode.ExactInstruments) {
                mc.player.playSound(note.getInstrument().getSound().value(), 2f, (float) Math.pow(2.0D, (note.getNoteLevel() - 12) / 12.0D));
            } else {
                mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(), 2f, (float) Math.pow(2.0D, (note.getNoteLevel() - 12) / 12.0D));
            }
        }
    }

    /**
     * Tunes noteblocks. This method is called per tick.
     */
    private void tune() {
        if (tuneHits.isEmpty()) {
            if (anyNoteblockTuned) {
                anyNoteblockTuned = false;
                waitTicks = checkNoteblocksAgainDelay.get();
                stage = Stage.WaitingToCheckNoteblocks;

                info("延迟检查音符块");
            } else {
                stage = Stage.Playing;
                info("加载完成。");
                play();
            }
            return;
        }

        if (ticks < tickDelay.get()) {
            return;
        }

        tuneBlocks();
        ticks = 0;
    }

    private void tuneBlocks() {
        if (mc.world == null || mc.player == null) {
            disable();
        }

        if (swingArm.get()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        int iterations = 0;
        var iterator = tuneHits.entrySet().iterator();

        // Concurrent tuning :o
        while (iterator.hasNext()){
            var entry = iterator.next();
            BlockPos pos = entry.getKey();
            int hitsNumber = entry.getValue();

            if (autoRotate.get()) {
                Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), 100, () -> tuneNoteblockWithPackets(pos));
            } else {
                this.tuneNoteblockWithPackets(pos);
            }

            clickedBlocks.add(pos);

            hitsNumber--;
            entry.setValue(hitsNumber);

            if (hitsNumber == 0) {
                iterator.remove();
            }

            iterations++;

            if (iterations == concurrentTuneBlocks.get()) return;
        }
    }

    private void tuneNoteblockWithPackets(BlockPos pos) {
        // We don't need to raycast here. Server handles this packet fine
        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(pos), Direction.DOWN, pos, false), 0));

        anyNoteblockTuned = true;
    }

    public void updateStatus() {
        if (status != null) status.set(getStatus());
    }

    private static int calcNumberOfHits(int from, int to) {
        if (from > to) {
            return (25 - from) + to;
        } else {
            return to - from;
        }
    }

    private void onTickPlay() {
        Collection<Note> notes = song.getNotesMap().get(this.currentTick);
        if (!notes.isEmpty()) {

            // Rotate player's head
            if (autoRotate.get()) {
                Optional<Note> firstNote = notes.stream().findFirst();
                if (firstNote.isPresent()) {
                    BlockPos firstPos = noteBlockPositions.get(firstNote.get());

                    if (firstPos != null) {
                        Rotations.rotate(Rotations.getYaw(firstPos), Rotations.getPitch(firstPos));
                    }
                }
            }

            // Swing arm
            if (swingArm.get()) {
                mc.player.swingHand(Hand.MAIN_HAND);
            }

            // Play notes
            for (Note note : notes) {
                BlockPos pos = noteBlockPositions.get(note);
                if (pos == null) {
                    return;
                }

                if (polyphonic.get()) {
                    playRotate(pos);
                } else {
                    this.playRotate(pos);
                }
            }
        }
    }

    private void playRotate(BlockPos pos) {
        if (mc.interactionManager == null) return;
        try {
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.DOWN, 0));
        } catch (NullPointerException ignored) {
        }
    }

    private boolean isValidScanSpot(BlockPos pos) {
        if (mc.world.getBlockState(pos).getBlock() != Blocks.NOTE_BLOCK) return false;
        return mc.world.getBlockState(pos.up()).isAir();
    }

    /**
     * Gets an NoteBlockInstrument from Note Map
     *
     * @param inst An instrument
     * @return A new instrument mapped by instrument given in parameters
     */
    @Nullable
    public NoteBlockInstrument getMappedInstrument(@NotNull NoteBlockInstrument inst) {
        if (mode.get() == NotebotUtils.NotebotMode.ExactInstruments) {
            NotebotUtils.OptionalInstrument optionalInstrument = (NotebotUtils.OptionalInstrument) sgNoteMap.getByIndex(inst.ordinal()).get();
            return optionalInstrument.toMinecraftInstrument();
        } else {
            return inst;
        }
    }

    private String beautifyText(String text) {
        text = text.toLowerCase(Locale.ROOT);

        String[] arr = text.split("_");
        StringBuilder sb = new StringBuilder();

        for (String s : arr) {
            sb.append(Character.toUpperCase(s.charAt(0)))
                .append(s.substring(1));
        }
        return sb.toString().trim();
    }

    public enum Stage {
        None,
        LoadingSong,
        SetUp,
        Tune,
        WaitingToCheckNoteblocks,
        Playing
    }

    public enum PlayingMode {
        None,
        Preview,
        Noteblocks
    }
}
