/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import com.loadercode.tabvisuals.common.animation.Animation;
import com.loadercode.tabvisuals.common.animation.Direction;
import com.loadercode.tabvisuals.common.animation.implement.Decelerate;
import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.gui.hud.compat.SelectSetting;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import com.loadercode.tabvisuals.render.math.calc.Calculate;

import java.awt.*;
import java.util.List;

public class Armor extends AbstractDraggable {

    private int lastWarnPercent = 101;
    private long lastWarnTimeMs = 0L;
    private static final long WARNING_DURATION_MS = 1500L;
    private static final int SHADOW_TEXT = new Color(0, 0, 0, 180).getRGB();
    private static final int WARN_SHADOW = ColorAssist.rgba(0, 0, 0, 200);
    private static final int WARN_RED = ColorAssist.rgba(255, 60, 60, 255);
    private static final int DURABILITY_GREEN = ColorAssist.rgba(120, 255, 120, 255);
    private static final int DURABILITY_YELLOW = ColorAssist.rgba(255, 230, 120, 255);
    private static final int DURABILITY_RED = ColorAssist.rgba(255, 120, 120, 255);
    private static final ItemStack PREVIEW_HELMET = new ItemStack(Items.DIAMOND_HELMET);
    private static final ItemStack PREVIEW_CHESTPLATE = new ItemStack(Items.DIAMOND_CHESTPLATE);
    private static final ItemStack PREVIEW_LEGGINGS = new ItemStack(Items.DIAMOND_LEGGINGS);
    private static final ItemStack PREVIEW_BOOTS = new ItemStack(Items.DIAMOND_BOOTS);

    private static final String[] ORIENTATION_OPTIONS = {"Горизонтально", "Вертикально"};
    private static final float MENU_WIDTH = 92f;
    private static final float MENU_ROW = 14f;
    private static final float MENU_PAD = 4f;
    private static final int SLOT_COUNT = 4;
    private static final int EDGE_ANCHOR_TOLERANCE = 2;

    private FontRenderer percentFont;
    private FontRenderer warnFont;
    private FontRenderer menuFont;

    private boolean menuOpen;
    private float menuX, menuY;
    private final Animation menuAlpha = new Decelerate().setMs(200).setValue(1);
    private final Animation menuHeight = new Decelerate().setMs(160).setValue(0);

    public Armor() {
        super("Armor", 10, 150, 100, 28, true);
        menuAlpha.setDirection(Direction.BACKWARDS);
        menuHeight.setDirection(Direction.BACKWARDS);
    }

    @Override
    public boolean visible() {
        return Hud.getInstance().interfaceSettings.isSelected("Armor");
    }

