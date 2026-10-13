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

    private final List<CachedLine> cachedLines = new ArrayList<>();
    private ScoreboardObjective cachedObjective;
    private long cachedAtWorldTime = Long.MIN_VALUE;
    private String cachedTitle = "";
    private float cachedTitleWidth;
    private float cachedWidth = 150;
    private float cachedHeight = 20;
    private FontRenderer font;

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
        font.drawString(context.getMatrices(), cachedTitle, titleX, getY() + 4, -1);

        int yOffset = 13;
        for (CachedLine line : cachedLines) {
            font.drawString(context.getMatrices(), line.name, getX() + 4, getY() + yOffset, -1);
            font.drawString(context.getMatrices(), line.score, getX() + cachedWidth - line.scoreWidth - 4, getY() + yOffset, 0xFFFF5555);
            yOffset += 9;
        }
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
