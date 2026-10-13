// language: Java, file: Network.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.util;

import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.math.time.StopWatch;

/**
 * LoxeDLC Network port: server detection, TPS and PVP/teleport countdowns.
 * Data sources are all vanilla-visible — boss bar display names, the sidebar
 * objective, the server address/brand and WorldTimeUpdateS2CPacket (fed from
 * ClientPlayNetworkMixin).
 */
@Getter
@UtilityClass
public class Network implements QuickImports {
    /** Универсальный поиск PVP: PVP/PvP/ПВП в любом регистре, либо «режим»+«активен». */
    private static final Pattern PVP_PATTERN = Pattern.compile("[Pp][Vv][Pp]|[Пп][Вв][Пп]");
    /** Число + время (сек, сек., секунд, s, с). */
    private static final Pattern PVP_TIME_PATTERN = Pattern.compile("(\\d+)\\s*(?:сек\\.?|секунд(?:ы|а)?|СЕК\\.?|СЕКУНД(?:Ы|А)?|Секунд|s|с)(?=\\s|$|\\p{Punct})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** Число + время для телепорта. */
    private static final Pattern TELEPORT_TIME_PATTERN = Pattern.compile("(\\d+)\\s*(?:сек\\.?|секунд(?:ы|а)?|СЕК\\.?|СЕКУНД(?:Ы|А)?|Секунд|s|с)(?=\\s|$|\\p{Punct})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9a-f-or]");

    private final StopWatch pvpWatch = new StopWatch();
    public String server = "Vanilla";
    public float TPS = 20;
    public long timestamp;
    @Getter
    public int anarchy;
    @Getter
    public boolean pvpEnd;
    @Getter
    public boolean hasPvpCountdown;
    @Getter
    public int pvpCountdownSeconds;
    @Getter
    public boolean hasTeleportCountdown;
    @Getter
    public int teleportCountdownSeconds;
    @Getter
    public boolean hasPvpActive;

    /** BossBarHud.bossBars is private in Yarn — read it via the accessor mixin. */
    public Map<java.util.UUID, net.minecraft.entity.boss.ServerBossBar> bossBars() {
        return ((com.loadercode.tabvisuals.mixin.BossBarHudAccessor) mc.inGameHud.getBossBarHud()).getBossBars();
    }

    public void tick() {
        anarchy = getAnarchyMode();
        server = getServer();
        pvpEnd = inPvpEnd();
        if (inPvp()) pvpWatch.reset();
        updatePvpCountdown();
        updatePvpActive();
        updateTeleportCountdown();
    }

    /** Called on GameJoin: wipe the previous world's values (ClientPlayNetworkMixin). */
    public void reset() {
        server = "Vanilla";
        TPS = 20;
        timestamp = 0;
        anarchy = 0;
        pvpEnd = false;
        hasPvpCountdown = false;
        pvpCountdownSeconds = 0;
        hasTeleportCountdown = false;
        teleportCountdownSeconds = 0;
        hasPvpActive = false;
    }

    /** Босбар «PVP режим: Активен» — вечный PVP без таймера. */
    private void updatePvpActive() {
        hasPvpActive = false;
        if (mc.player == null || mc.inGameHud == null) return;
        for (var bossBar : bossBars().values()) {
            if (bossBar.getName() == null) continue;
            String name = stripFormatting(bossBar.getName().getString());
            if (PVP_PATTERN.matcher(name).find() && name.toLowerCase().contains("активен")) {
                hasPvpActive = true;
                break;
            }
        }
    }

    private void updatePvpCountdown() {
        hasPvpCountdown = false;
        pvpCountdownSeconds = 0;
        if (mc.player == null || mc.inGameHud == null) return;
        for (var bossBar : bossBars().values()) {
            if (bossBar.getName() == null) continue;
            String name = stripFormatting(bossBar.getName().getString());
            if (!isPvpRelated(name)) continue;
            Matcher m = PVP_TIME_PATTERN.matcher(name);
            if (m.find()) {
                pvpCountdownSeconds = Integer.parseInt(m.group(1));
                hasPvpCountdown = pvpCountdownSeconds > 0;
                break;
            }
        }
    }

    /** Текст считается PVP-режимом: PVP/ПВП или «режим»+«активен». */
    public static boolean isPvpRelated(String name) {
        if (PVP_PATTERN.matcher(name).find()) return true;
        String lower = name.toLowerCase();
        return lower.contains("режим") && (lower.contains("активен") || lower.contains("до конца"));
    }

    /** Убирает §-коды Minecraft из строки для надёжного парсинга. */
    public static String stripFormatting(String s) {
        if (s == null) return "";
        return FORMATTING.matcher(s).replaceAll("");
    }

    private void updateTeleportCountdown() {
        hasTeleportCountdown = false;
        teleportCountdownSeconds = 0;
        if (mc.player == null || mc.inGameHud == null) return;
        for (var bossBar : bossBars().values()) {
            if (bossBar.getName() == null) continue;
            String name = stripFormatting(bossBar.getName().getString());
            if (!name.toLowerCase().contains("телепортация")) continue;
            Matcher m = TELEPORT_TIME_PATTERN.matcher(name);
            if (m.find()) {
                teleportCountdownSeconds = Integer.parseInt(m.group(1));
                hasTeleportCountdown = teleportCountdownSeconds > 0;
                break;
            }
        }
    }

    /** Вызывается из ClientPlayNetworkMixin при WorldTimeUpdateS2CPacket. */
    public void onTimeUpdate() {
        long nanoTime = System.nanoTime();
        if (timestamp != 0) {
            float rawTPS = 20f * (1e9f / (nanoTime - timestamp));
            TPS = MathHelper.clamp(rawTPS, 0f, 20f);
        }
        timestamp = nanoTime;
    }

    public String getServer() {
        if (mc.player == null || mc.getNetworkHandler() == null
                || mc.getNetworkHandler().getServerInfo() == null
                || mc.getNetworkHandler().getBrand() == null) return "Vanilla";
        String serverIp = mc.getNetworkHandler().getServerInfo().address.toLowerCase();
        String brand = mc.getNetworkHandler().getBrand().toLowerCase();

        if (isFunTimeByAddress(serverIp)) return "FunTime";
        if (brand.contains("botfilter")) return "FunTime";
        else if (brand.contains("§6spooky§ccore")) return "SpookyTime";
        else if (serverIp.contains("funtime") || serverIp.contains("skytime") || serverIp.contains("space-times") || serverIp.contains("funsky")) return "CopyTime";
        else if (brand.contains("holyworld") || brand.contains("vk.com/idwok")) return "HolyWorld";
        else if (serverIp.contains("reallyworld")) return "ReallyWorld";
        else if (serverIp.contains("gulpvp")) return "GulPvP";
        return "Vanilla";
    }

    /** Режим из заголовка сайдбара (текст пишет сервер, формат серверный). */
    private int getAnarchyMode() {
        if (mc.world == null) return -1;
        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return -1;
        switch (server) {
            case "FunTime" -> {
                String[] string = objective.getDisplayName().getString().split("-");
                if (string.length > 1) return parseIntOrNegative(string[1]);
            }
            case "HolyWorld" -> {
                for (ScoreboardEntry scoreboardEntry : scoreboard.getScoreboardEntries(objective)) {
                    String text = Team.decorateName(scoreboard.getScoreHolderTeam(scoreboardEntry.owner()), scoreboardEntry.name()).getString();
                    if (!text.isEmpty()) {
                        String string = StringUtils.substringBetween(text, "#", " -◆-");
                        if (string != null && !string.isEmpty())
                            return parseIntOrNegative(string.replace(" (1.20)", ""));
                    }
                }
            }
        }
        return -1;
    }

    private static int parseIntOrNegative(String text) {
        String digits = text.trim();
        if (digits.isEmpty()) return -1;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public boolean isPvp() {
        return !pvpWatch.finished(500);
    }

    private boolean inPvp() {
        return bossBars().values().stream()
                .anyMatch(b -> b.getName() != null && isPvpRelated(stripFormatting(b.getName().getString())));
    }

    private boolean inPvpEnd() {
        return bossBars().values().stream()
                .anyMatch(b -> {
                    if (b.getName() == null) return false;
                    String s = stripFormatting(b.getName().getString());
                    return isPvpRelated(s) && (s.contains("0") || s.contains("1"));
                });
    }

    public String getWorldType() {
        // no assert: this runs on the HUD path where the world may be absent
        return mc.world == null ? "world" : mc.world.getRegistryKey().getValue().getPath();
    }

    public boolean isFunTime() {
        return server.equals("FunTime");
    }

    /** Проверка по адресу: funtime.su или поддомен.funtime.su. */
    public static boolean isFunTimeByAddress(String address) {
        if (address == null || address.isEmpty()) return false;
        String host = address.toLowerCase().split(":")[0].trim();
        return host.equals("funtime.su") || host.endsWith(".funtime.su");
    }

    public boolean isAresMine() {
        return server.equals("aresmine");
    }

    /** Проверка на спавне (FunTime/HolyWorld и т.д.). */
    public boolean spawn() {
        if (mc.player == null || mc.world == null) return false;
        BlockPos pos = mc.player.getBlockPos();
        Block below = mc.world.getBlockState(pos.down(1)).getBlock();
        Block atZero = mc.world.getBlockState(new BlockPos(pos.getX(), 0, pos.getZ())).getBlock();
        if (isFunTime() && mc.world.getDifficulty() == net.minecraft.world.Difficulty.NORMAL) return false;
        if (isFunTime() || server.equals("SpookyTime")) return below == Blocks.AIR || atZero == Blocks.AIR;
        if (mc.world.getRegistryKey() == World.OVERWORLD) return atZero == Blocks.BEDROCK || below == Blocks.BEDROCK;
        return false;
    }
}
