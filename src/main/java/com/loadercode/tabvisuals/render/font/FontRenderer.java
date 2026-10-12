/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.font;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import com.loadercode.tabvisuals.render.font.entry.DrawEntry;
import com.loadercode.tabvisuals.render.font.glyph.Glyph;
import com.loadercode.tabvisuals.render.font.glyph.GlyphMap;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.math.calc.Calculate;

import java.awt.Color;
import java.awt.Font;

import static net.minecraft.client.render.VertexFormat.DrawMode.QUADS;
import static net.minecraft.client.render.VertexFormats.POSITION_TEXTURE_COLOR;

@Setter
@Accessors(chain = true)
public class FontRenderer implements QuickImports {
    private static final int WIDTH_CACHE_MAX = 512;

    private final Object2ObjectMap<Identifier, ObjectList<DrawEntry>> GLYPH_PAGE_CACHE = new Object2ObjectOpenHashMap<>();
    /**
     * Пул записей глифов. Страницы отдаются обратно в пул на каждой отрисовке
     * (см. {@link #flushGlyphs}), поэтому между кадрами не создаётся ни одного
     * нового {@code DrawEntry} — только присваивание полей. Пул экземплярный,
     * потому что и страницы экземплярные: каждый шрифт держит свой кэш.
     * Потокобезопасность не нужна — рисование идёт только из рендер-потока.
     */
    private final ObjectList<DrawEntry> drawEntryPool = new ObjectArrayList<>();
    private final StringBuilder colorEscapeBuffer = new StringBuilder(24);
    private final ObjectList<GlyphMap> maps = new ObjectArrayList<>();
    private final Char2ObjectOpenHashMap<Glyph> glyphCache = new Char2ObjectOpenHashMap<>();
    private final Object2FloatOpenHashMap<String> widthCache = new Object2FloatOpenHashMap<>();
    @Getter
    private Font font;

    public FontRenderer(Font font, float sizePx) {
        init(font, sizePx);
        widthCache.defaultReturnValue(Float.NaN);
    }

    /**
     * No-op: deferred text batching was reverted (caused intermittent missing/laggy glyphs
     * when flushed under a different shader alpha / matrix). Call sites kept for safety.
     */
    public static void flushDeferred() {
    }

    private void init(Font font, float sizePx) {
        this.font = font.deriveFont(sizePx * 2);
    }

    private GlyphMap generateMap(char from, char to) {
        GlyphMap glyphMap = new GlyphMap(from, to, this.font, randomIdentifier(), 5);
        maps.add(glyphMap);
        return glyphMap;
    }

    private Glyph locateGlyph(char glyph) {
        Glyph cached = glyphCache.get(glyph);
        if (cached != null) {
            return cached;
        }

        for (GlyphMap map : maps) {
            if (map.contains(glyph)) {
                Glyph found = map.getGlyph(glyph);
                glyphCache.put(glyph, found);
                return found;
            }
        }

        char base = (char) Calculate.floorNearestMulN(glyph, 128);
        Glyph created = generateMap(base, (char) (base + 128)).getGlyph(glyph);
        glyphCache.put(glyph, created);
        return created;
    }

    /** Text pipeline hook point — the name-protect / text-factory layer went out with the feature stack. */
    private static String applyTextFactory(String text) {
        return text;
    }

    public void drawText(MatrixStack matrix, Text text, double x, double y) {
        StringBuilder sb = new StringBuilder();
        findStyle(sb,text);
        drawString(matrix, sb.toString(), x, y, ColorAssist.getText());
    }

    public void findStyle(StringBuilder sb, Text component) {
        Style style = component.getStyle();
        if (component.getSiblings().isEmpty()) {
            if (style.getColor() != null) sb.append(ColorAssist.formatting(style.getColor().getRgb()));
            sb.append(component.getString()).append(Formatting.RESET);
        } else component.getWithStyle(style).forEach(text -> findStyle(sb,text));
    }

    public void drawStringWithScroll(MatrixStack matrix, java.lang.String text, double x, double y, float width, int color) {
        java.lang.String separation = "  |  ";
        float textWidth = getStringWidth(text + separation);

        if (textWidth - width < 10) {
            drawString(matrix,text,x,y,color);
            return;
        }

        drawString(matrix,text + separation + text, x - Calculate.textScrolling(textWidth), y, color);
    }

    public void drawString(MatrixStack matrix, java.lang.String text, double x, double y, int color) {
        text = applyTextFactory(text);
        if (text == null || text.isEmpty()) {
            return;
        }
        collectString(text, 0f, 0f, color);
        flushGlyphs(matrix, x, y);
    }

