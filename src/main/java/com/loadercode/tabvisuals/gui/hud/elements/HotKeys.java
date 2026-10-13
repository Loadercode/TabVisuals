/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import com.loadercode.tabvisuals.common.animation.Direction;
import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.gui.Category;
import com.loadercode.tabvisuals.gui.module.ModuleRegistry;
import com.loadercode.tabvisuals.gui.module.ModuleStub;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.math.calc.Calculate;
import com.loadercode.tabvisuals.gui.hud.elements.StringHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HotKeys extends AbstractDraggable {
    private final List<ModuleStub> keysList = new ArrayList<>();
    private long lastKeyChange = 0;
    private java.lang.String currentRandomKey = "NONE";
    private int activeBindCount = 0;
    private FontRenderer font;
    private FontRenderer fontModule;
    private FontRenderer icon;
    private FontRenderer items;
    private FontRenderer categoryIcon;
    private static final Random RANDOM = new Random();
    private static final List<java.lang.String> PREVIEW_KEYS = List.of("A", "B", "C", "D", "E");

    public HotKeys() {
        super("Hot Keys", 300, 40, 80, 23, true);
    }

    /** Category tab glyph in the ICONSCATEGORY atlas (stable letters only). */
    private static String categoryGlyph(Category category) {
        return switch (category) {
            case RENDER -> "C";
            case MISC -> "E";
            case CONFIGS -> "F";
            case COMBAT -> "G";
            case PLAYER -> "H";
            case MOVEMENT -> "D";
        };
    }

    @Override
    public boolean visible() {
        return Hud.getInstance().interfaceSettings.isSelected("Hot Keys")
                && (!keysList.isEmpty() || HudHelper.isChat(mc.currentScreen));
    }

    @Override
    public void tick() {
        keysList.clear();

        int activeCount = 0;
        for (ModuleStub module : ModuleRegistry.ALL) {
            if ((module.bind() == -1 || module.bind() == 0) && holdKey(module) == null) {
                continue;
            }
            if (!module.enabled()) {
                continue;
            }
            keysList.add(module);
            activeCount++;
        }
        activeBindCount = activeCount;
        if (keysList.isEmpty() && HudHelper.isChat(mc.currentScreen)) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastKeyChange >= 1000) {
                currentRandomKey = PREVIEW_KEYS.get(RANDOM.nextInt(PREVIEW_KEYS.size()));
                lastKeyChange = currentTime;
            }
        }
    }

    @Override
    public void drawDraggable(DrawContext context) {
        MatrixStack matrix = context.getMatrices();
        if (font == null) {
            font = Fonts.getSize(13, Fonts.Type.DEFAULT);
            fontModule = Fonts.getSize(13, Fonts.Type.DEFAULT);
            icon = Fonts.getSize(23, Fonts.Type.ICONS);
            items = Fonts.getSize(12, Fonts.Type.DEFAULT);
            categoryIcon = Fonts.getSize(16, Fonts.Type.ICONSCATEGORY);
        }

        java.lang.String moduleCountText = java.lang.String.valueOf(activeBindCount);
        float textWidth = items.getStringWidth(moduleCountText);
        float boxWidth = textWidth + 6;

        int[] gc = ColorAssist.getRectangleGradientColors();
        int blurQuality = com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality();
        float totalH = getHeight();

        blur.render(ShapeProperties.create(matrix, getX(), getY(), getWidth(), totalH)
                .round(4f)
                .quality(blurQuality)
                .color(ColorAssist.getBlurColor())
                .build());
        rectangle.render(ShapeProperties.create(matrix, getX(), getY(), getWidth(), totalH)
                .round(4f)
                .thickness(0.1f)
                .outlineColor(ColorAssist.getOutlineColor())
                .color(gc[0], gc[1], gc[2], gc[3])
                .build());
            blur.render(ShapeProperties.create(matrix, getX(), getY() + 16.5F, getWidth(), getHeight() - 17)
                    .round(0,4,0,4).quality(com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality())
                    .color(ColorAssist.getBlurColor())
                    .build());
            rectangle.render(ShapeProperties.create(matrix, getX(), getY() + 16.5F, getWidth(), getHeight() - 17)
                    .round(0,4,0,4)
                    .thickness(0.1f)
                    .outlineColor(ColorAssist.getOutlineColor())
                    .color(gc[0], gc[1], gc[2], gc[3])
                .build());

        items.drawString(matrix, "Active:", getX() + getWidth() - boxWidth - 21, getY() + 7, ColorAssist.getText());
        items.drawString(matrix, moduleCountText, getX() + getWidth() - boxWidth - 2, getY() + 7, ColorAssist.getText());

        rectangle.render(ShapeProperties.create(matrix, getX() + 18, getY() + 5, 0.5f, 6)
                .color(ColorAssist.getText(0.5F)).round(0F).build());

        icon.drawString(matrix, "B", getX() + 4f, getY() + 5f, ColorAssist.getText());
        font.drawString(matrix, getName(), getX() + 22, getY() + 6.5f, ColorAssist.getText());

        float centerX = getX() + getWidth() / 2F;
        int offset = 23;
        int maxWidth = 80;

        if (keysList.isEmpty() && HudHelper.isChat(mc.currentScreen)) {
            float centerY = getY() + offset;
            java.lang.String name = "Example Module";
            java.lang.String bind = "[" + currentRandomKey + "]";
            java.lang.String iconChar = "A";
            int textColor = ColorAssist.getText();
            int textAlpha = 255;
            int colorWithAlpha = ColorAssist.rgba((textColor >> 16) & 255, (textColor >> 8) & 255, textColor & 255, textAlpha);
            float bindWidth = fontModule.getStringWidth(bind);
            Calculate.scale(matrix, centerX, centerY, 1, 1, () -> {
                categoryIcon.drawString(matrix, iconChar, getX() + 4.5f, centerY + 1.5f, textColor);
                rectangle.render(ShapeProperties.create(matrix, getX() + 15F, centerY - 1, 0.5F, 7).color(ColorAssist.getOutline(1, 0.5F)).build());
                fontModule.drawString(matrix, name, getX() + 19, centerY + 1, colorWithAlpha);
                fontModule.drawString(matrix, bind, getX() + getWidth() - bindWidth - 8, centerY + 1, textColor);
            });
            int width = (int) fontModule.getStringWidth(name + bind) + 25;
            maxWidth = Math.max(width, maxWidth);
            offset += 11;
        } else {
            for (ModuleStub module : keysList) {
                java.lang.String keyName = module.bind() > 0
                        ? StringHelper.getBindName(module.bind()) : holdKey(module);
                java.lang.String bind = "[" + keyName + "]";
                float centerY = getY() + offset;
                float animation = 1.0f;
                java.lang.String iconChar = categoryGlyph(module.category());
                int textColor = ColorAssist.getText();
                int textAlpha = 255;
                int colorWithAlpha = ColorAssist.rgba((textColor >> 16) & 255, (textColor >> 8) & 255, textColor & 255, textAlpha);
                float bindWidth = fontModule.getStringWidth(bind);
                Calculate.scale(matrix, centerX, centerY, 1, animation, () -> {
                    categoryIcon.drawString(matrix, iconChar, getX() + 4.5f, centerY + 1.5f, textColor);
                    rectangle.render(ShapeProperties.create(matrix, getX() + 15F, centerY - 1, 0.5F, 7).color(ColorAssist.getOutline(1, 0.5F)).build());
                    fontModule.drawString(matrix, module.name(), getX() + 19, centerY + 1, colorWithAlpha);
                    fontModule.drawString(matrix, bind, getX() + getWidth() - bindWidth - 8, centerY + 1, textColor);
                });
                float width = fontModule.getStringWidth(module.name() + bind) + 25;
                maxWidth = (int) Math.max(width, maxWidth);
                offset += (int) (animation * 11);
            }
        }
        setWidth(maxWidth + 10);
        setHeight(offset);
    }

    /** Hold-key label (Zoom/FreeLook KeySetting) shown when the toggle bind is empty. */
    private static java.lang.String holdKey(ModuleStub module) {
        for (com.loadercode.tabvisuals.gui.setting.Setting s : module.settings()) {
            if (s instanceof com.loadercode.tabvisuals.gui.setting.KeySetting ks && ks.get() > 0) {
                return ks.label();
            }
        }
        return null;
    }
}
