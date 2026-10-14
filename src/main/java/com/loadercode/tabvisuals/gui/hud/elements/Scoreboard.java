/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Scoreboard extends AbstractDraggable {
    private static final long CACHE_TICKS = 5L;
    private static final int SCORE_COLOR = 0xFFFF5555;

    // Вход панели: строки выезжают слева со стаггером; прогресс считается от
    // времени (мс), поэтому скорость не зависит от FPS.
    private static final long REVEAL_GAP_MS = 250L;
    private static final long REVEAL_TITLE_MS = 200L;
    private static final long REVEAL_ROW_DELAY_MS = 40L;
    private static final long REVEAL_ROW_MS = 260L;
    private static final float REVEAL_SLIDE_PX = 6f;

    private final List<CachedLine> cachedLines = new ArrayList<>();
    private ScoreboardObjective cachedObjective;
    private long cachedAtWorldTime = Long.MIN_VALUE;
    private String cachedTitle = "";
    private float cachedTitleWidth;
    private float cachedWidth = 150;
    private float cachedHeight = 20;
    private FontRenderer font;
    private long revealStartMs;
    private long lastDrawMs;

    public Scoreboard() {
        super("Scoreboard", 10, 100, 150, 200, true);
    }

    @Override
    public boolean visible() {
        if (!com.loadercode.tabvisuals.gui.hud.Hud.getInstance().interfaceSettings.isSelected("Scoreboard")) {
            return false;
        }
        if (mc.world == null) return false;

        ScoreboardObjective objective = mc.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        return objective != null;
    }

    @Override
    public void drawDraggable(DrawContext context) {
        if (mc.world == null) return;
        ScoreboardObjective objective = mc.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return;

        net.minecraft.scoreboard.Scoreboard scoreboard = mc.world.getScoreboard();
        long worldTime = mc.world.getTime();
        if (cachedObjective != objective || worldTime < cachedAtWorldTime || worldTime - cachedAtWorldTime >= CACHE_TICKS) {
            rebuildCache(scoreboard, objective, worldTime);
        }

        setWidth((int) cachedWidth);
        setHeight((int) cachedHeight);
        if (font == null) {
            font = Fonts.getSize(16);
        }

        Hud hud = Hud.getInstance();
        if (hud != null && hud.shouldDrawHudBlur()) {
            QuickImports.blur.render(ShapeProperties.create(context.getMatrices(), getX(), getY(), cachedWidth, cachedHeight)
                    .color(ColorAssist.getBlurColor())
                    .quality(hud.getBlurQuality())
                    .round(4)
                    .build());
        }

        QuickImports.rectangle.render(ShapeProperties.create(context.getMatrices(), getX(), getY(), cachedWidth, cachedHeight)
                .color(ColorAssist.multAlpha(ColorAssist.getRectangleColor(), 100f / 75f))
                .round(4)
                .build());

        float titleX = getX() + (cachedWidth - cachedTitleWidth) / 2;

        // пауза в отрисовке (выключение/смена мира) перезапускает вход;
        // обновление счётов при видимой панели анимацию не трогает
        long nowMs = System.currentTimeMillis();
        if (lastDrawMs <= 0L || nowMs - lastDrawMs > REVEAL_GAP_MS) {
            revealStartMs = nowMs;
        }
        lastDrawMs = nowMs;

        float titleP = ease_out_cubic(clamp01((nowMs - revealStartMs) / (float) REVEAL_TITLE_MS));
        float titleSlide = (1f - titleP) * REVEAL_SLIDE_PX;
        font.drawString(context.getMatrices(), cachedTitle, titleX - titleSlide, getY() + 4,
                ColorAssist.multAlpha(-1, titleP));

        int yOffset = 13;
        int rowIndex = 0;
        for (CachedLine line : cachedLines) {
            float rowP = ease_out_cubic(clamp01(
                    (nowMs - revealStartMs - rowIndex * REVEAL_ROW_DELAY_MS) / (float) REVEAL_ROW_MS));
            float slide = (1f - rowP) * REVEAL_SLIDE_PX;
            font.drawString(context.getMatrices(), line.name, getX() + 4 - slide, getY() + yOffset,
                    ColorAssist.multAlpha(-1, rowP));
            font.drawString(context.getMatrices(), line.score, getX() + cachedWidth - line.scoreWidth - 4 - slide,
                    getY() + yOffset, ColorAssist.multAlpha(SCORE_COLOR, rowP));
            yOffset += 9;
            rowIndex++;
        }
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }

    /** easeOutCubic: 0→1 за REVEAL_ROW_MS, чисто временная (не кадровая) шкала. */
    private static float ease_out_cubic(float p) {
        float q = 1f - p;
        return 1f - q * q * q;
    }

    private void rebuildCache(net.minecraft.scoreboard.Scoreboard scoreboard, ScoreboardObjective objective, long worldTime) {
        List<ScoreboardEntry> entries = scoreboard.getScoreboardEntries(objective).stream()
                .filter(entry -> !entry.hidden())
                .sorted(
                        Comparator.comparingInt(ScoreboardEntry::value).reversed()
                                .thenComparing(e -> e.name().getString(), String.CASE_INSENSITIVE_ORDER)
                )
                .limit(15)
                .toList();

        cachedObjective = objective;
        cachedAtWorldTime = worldTime;
        cachedTitle = objective.getDisplayName().getString();
        if (font == null) {
            font = Fonts.getSize(16);
        }
        cachedTitleWidth = font.getStringWidth(cachedTitle);
        cachedLines.clear();

        float maxWidth = cachedTitleWidth;
        for (ScoreboardEntry entry : entries) {
            Team team = scoreboard.getScoreHolderTeam(entry.owner());
            Text name = Team.decorateName(team, entry.name());

            StringBuilder sb = new StringBuilder();
            font.findStyle(sb, name);
            String formattedName = sb.toString();

            String score = String.valueOf(entry.value());
            float scoreWidth = font.getStringWidth(score);
            float lineWidth = font.getStringWidth(formattedName + ": " + score);
            if (lineWidth > maxWidth) maxWidth = lineWidth;

            cachedLines.add(new CachedLine(formattedName, score, scoreWidth));
        }

        cachedWidth = maxWidth + 10;
        cachedHeight = (cachedLines.size() + 1) * 9 + 4;
    }

    private static class CachedLine {
        private final String name;
        private final String score;
        private final float scoreWidth;

        private CachedLine(String name, String score, float scoreWidth) {
            this.name = name;
            this.score = score;
            this.scoreWidth = scoreWidth;
        }
    }
}