    /**
     * Outline + fill in a single glyph flush (one texture bind / draw call set).
     * Outline offsets are in the same screen space as {@code x}/{@code y}.
     */
    public void drawStringOutlined(MatrixStack matrix, java.lang.String text, double x, double y,
                                   int color, int outlineColor, float outline) {
        collectOutlined(text, 0f, 0f, color, outlineColor, outline, true);
        flushGlyphs(matrix, x, y);
    }

    /**
     * Many short outlined strings → one flush. Positions use the same space as {@link #drawString}.
     * Skips NameProtect (for trusted labels like hotbar slot digits).
     */
    public void drawOutlinedBatch(MatrixStack matrix, int color, int outlineColor, float outline,
                                  String[] texts, float[] xs, float[] ys, int count) {
        if (count <= 0) return;
        float originX = xs[0];
        float originY = ys[0];
        for (int i = 0; i < count; i++) {
            float dx = (xs[i] - originX) * 2f;
            float dy = (ys[i] - originY) * 2f;
            collectOutlined(texts[i], dx, dy, color, outlineColor, outline, false);
        }
        flushGlyphs(matrix, originX, originY);
    }

    private void collectOutlined(String text, float baseDx, float baseDy, int color, int outlineColor,
                                 float outline, boolean applyFactory) {
        if (applyFactory) {
            text = applyTextFactory(text);
        }
        // drawGlyphs scales by 0.5, so screen outline → glyph-space offset
        float g = outline * 2f;
        collectString(text, baseDx - g, baseDy, outlineColor);
        collectString(text, baseDx + g, baseDy, outlineColor);
        collectString(text, baseDx, baseDy - g, outlineColor);
        collectString(text, baseDx, baseDy + g, outlineColor);
        collectString(text, baseDx, baseDy, color);
    }