    private boolean isVertical() {
        Hud hud = Hud.getInstance();
        return hud != null && hud.armorOrientation != null && hud.armorOrientation.isSelected("Вертикально");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (Hud.getInstance() == null || !canDraw(Hud.getInstance(), this)) return false;

        // ПКМ по Armor — открыть/закрыть меню ориентации
        if (button == 1 && isHovered(mouseX, mouseY)) {
            menuOpen = !menuOpen;
            syncMenuAnimation();
            return true;
        }

        if (menuOpen && button == 0) {
            if (isMenuHovered(mouseX, mouseY)) {
                SelectSetting orientation = Hud.getInstance().armorOrientation;
                float listY = menuY + 18f;
                for (int i = 0; i < ORIENTATION_OPTIONS.length; i++) {
                    float rowY = listY + i * MENU_ROW;
                    if (Calculate.isHovered(mouseX, mouseY, menuX, rowY, MENU_WIDTH, MENU_ROW)) {
                        orientation.setSelected(ORIENTATION_OPTIONS[i]);
                        return true;
                    }
                }
                return true;
            }
            // Клик мимо меню — закрыть
            closeMenu();
            if (isHovered(mouseX, mouseY)) {
                return true;
            }
        }

        if (isCanDrag()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    private void closeMenu() {
        menuOpen = false;
        syncMenuAnimation();
    }

    private void syncMenuAnimation() {
        if (menuOpen) {
            menuAlpha.setDirection(Direction.FORWARDS);
            menuHeight.setDirection(Direction.FORWARDS);
            menuHeight.setValue(18f + ORIENTATION_OPTIONS.length * MENU_ROW + MENU_PAD);
        } else {
            menuAlpha.setDirection(Direction.BACKWARDS);
            menuHeight.setDirection(Direction.BACKWARDS);
        }
    }

    private boolean isMenuHovered(double mouseX, double mouseY) {
        float h = Math.max(menuHeight.getOutput().floatValue(), 1f);
        return Calculate.isHovered(mouseX, mouseY, menuX, menuY, MENU_WIDTH, h);
    }

    @Override
    public void drawDraggable(DrawContext context) {
        if (mc.player == null || mc.world == null) return;

        MatrixStack matrix = context.getMatrices();
        if (percentFont == null) {
            percentFont = Fonts.getSize(12, Fonts.Type.BOLD);
            warnFont = Fonts.getSize(22, Fonts.Type.BOLD);
            menuFont = Fonts.getSize(12, Fonts.Type.BOLD);
        }

        List<ItemStack> armorSlots = mc.player.getInventory().armor;
        boolean chatOpen = HudHelper.isChat(mc.currentScreen);
        boolean allEmpty = true;
        for (ItemStack armorStack : armorSlots) {
            if (!armorStack.isEmpty()) {
                allEmpty = false;
                break;
            }
        }
        boolean previewMode = chatOpen && allEmpty;
        boolean vertical = isVertical();

        float iconSize = 18f;
        float slotSize = iconSize + 2f;
        float padding = 3f;
        float textGap = 6f;
        Hud hud = Hud.getInstance();

        // Вертикально: левая половина экрана → % справа от иконок; правая → % слева
        int screenWidth = mc.getWindow().getScaledWidth();
        float screenMid = screenWidth / 2f;
        boolean durabilityOnRight = getX() < screenMid;

        float maxTextWidth = 0f;
        if (vertical) {
            for (int i = 0; i < SLOT_COUNT; i++) {
                ItemStack stack = resolveArmorStack(armorSlots, i, previewMode);
                if (stack.isEmpty()) continue;
                maxTextWidth = Math.max(maxTextWidth,
                        percentFont.getStringWidth(getDurabilityPercentInt(stack) + "%"));
            }
        }

        float iconOffsetX = (vertical && !durabilityOnRight && maxTextWidth > 0f)
                ? maxTextWidth + textGap
                : 0f;

        float slotStep = iconSize + padding + 3f;
        int targetWidth;
        int targetHeight;
        if (vertical) {
            float contentWidth = slotSize;
            if (maxTextWidth > 0f) {
                contentWidth = durabilityOnRight
                        ? Math.max(slotSize, iconSize + textGap + maxTextWidth)
                        : maxTextWidth + textGap + slotSize;
            }
            targetWidth = (int) Math.ceil(contentWidth);
            targetHeight = (int) Math.ceil((SLOT_COUNT - 1) * slotStep + slotSize);
        } else {
            targetWidth = (int) Math.ceil((SLOT_COUNT - 1) * slotStep + slotSize);
            targetHeight = (int) Math.ceil(iconSize + 10f);
        }

        // Если пользователь прижал HUD к правому краю, изменение ширины не должно
        // менять его якорь. Особенно это заметно при переходе preview → пустая броня.
        boolean anchoredToRight = Math.abs(screenWidth - (getX() + getWidth())) <= EDGE_ANCHOR_TOLERANCE;
        setWidth(targetWidth);
        setHeight(targetHeight);
        if (anchoredToRight) {
            setX(Math.max(0, screenWidth - targetWidth));
        }
        validPosition();

        float x = getX();
        float y = getY();

        float currentX = x;
        float currentY = y;

        matrix.push();
        int lowestPercent = 101;

        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = resolveArmorStack(armorSlots, i, previewMode);

            float slotX = vertical ? x + iconOffsetX : currentX;
            float slotY = vertical ? currentY : y;

            if (hud != null && hud.shouldDrawHudBlur()) {
                blur.render(ShapeProperties.create(matrix, slotX, slotY, iconSize + 2f, iconSize + 2f)
                        .round(4f)
                        .quality(hud.getBlurQuality())
                        .color(ColorAssist.getBlurColor())
                        .build());
            }

            rectangle.render(ShapeProperties.create(matrix, slotX, slotY, iconSize + 2f, iconSize + 2f)
                    .round(4f)
                    .color(ColorAssist.multAlpha(ColorAssist.getRectangleColor(), 180f / 75f))
                    .build());

            if (!stack.isEmpty()) {
                int durabilityPercent = getDurabilityPercentInt(stack);
                String durabilityText = durabilityPercent + "%";
                int durabilityColor = getDurabilityColor(stack);

                if (durabilityPercent < lowestPercent) {
                    lowestPercent = durabilityPercent;
                }

                float textWidth = percentFont.getStringWidth(durabilityText);
                if (vertical) {
                    float textX = durabilityOnRight
                            ? slotX + iconSize + textGap
                            : slotX - textGap - textWidth;
                    float textY = slotY + (iconSize + 2f) / 2f - 2f;
                    percentFont.drawString(matrix, durabilityText, textX + 0.5f, textY + 0.5f, SHADOW_TEXT);
                    percentFont.drawString(matrix, durabilityText, textX, textY, durabilityColor);
                } else {
                    float textX = slotX + (iconSize + 2f) / 2f - textWidth / 2f;
                    float textY = slotY - 6f;
                    percentFont.drawString(matrix, durabilityText, textX + 0.5f, textY + 0.5f, SHADOW_TEXT);
                    percentFont.drawString(matrix, durabilityText, textX, textY, durabilityColor);
                }

                int itemX = (int) (slotX + 2f);
                int itemY = (int) (slotY + 2f);
                context.drawItem(stack, itemX, itemY);
                context.drawStackOverlay(mc.textRenderer, stack, itemX, itemY);
            }

            if (vertical) {
                currentY += iconSize + padding + 3f;
            } else {
                currentX += iconSize + padding + 3f;
            }
        }

        matrix.pop();

        // Меню под элементом (как Select в настройках)
        menuX = getX();
        menuY = getY() + getHeight() + 4f;
        if (menuOpen || !menuAlpha.isFinished(Direction.BACKWARDS)) {
            renderOrientationMenu(matrix);
        }

        long now = System.currentTimeMillis();
        if (lowestPercent >= 10 || lowestPercent == 101) {
            lastWarnPercent = 101;
            lastWarnTimeMs = 0L;
        } else if (lowestPercent >= 0 && lowestPercent < 10 && lowestPercent < lastWarnPercent) {
            lastWarnPercent = lowestPercent;
            lastWarnTimeMs = now;
            mc.world.playSound(mc.player, mc.player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.BLOCKS, 1f, 1f);
        }

        if (lastWarnTimeMs > 0 && now - lastWarnTimeMs <= WARNING_DURATION_MS) {
            String warnText = "Броня почти сломана";
            float screenW = mc.getWindow().getScaledWidth();
            float screenH = mc.getWindow().getScaledHeight();
            float textW = warnFont.getStringWidth(warnText);
            float textH = warnFont.getFont().getSize() / 1.5f;

            float sx = screenW / 2f - textW / 2f;
            float sy = screenH / 2f - textH / 2f;

            warnFont.drawString(matrix, warnText, sx + 1.5f, sy + 1.5f, WARN_SHADOW);
            warnFont.drawString(matrix, warnText, sx, sy, WARN_RED);
        }
    }

    private void renderOrientationMenu(MatrixStack matrix) {
        float opacity = menuAlpha.getOutput().floatValue();
        if (opacity <= 0.01f) return;

        float animatedHeight = menuHeight.getOutput().floatValue();
        SelectSetting orientation = Hud.getInstance().armorOrientation;
        String selected = orientation != null ? orientation.getSelected() : "Горизонтально";

        int listBg = ColorAssist.getGuiRectColor2(0.55f * opacity);
        rectangle.render(ShapeProperties.create(matrix, menuX, menuY, MENU_WIDTH, animatedHeight)
                .round(3).thickness(2).outlineColor(ColorAssist.getOutline(155 / 255f))
                .color(listBg, listBg, listBg, listBg)
                .build());

        // Заголовок
        Fonts.getSize(11, Fonts.Type.DEFAULT).drawString(matrix, "Ориентация",
                menuX + 5f, menuY + 6f, ColorAssist.multAlpha(ColorAssist.getText(0.7f), opacity));

        // Разделитель
        rectangle.render(ShapeProperties.create(matrix, menuX + 3f, menuY + 15f, MENU_WIDTH - 6f, 0.5f)
                .color(ColorAssist.multAlpha(ColorAssist.getOutline(), opacity * 0.6f))
                .build());

        float rowY = menuY + 18f;
        for (String option : ORIENTATION_OPTIONS) {
            boolean isSelected = option.equals(selected);
            if (isSelected && rowY - menuY < animatedHeight) {
                int hoverBg = ColorAssist.getGuiRectColor2(0.45f * opacity);
                rectangle.render(ShapeProperties.create(matrix, menuX + 1f, rowY, MENU_WIDTH - 2f, MENU_ROW - 0.5f)
                        .round(2)
                        .color(hoverBg, hoverBg, ColorAssist.getGuiRectColor2(0), ColorAssist.getGuiRectColor2(0))
                        .build());
            }
            if (rowY - menuY < animatedHeight) {
                menuFont.drawString(matrix, option, menuX + 6f, rowY + 5f,
                        ColorAssist.multAlpha(ColorAssist.getText(), opacity));
            }
            rowY += MENU_ROW;
        }
    }

    private ItemStack resolveArmorStack(List<ItemStack> armorSlots, int visualIndex, boolean previewMode) {
        if (previewMode) {
            return switch (visualIndex) {
                case 0 -> PREVIEW_HELMET;
                case 1 -> PREVIEW_CHESTPLATE;
                case 2 -> PREVIEW_LEGGINGS;
                case 3 -> PREVIEW_BOOTS;
                default -> ItemStack.EMPTY;
            };
        }
        int armorIndex = armorSlots.size() - 1 - visualIndex;
        return armorIndex >= 0 ? armorSlots.get(armorIndex) : ItemStack.EMPTY;
    }

    private int getDurabilityPercentInt(ItemStack stack) {
        if (!stack.isDamageable()) return 100;
        int max = stack.getMaxDamage();
        int left = max - stack.getDamage();
        if (max <= 0) return 0;
        return Math.round((left / (float) max) * 100f);
    }

    private int getDurabilityColor(ItemStack stack) {
        if (!stack.isDamageable()) return DURABILITY_GREEN;
        int max = stack.getMaxDamage();
        int left = max - stack.getDamage();
        if (max <= 0) return ColorAssist.getText();
        float pct = left / (float) max;

        if (pct > 0.6f) {
            return DURABILITY_GREEN;
        } else if (pct > 0.3f) {
            return DURABILITY_YELLOW;
        } else {
            return DURABILITY_RED;
        }
    }
}
