/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.font.glyph;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import com.loadercode.tabvisuals.render.font.entry.DrawEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.awt.RenderingHints.*;
import static net.minecraft.client.texture.NativeImage.Format.*;

public class GlyphMap {
    private final Char2ObjectArrayMap<Glyph> glyphs = new Char2ObjectArrayMap<>();
    private final char fromIncl, toExcl;
    private final Font font;
    public final Identifier bindToTexture;
    private final int pixelPadding;
    public int width, height;
    private volatile boolean generated = false;
    /** Glyphs queued for the next flush of the owning FontRenderer (render thread only). */
    public final ObjectArrayList<DrawEntry> batch = new ObjectArrayList<>();
    private Font lastFallback;
    private Font defaultFallback;

    public GlyphMap(char from, char to, Font font, Identifier identifier, int padding) {
        this.fromIncl = from;
        this.toExcl = to;
        this.font = font;
        this.bindToTexture = identifier;
        this.pixelPadding = padding;
    }

    public Glyph getGlyph(char c) {
        if (!generated) generate();
        return glyphs.get(c);
    }

    public boolean contains(char c) {
        return c >= fromIncl && c < toExcl;
    }

    /** Resolved once per class-load: enumerating GraphicsEnvironment.getAllFonts()
     *  walks every installed system font and is far too slow to call per glyph in
     *  the render loop (it froze on the first unsupported character). */
    private static volatile Font[] systemFonts;

    private Font getFontForGlyph(char c) {
        if (font.canDisplay(c)) return font;
        // Neighbouring code points almost always share the fallback font — try
        // it before walking every installed font again.
        Font last = lastFallback;
        if (last != null && last.canDisplay(c)) return last;
        // Nothing can draw these; scanning all system fonts for each of them
        // froze the frame for whole pages of unassigned code points.
        if (!Character.isDefined(c) || Character.isISOControl(c) || Character.isSurrogate(c)
                || Character.getType(c) == Character.PRIVATE_USE) {
            return font;
        }
        Font[] all = systemFonts;
        if (all == null) {
            all = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
            systemFonts = all;
        }
        for (Font f : all) {
            if (f.canDisplay(c)) {
                Font derived = new Font(f.getFontName(), font.getStyle(), font.getSize());
                lastFallback = derived;
                return derived;
            }
        }
        if (defaultFallback == null) {
            defaultFallback = new Font("SansSerif", font.getStyle(), font.getSize());
        }
        return defaultFallback;
    }

