/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.font;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.awt.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class Fonts {

    // Cache base Font objects (one per font file) to avoid repeated Font.createFont() calls.
    // Font.createFont() creates a temp file in %TEMP% each time — caching the base font
    // reduces ~522 temp files per launch down to ~18 (one per font type).
    private static final Map<String, Font> baseFontCache = new HashMap<>();

    @SneakyThrows
    private static Font loadBaseFont(String name) {
        Font cached = baseFontCache.get(name);
        if (cached != null) return cached;

        for (String fontName : new String[]{name, name.toLowerCase(), name.toUpperCase()}) {
            for (String ext : new String[]{".ttf", ".otf"}) {
                String path = "assets/minecraft/fonts/" + fontName + ext;
                try (InputStream inputStream = Fonts.class.getClassLoader().getResourceAsStream(path)) {
                    if (inputStream != null) {
                        Font baseFont = Font.createFont(Font.TRUETYPE_FONT, inputStream);
                        baseFontCache.put(name, baseFont);
                        return baseFont;
                    }
                }
            }
        }
        throw new IllegalStateException("Font not found: " + name);
    }

    public static FontRenderer create(float size, String name) {
        Font baseFont = loadBaseFont(name);
        Font derived = baseFont.deriveFont(size / 2f);
        return new FontRenderer(derived, size / 2f);
    }

    private static final Map<FontKey, FontRenderer> fontCache = new HashMap<>();

    /**
     * Индексированный доступ по размеру. Размеры 4..32 предзаполнены в
     * {@link #init()}, остальное падает в map — но обычный путь HUD/ESP зовёт
     * getSize десятки раз за кадр, и ради него аллокация FontKey каждый раз
     * не нужна.
     */
    private static final FontRenderer[][] SIZED_FONTS = new FontRenderer[Type.values().length][33];

    public static void init() {
        for (Type type : Type.values()) {
            for (int size = 4; size <= 32; size++) {
                SIZED_FONTS[type.ordinal()][size] = create(size, type.getType());
            }
        }
    }

    public static FontRenderer getSize(int size) {
        return getSize(size, Type.INST);
    }

    public static FontRenderer getSize(int size, Type type) {
        FontRenderer[] row = SIZED_FONTS[type.ordinal()];
        if (size >= 0 && size < row.length) {
            FontRenderer cached = row[size];
            if (cached == null) {
                cached = create(size, type.getType());
                row[size] = cached;
            }
            return cached;
        }
        return fontCache.computeIfAbsent(new FontKey(size, type), k -> create(size, type.getType()));
    }

    @Getter
    @RequiredArgsConstructor
    public enum Type {
        DEFAULT("sf_medium"),
        REGULAR("sf_regular"),
        SEMI("sf_semibold"),
        BOLD("sf_bold"),
        BOLDED("bold"),
        MANROPEEXTRABOLD("manropeextrabold"),
        MANROPEBOLD("manropebold"),
        NOTREGULAR("loxedlc_regular"),
        ICONRICHREG("iconloxedlcreg"),
        INST("suisseintl"),
        ICONS("icons"),
        ICONSTYPENEW("icon2"),
        GUIICONS("guiicons"),
        ICONSCATEGORY("categoryicons"),
        NOTVISUALS_MEDIUM("not_medium"),
        BANTY_BOLD("banty_bold"),
        baskerville_bold("baskerville_bold"),
        agoraslabpro_bold("agoraslabpro_bold"),
        MONO("mono");

        private final String type;
    }

    private record FontKey(int size, Type type) {
    }
}