    private void collectString(String text, float dx, float dy, int color) {
        if (text == null || text.isEmpty()) return;

        int len = text.length();
        float xOffset = dx;
        float yOffset = dy;
        int lineStart = 0;
        // Общий на весь рендер-буфер: на строке с ⏏ раньше создавался новый
        // StringBuilder, а collectString зовётся пять раз на обведённый текст.
        StringBuilder stringColor = colorEscapeBuffer;
        stringColor.setLength(0);
        boolean colorFormat = false;
        boolean textColor = false;
        int clr = color;

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            if (c == '§') {
                colorFormat = true;
                continue;
            } else if (colorFormat) {
                colorFormat = false;
                char c1 = Character.toUpperCase(c);
                if (ColorAssist.colorCodes.containsKey(c1)) {
                    clr = 0xFF000000 | ColorAssist.colorCodes.get(c1);
                } else if (c1 == 'R') {
                    clr = color;
                }
                continue;
            }

            if (c == '⏏') {
                if (textColor) {
                    // ⏏ попадает и в чужой текст (ник/чат, символ есть в CHARSET):
                    // без try/catch нечисловое содержимое роняло NumberFormatException
                    // прямо посреди кадра отрисовки текста.
                    try {
                        clr = new Color(Integer.parseInt(stringColor.toString())).getRGB();
                    } catch (NumberFormatException ignored) {
                        // оставляем предыдущий цвет
                    }
                    stringColor.setLength(0);
                }
                textColor = !textColor;
                continue;
            } else if (textColor) {
                stringColor.append(c);
                continue;
            }

            if (c == '\n') {
                yOffset += getStringHeight(text.substring(lineStart, i)) - 2;
                xOffset = dx;
                lineStart = i + 1;
                continue;
            }

            Glyph glyph = locateGlyph(c);
            if (glyph != null) {
                if (glyph.value() != ' ') {
                    Identifier i1 = glyph.owner().bindToTexture;
                    DrawEntry entry = obtainDrawEntry(xOffset, yOffset, clr, glyph);
                    GLYPH_PAGE_CACHE.computeIfAbsent(i1, integer -> new ObjectArrayList<>()).add(entry);
                }
                xOffset += glyph.width();
            }
        }
    }

    /** Берёт запись глифа из пула либо создаёт первую — дальше только присваивание. */
    private DrawEntry obtainDrawEntry(float atX, float atY, int color, Glyph glyph) {
        int n = drawEntryPool.size();
        DrawEntry entry = n > 0 ? drawEntryPool.remove(n - 1) : new DrawEntry();
        return entry.reset(atX, atY, color, glyph);
    }

    private void flushGlyphs(MatrixStack matrix, double x, double y) {
        if (!hasPendingGlyphs()) {
            return;
        }
        drawGlyphs(matrix, x, y);
        // Страницы (и capacity списков) сохраняются, а записи уходят в пул: следующий
        // кадр обойдётся без новых DrawEntry — только присваивания полей.
        for (ObjectList<DrawEntry> list : GLYPH_PAGE_CACHE.values()) {
            for (int i = 0, n = list.size(); i < n; i++) {
                drawEntryPool.add(list.get(i));
            }
            list.clear();
        }
    }

    private boolean hasPendingGlyphs() {
        for (ObjectList<DrawEntry> list : GLYPH_PAGE_CACHE.values()) {
            if (!list.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public void drawGradientString(MatrixStack matrix, java.lang.String text, double x, double y, int colorStart, int colorEnd) {
        text = applyTextFactory(text);
        // drawString() ниже null-проверку делает, а этот метод шёл прямо в
        // length() — отменённый фабрикой текст падал NPE посреди кадра.
        if (text == null || text.isEmpty()) {
            return;
        }

        int len = text.length();
        float xOffset = 0;
        float yOffset = 0;
        int lineStart = 0;

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                yOffset += getStringHeight(text.substring(lineStart, i)) - 2;
                xOffset = 0;
                lineStart = i + 1;
                continue;
            }
            Glyph glyph = locateGlyph(c);
            if (glyph != null) {
                if (glyph.value() != ' ') {
                    float t = len <= 1 ? 0f : (float) i / (len - 1);
                    int color = interpolateColor(colorStart, colorEnd, t);
                    Identifier i1 = glyph.owner().bindToTexture;
                    DrawEntry entry = obtainDrawEntry(xOffset, yOffset, color, glyph);
                    GLYPH_PAGE_CACHE.computeIfAbsent(i1, integer -> new ObjectArrayList<>()).add(entry);
                }
                xOffset += glyph.width();
            }
        }

        flushGlyphs(matrix, x, y);
    }

    private void drawGlyphs(MatrixStack matrix, double x, double y) {
        matrix.push();
        matrix.translate(x, y - 3F, 0);
        matrix.scale(0.5F, 0.5F, 1);
        Matrix4f matrix4f = matrix.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        for (var entry : GLYPH_PAGE_CACHE.object2ObjectEntrySet()) {
            ObjectList<DrawEntry> entries = entry.getValue();
            if (entries.isEmpty()) continue;

            RenderSystem.setShaderTexture(0, entry.getKey());
            BufferBuilder buffer = Tessellator.getInstance().begin(QUADS, POSITION_TEXTURE_COLOR);
            for (DrawEntry drawEntry : entries) {
                float x1 = drawEntry.atX();
                float y1 = drawEntry.atY();

                Glyph glyph = drawEntry.toDraw();
                GlyphMap glyphMap = glyph.owner();

                float width = glyph.width();
                float height = glyph.height();
                float invW = 1.0f / glyphMap.width;
                float invH = 1.0f / glyphMap.height;

                float u1 = glyph.u() * invW;
                float v1 = glyph.v() * invH;
                float u2 = (glyph.u() + width) * invW;
                float v2 = (glyph.v() + height) * invH;

                int color = drawEntry.color();

                buffer.vertex(matrix4f, x1, y1 + height, 0).texture(u1, v2).color(color);
                buffer.vertex(matrix4f, x1 + width, y1 + height, 0).texture(u2, v2).color(color);
                buffer.vertex(matrix4f, x1 + width, y1, 0).texture(u2, v1).color(color);
                buffer.vertex(matrix4f, x1, y1, 0).texture(u1, v1).color(color);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
        RenderSystem.disableBlend();
        matrix.pop();
    }

    public void drawCenteredString(MatrixStack stack, java.lang.String s, double x, double y, int color) {
        drawString(stack, s, (int) (x - getStringWidth(s) / 2f), (float) y, color);
    }

    public float getStringWidth(Text text) {
        return text != null ? getStringWidth(text.getString()) : 0;
    }

    public float getStringWidth(java.lang.String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        text = applyTextFactory(text);

        float cached = widthCache.getFloat(text);
        if (!Float.isNaN(cached)) {
            return cached;
        }

        float currentLine = 0;
        float maxPreviousLines = 0;
        boolean ignore = false;
        boolean textColor = false;

        for (char c : text.toCharArray()) {
            if (ignore) {
                ignore = false;
                continue;
            } else if (c == '§') {
                ignore = true;
                continue;
            } else if (c == '⏏') {
                textColor = !textColor;
                continue;
            } else if (textColor) {
                continue;
            } else if (c == '\n') {
                maxPreviousLines = Math.max(currentLine, maxPreviousLines);
                currentLine = 0;
                continue;
            }

            Glyph glyph = locateGlyph(c);
            currentLine += glyph == null ? 0 : glyph.width();
        }

        float width = Math.max(currentLine, maxPreviousLines) / 2;
        if (widthCache.size() >= WIDTH_CACHE_MAX) {
            widthCache.clear();
        }
        widthCache.put(text, width);
        return width;
    }

    public float getStringHeight(Text text) {
        return getStringHeight(text.getString());
    }

    public float getStringHeight(java.lang.String text) {
        float currentLine = 0;
        float previous = 0;

        for (char c : (text.isEmpty() ? " " : text).toCharArray()) {
            if (c == '\n') {
                currentLine = (currentLine == 0 ? locateGlyph(' ').height() : currentLine);
                previous += currentLine;
                currentLine = 0;
                continue;
            }
            Glyph glyph = locateGlyph(c);
            currentLine = Math.max(glyph == null ? 0 : glyph.height(), currentLine);
        }

        return currentLine + previous;
    }

    public java.lang.String trimToWidth(java.lang.String text, int maxWidth, boolean backwards) {
        return backwards ? trimToWidthBackwards(text, maxWidth) : trimToWidth(text, maxWidth);
    }

    public java.lang.String trimToWidth(java.lang.String text, int maxWidth) {
        return text.substring(0, getTrimmedLength(text, maxWidth));
    }

    private int getTrimmedLength(java.lang.String text, int maxWidth) {
        WidthLimitingVisitor visitor = new WidthLimitingVisitor(maxWidth);
        visitForwards(text, visitor);
        return visitor.getLength();
    }

    public java.lang.String trimToWidthBackwards(java.lang.String text, int maxWidth) {
        MutableFloat widthLeft = new MutableFloat(maxWidth);
        MutableInt trimmedLength = new MutableInt(text.length());

        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            Glyph glyph = locateGlyph(c);
            if (glyph != null) {
                widthLeft.subtract(glyph.width());
                if (widthLeft.floatValue() < 0) {
                    trimmedLength.setValue(i + 1);
                    break;
                }
            }
        }

        return text.substring(trimmedLength.intValue());
    }

    private void visitForwards(String text, CharacterVisitor visitor) {
        int length = text.length();

        for (int i = 0; i < length; ++i) {
            char c = text.charAt(i);
            if (!visitor.accept(i, c)) {
                return;
            }
        }

    }

    private int interpolateColor(int colorStart, int colorEnd, float t) {
        float startAlpha = (colorStart >> 24 & 255) / 255.0F;
        float startRed = (colorStart >> 16 & 255) / 255.0F;
        float startGreen = (colorStart >> 8 & 255) / 255.0F;
        float startBlue = (colorStart & 255) / 255.0F;

        float endAlpha = (colorEnd >> 24 & 255) / 255.0F;
        float endRed = (colorEnd >> 16 & 255) / 255.0F;
        float endGreen = (colorEnd >> 8 & 255) / 255.0F;
        float endBlue = (colorEnd & 255) / 255.0F;

        float alpha = startAlpha + t * (endAlpha - startAlpha);
        float red = startRed + t * (endRed - startRed);
        float green = startGreen + t * (endGreen - startGreen);
        float blue = startBlue + t * (endBlue - startBlue);

        return ((int) (alpha * 255.0F) << 24) | ((int) (red * 255.0F) << 16) | ((int) (green * 255.0F) << 8) | (int) (blue * 255.0F);
    }

    private class WidthLimitingVisitor implements CharacterVisitor {
        private float widthLeft;
        @Getter
        private int length;

        public WidthLimitingVisitor(float maxWidth) {
            this.widthLeft = maxWidth;
        }

        @Override
        public boolean accept(int index, char c) {
            Glyph glyph = locateGlyph(c);
            if (glyph != null) {
                widthLeft -= glyph.width();
                if (widthLeft >= 0) {
                    length = index + 1;
                    return true;
                }
            }
            return false;
        }

    }

    @FunctionalInterface
    public interface CharacterVisitor {
        boolean accept(int index, char codePoint);
    }

    /** Identifier path segment must match [a-z0-9/._-] — lowercase digits only. */
    private static final String RANDOM_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    @Contract(value = "-> new", pure = true)
    public static @NotNull Identifier randomIdentifier() {
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) sb.append(RANDOM_ALPHABET.charAt(rnd.nextInt(RANDOM_ALPHABET.length())));
        return Identifier.of("tabvisuals", "temp/" + sb);
    }

    @Contract(value = "_ -> new", pure = true)
    public static int @NotNull [] RGBIntToRGB(int in) {
        int red = in >> 8 * 2 & 0xFF;
        int green = in >> 8 & 0xFF;
        int blue = in & 0xFF;
        return new int[]{red, green, blue};
    }
}