    /** Frees the atlas texture; the map must not be drawn afterwards. */
    public void destroy() {
        if (!generated) return;
        Identifier id = bindToTexture;
        if (RenderSystem.isOnRenderThread()) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
        } else {
            RenderSystem.recordRenderCall(() -> MinecraftClient.getInstance().getTextureManager().destroyTexture(id));
        }
    }

    // synchronized: two threads measuring text at once would both rasterize
    // and register the same page (and clear glyphs under each other).
    public synchronized void generate() {
        if (generated) return;

        int range = toExcl - fromIncl - 1;
        int charsVert = (int) (Math.ceil(Math.sqrt(range)) * 1.5);
        glyphs.clear();

        int generatedChars = 0;
        int charNX = 0;
        int maxX = 0, maxY = 0;
        int currentX = 0, currentY = 0;
        int currentRowMaxY = 0;

        List<Glyph> glyphs1 = new ArrayList<>();
        List<Font> glyphFonts = new ArrayList<>();
        AffineTransform affineTransform = new AffineTransform();
        FontRenderContext fontRenderContext = new FontRenderContext(affineTransform, true, false);
        // Дробные метрики — только для advance пера: целочисленные Java2D
        // округляют шаг вверх и накапливают ошибку по строке.
        FontRenderContext exactRenderContext = new FontRenderContext(affineTransform, true, true);

        while (generatedChars <= range) {
            char currentChar = (char) (fromIncl + generatedChars);
            Font font = getFontForGlyph(currentChar);
            glyphFonts.add(font);

            Rectangle2D stringBounds = font.getStringBounds(String.valueOf(currentChar), fontRenderContext);
            Rectangle2D exactBounds = font.getStringBounds(String.valueOf(currentChar), exactRenderContext);

            int width = (int) Math.ceil(stringBounds.getWidth());
            int height = (int) Math.ceil(stringBounds.getHeight());
            float advance = Math.max(0f, (float) exactBounds.getWidth());
            generatedChars++;

            if (charNX >= charsVert) {
                currentX = 0;
                currentY += currentRowMaxY + pixelPadding;
                charNX = 0;
                currentRowMaxY = 0;
            }

            currentRowMaxY = Math.max(currentRowMaxY, height);
            glyphs1.add(new Glyph(currentX, currentY, width, height, advance, currentChar, this));

            // Габариты атласа считаем по ФАКТИЧЕСКОМУ месту укладки (после
            // возможного переноса строки): раньше обновление шло до переноса,
            // и высота текстуры недобирала высоту последнего ряда — нижние
            // пиксели глифов последней строки обрезались при загрузке.
            maxX = Math.max(maxX, currentX + width);
            maxY = Math.max(maxY, currentY + height);

            currentX += width + pixelPadding;
            charNX++;
        }

        BufferedImage bufferedImage = new BufferedImage(
                Math.max(maxX + pixelPadding, 1),
                Math.max(maxY + pixelPadding, 1),
                BufferedImage.TYPE_INT_ARGB
        );

        width = bufferedImage.getWidth();
        height = bufferedImage.getHeight();

        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.setColor(new Color(255, 255, 255, 0));
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.WHITE);

        g2d.setRenderingHint(KEY_FRACTIONALMETRICS, VALUE_FRACTIONALMETRICS_OFF);
        g2d.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(KEY_TEXT_ANTIALIASING, VALUE_TEXT_ANTIALIAS_ON);

        try {
            Font current = null;
            FontMetrics fontMetrics = null;
            for (int i = 0, n = glyphs1.size(); i < n; i++) {
                Glyph glyph = glyphs1.get(i);
                Font glyphFont = glyphFonts.get(i);
                if (glyphFont != current) {
                    current = glyphFont;
                    g2d.setFont(glyphFont);
                    fontMetrics = g2d.getFontMetrics();
                }
                g2d.drawString(String.valueOf(glyph.value()), glyph.u(), glyph.v() + fontMetrics.getAscent());
                glyphs.put(glyph.value(), glyph);
            }
        } finally {
            g2d.dispose();
        }

        registerBufferedImageTexture(bindToTexture, bufferedImage);
        generated = true;
    }

    public static void registerBufferedImageTexture(Identifier textureIdentifier, BufferedImage inputImage) {
        int imageWidth = inputImage.getWidth();
        int imageHeight = inputImage.getHeight();

        NativeImage nativeImage = new NativeImage(RGBA, imageWidth, imageHeight, false);

        try {
            IntBuffer buffer = MemoryUtil.memIntBuffer(nativeImage.pointer, nativeImage.getWidth() * nativeImage.getHeight());

            // Row-wise getRGB instead of five ColorModel calls per pixel:
            // same sRGB ARGB values, an order of magnitude less work per page.
            int[] row = new int[imageWidth];
            for (int y = 0; y < imageHeight; y++) {
                inputImage.getRGB(0, y, imageWidth, 1, row, 0, imageWidth);
                for (int x = 0; x < imageWidth; x++) {
                    int argb = row[x];
                    // ARGB -> ABGR (NativeImage byte order)
                    row[x] = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
                }
                buffer.put(row);
            }
        } catch (Throwable t) {
            // The NativeImage is off-heap; an exception in the pixel-copy loop
            // would leak it (the texture wrapper never got created). Close and
            // rethrow as a plain RuntimeException — no more @SneakyThrows, so
            // callers get a real stack trace instead of a hidden IOException.
            nativeImage.close();
            throw new RuntimeException("Failed to prepare glyph texture " + textureIdentifier, t);
        }

        // Ownership transfers to the texture here. Its constructor already
        // uploads the image (directly on the render thread, otherwise through
        // a recorded render call), so a second explicit upload() only repeated
        // the whole glTexSubImage2D.
        NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);

        if (RenderSystem.isOnRenderThread()) {
            finishTexture(textureIdentifier, texture);
        } else {
            RenderSystem.recordRenderCall(() -> finishTexture(textureIdentifier, texture));
        }
    }

    private static void finishTexture(Identifier textureIdentifier, NativeImageBackedTexture texture) {
        // Linear minification: the default (nearest) aliased glyphs whenever
        // the atlas is drawn below 1:1 — GUI scale 1 or a down-scaled matrix.
        texture.setFilter(true, false);
        MinecraftClient.getInstance().getTextureManager().registerTexture(textureIdentifier, texture);
    }
}
