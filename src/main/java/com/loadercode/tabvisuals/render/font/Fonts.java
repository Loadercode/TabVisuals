/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.font;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.awt.*;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Fonts {

    // Cache base Font objects (one per font file) to avoid repeated Font.createFont() calls.
    // Font.createFont() creates a temp file in %TEMP% each time — caching the base font
    // reduces ~522 temp files per launch down to ~18 (one per font type).
    private static final Map<String, Font> baseFontCache = new ConcurrentHashMap<>();

    /**
     * Поток init/рендера: шрифты создаются только в нём. Ловится при первой
     * загрузке класса и переназначается в {@link #init()} (его как раз зовёт
     * клиентский поток Fabric), остальным потокам — см. {@link #awaitInit()}.
     */
    private static volatile Thread ownerThread = Thread.currentThread();

    /** init() завершился — даже со сбоем: TabVisualsClient переходит на ленивую загрузку. */
    private static final CountDownLatch INIT_DONE = new CountDownLatch(1);

    @SneakyThrows
    private static Font loadBaseFont(String name) {
        Font cached = baseFontCache.get(name);
        if (cached != null) return cached;

        // Locale.ROOT: в турецкой локали toLowerCase("I") даёт «ı», ресурс не находится и старт падает
        for (String fontName : new String[]{name, name.toLowerCase(Locale.ROOT), name.toUpperCase(Locale.ROOT)}) {
            for (String ext : new String[]{".ttf", ".otf"}) {
                String path = "assets/minecraft/fonts/" + fontName + ext;
                try (InputStream inputStream = Fonts.class.getClassLoader().getResourceAsStream(path)) {
                    if (inputStream != null) {
                        Font baseFont = Font.createFont(Font.TRUETYPE_FONT, inputStream);
                        Font published = baseFontCache.putIfAbsent(name, baseFont);
                        return published != null ? published : baseFont;
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

    /** Размеры вне 4..32 (например логотип 46): конкурентная map + {@link #awaitInit()} перед созданием. */
    private static final Map<FontKey, FontRenderer> fontCache = new ConcurrentHashMap<>();

    /**
     * Индексированный доступ по размеру. Размеры 4..32 предзаполнены в
     * {@link #init()}, остальное падает в map — но обычный путь HUD/ESP зовёт
     * getSize десятки раз за кадр, и ради него аллокация FontKey каждый раз
     * не нужна.
     */
    private static final FontRenderer[][] SIZED_FONTS = new FontRenderer[Type.values().length][33];

    public static void init() {
        ownerThread = Thread.currentThread();
        try {
            for (Type type : Type.values()) {
                for (int size = 4; size <= 32; size++) {
                    SIZED_FONTS[type.ordinal()][size] = create(size, type.getType());
                }
            }
        } finally {
            // finally: сбой предзаполнения не должен оставлять чужие потоки в вечном ожидании
            INIT_DONE.countDown();
        }
    }

    public static FontRenderer getSize(int size) {
        return getSize(size, Type.INST);
    }

    public static FontRenderer getSize(int size, Type type) {
        FontRenderer[] row = SIZED_FONTS[type.ordinal()];
        if (size >= 0 && size < row.length) {
            FontRenderer cached = row[size];
            if (cached != null) return cached;
            return getOrCreateSized(size, type, row);
        }
        FontKey key = new FontKey(size, type);
        FontRenderer cached = fontCache.get(key);
        if (cached != null) return cached;
        awaitInit();
        return fontCache.computeIfAbsent(key, k -> create(size, type.getType()));
    }

    /**
     * Ленивый слот: чужой поток сначала ждёт конца init() — тот заполняет весь
     * диапазон 4..32, так что создавать обычно уже не нужно; если всё же нужно
     * (init упал), создание идёт под монитором — запись в слот без гонок.
     */
    private static FontRenderer getOrCreateSized(int size, Type type, FontRenderer[] row) {
        awaitInit();
        synchronized (SIZED_FONTS) {
            FontRenderer cached = row[size];
            if (cached != null) return cached;
            FontRenderer created = create(size, type.getType());
            row[size] = created;
            return created;
        }
    }

    /**
     * Ждать может только чужой поток: владельцу (он же запускает init)
     * ожидание — это deadlock с самим init(). Таймаут, чтобы не повиснуть
     * навсегда, если init() вдруг не вызовут. Для редких размеров вне 4..32
     * остаточное создание вне render-потока безопасно: FontRenderer собирается
     * только из AWT (deriveFont), без GL.
     */
    private static void awaitInit() {
        if (Thread.currentThread() == ownerThread) return;
        try {
            INIT_DONE.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
