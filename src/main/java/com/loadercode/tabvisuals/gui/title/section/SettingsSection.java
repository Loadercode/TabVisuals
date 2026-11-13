// language: Java, file: SettingsSection.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.title.section;

import com.loadercode.tabvisuals.gui.icon.Icons;
import com.loadercode.tabvisuals.gui.title.MenuFont;
import com.loadercode.tabvisuals.gui.title.MenuFrame;
import com.loadercode.tabvisuals.gui.title.MenuKit;
import com.loadercode.tabvisuals.render.font.Fonts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screen.option.ChatOptionsScreen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.screen.option.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.option.SkinOptionsScreen;
import net.minecraft.client.gui.screen.option.SoundOptionsScreen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;

import static com.loadercode.tabvisuals.gui.title.MenuKit.*;

/**
 * "Настройки": a 3x3 grid of glass cards, each opening one vanilla options
 * page (parent = the title screen, so Done comes back here). Cards stagger
 * in on show, lift + glow on hover, sink on press.
 */
public final class SettingsSection implements MenuSection {

    private static final MenuFont F_CARD = new MenuFont(18, Fonts.Type.SEMI);
    private static final MenuFont F_DESC = new MenuFont(12.5f, Fonts.Type.REGULAR);
    private static final MenuFont F_LANG = new MenuFont(15, Fonts.Type.BOLD);

    private static final String[] TITLES = {"Видео", "Звук", "Управление", "Язык", "Ресурспаки",
            "Доступность", "Скин", "Чат", "Все настройки"};
    private static final String[] DESCS = {"Графика, дальность, FPS", "Громкость и музыка", "Клавиши и мышь",
            "Язык интерфейса", "Текстуры и шейдеры", "Читаемость и подсказки", "Слои скина и основная рука",
            "Видимость, размер и фильтр", "Полное меню Minecraft"};
    /** null = drawn by hand (language "Aa" mark). */
    private static final Icons.Glyph[] GLYPHS = {Icons.IMAGE, Icons.ACTIVITY, Icons.GAMEPAD, null, Icons.BOX,
            Icons.EYE, Icons.PERSON, Icons.ELLIPSIS, Icons.DASHBOARD};

    private final Screen parent;
    private final Anim[] anims = new Anim[9];
    private final float[] hx = new float[9], hy = new float[9];
    private float hw, hh;
    private boolean hitsValid;
    private long shownAt;

    public SettingsSection(Screen parent) {
        this.parent = parent;
        for (int i = 0; i < anims.length; i++) {
            anims[i] = new Anim();
        }
    }

    @Override
    public String title() {
        return "Настройки";
    }

    @Override
    public String subtitle() {
        return "Параметры игры и клиента";
    }

    @Override
    public void onShow() {
        shownAt = System.currentTimeMillis();
    }

    @Override
    public void onHide() {
        hitsValid = false;
    }

    @Override
    public void render(MenuFrame f, float x, float y, float w, float h, float alpha) {
        MatrixStack m = f.m;
        float gap = 9f;
        float cw = (w - gap * 2f) / 3f;
        float ch = Math.min(86f, (h - gap * 2f) / 3f);
        hw = cw;
        hh = ch;
        for (int i = 0; i < 9; i++) {
            int col = i % 3, row = i / 3;
            float e = enter(f.now, shownAt, 40L + (row * 3 + col) * 35L, 420L);
            float a = alpha * e;
            float cx = x + col * (cw + gap);
            float cy = y + row * (ch + gap) + (1f - e) * 10f;
            Anim st = anims[i];
            st.update(f, cx, cy, cw, ch);
            hx[i] = cx;
            hy[i] = cy;
            float lift = st.hover * 2f;
            cy -= lift;

            pushScale(m, cx + cw / 2f, cy + ch / 2f, st.scale());
            if (st.hover > 0.01f) {
                glow(m, cx + 30f, cy + 26f, cw * 0.55f, ch * 0.7f, alpha(ACCENT_DEEP, 0.16f * st.hover * a));
            }
            card(m, cx, cy, cw, ch, 10f, alpha(mix(CARD, CARD_HOVER, st.hover), a),
                    alpha(mix(LINE, LINE_HOVER, st.hover), a));
            // top hairline sheen
            rect(m, cx + 10f, cy + 0.5f, cw - 20f, 0.6f, 0.3f, alpha(0x14FFFFFF, a));

            int ic = alpha(mix(ACCENT, 0xFFDDD3FF, st.hover), a);
            float ix = cx + 18f, iy = cy + 20f;
            if (GLYPHS[i] == null) {
                F_LANG.drawCentered(m, "Aa", ix, iy, ic);
            } else {
                icon(m, GLYPHS[i], ix, iy, 20f, ic);
            }
            F_CARD.draw(m, TITLES[i], cx + 12f, cy + ch - 32f, alpha(TEXT, a));
            F_DESC.draw(m, F_DESC.fit(DESCS[i], cw - 24f), cx + 12f, cy + ch - 17f, alpha(TEXT_3, a));

            // a chevron slides in from the left on hover
            if (st.hover > 0.02f) {
                float chx = cx + cw - 18f - (1f - st.hover) * 6f;
                m.push();
                m.translate(chx, cy + 20f, 0f);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90f));
                icon(m, Icons.CHEVRON_DOWN, 0f, 0f, 14f, alpha(TEXT_2, a * st.hover));
                m.pop();
            }
            m.pop();
        }
        hitsValid = alpha > 0.9f;
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (button != 0 || !hitsValid) {
            return false;
        }
        for (int i = 0; i < 9; i++) {
            if (mx >= hx[i] && mx <= hx[i] + hw && my >= hy[i] && my <= hy[i] + hh) {
                MenuKit.pressed = anims[i];
                open(i);
                return true;
            }
        }
        return false;
    }

    private void open(int i) {
        MinecraftClient mc = MinecraftClient.getInstance();
        GameOptions o = mc.options;
        Screen next = switch (i) {
            case 0 -> new VideoOptionsScreen(parent, mc, o);
            case 1 -> new SoundOptionsScreen(parent, o);
            case 2 -> new ControlsOptionsScreen(parent, o);
            case 3 -> new LanguageOptionsScreen(parent, o, mc.getLanguageManager());
            case 4 -> packScreen(mc);
            case 5 -> new AccessibilityOptionsScreen(parent, o);
            case 6 -> new SkinOptionsScreen(parent, o);
            case 7 -> new ChatOptionsScreen(parent, o);
            default -> new OptionsScreen(parent, o);
        };
        mc.setScreen(next);
    }

    /** Resource packs: the same screen + apply step vanilla's options menu uses. */
    private Screen packScreen(MinecraftClient mc) {
        return new PackScreen(mc.getResourcePackManager(), manager -> {
            mc.options.refreshResourcePacks(manager);
            mc.setScreen(parent);
        }, mc.getResourcePackDir(), Text.translatable("resourcePack.title"));
    }
}
