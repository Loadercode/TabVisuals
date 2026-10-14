/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.gui.hud.compat.FTHelper;
import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.common.animation.Animation;
import com.loadercode.tabvisuals.common.animation.Direction;
import com.loadercode.tabvisuals.common.animation.implement.Decelerate;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.gui.hud.elements.StringHelper;
import net.minecraft.client.render.DiffuseLighting;
import com.mojang.blaze3d.systems.RenderSystem;

import java.awt.*;
import java.util.*;
import java.util.List;

public class Binds extends AbstractDraggable {
    private final FTHelper serverHelper;
    private final List<BindInfo> binds = new ArrayList<>();
    private final Map<Item, Long> cooldownStartTimes = new HashMap<>();
    private final Map<Item, Integer> itemCounts = new HashMap<>();
    private final Set<Item> activeCooldowns = new HashSet<>();
    private final List<BindInfo> activeBindsCache = new ArrayList<>();
    private final Map<Item, ItemStack> displayStacks = new HashMap<>();
    private static final int BINDS_PER_ROW = 5;
    private static final int BODY_OUTLINE = new Color(33, 33, 33, 255).getRGB();
    private static final int EMPTY_BG_1 = new Color(250, 10, 10, 35).getRGB();
    private static final int EMPTY_BG_2 = new Color(250, 5, 5, 35).getRGB();
    private static final int EMPTY_ICON_1 = new Color(80, 10, 10, 65).getRGB();
    private static final int EMPTY_ICON_2 = new Color(20, 5, 5, 65).getRGB();
    private static final int BODY_BG_1 = new Color(18, 19, 20, 75).getRGB();
    private static final int BODY_BG_2 = new Color(0, 2, 5, 75).getRGB();
    private static final int[] PROGRESS_COLORS = new int[101];
    private static final Random RANDOM = new Random();
    private static final List<java.lang.String> PREVIEW_KEYS = List.of("A", "B", "C", "D", "E");
    private long lastBindChange = 0;
    private java.lang.String currentRandomKey1 = "None";
    private java.lang.String currentRandomKey2 = "None";
    private java.lang.String currentRandomKey3 = "None";
    private int currentItemIndex1 = 0;
    private int currentItemIndex2 = 1;
    private int currentItemIndex3 = 2;
    private static final Item[] EXAMPLE_ITEMS = {Items.ENDER_EYE, Items.SUGAR, Items.DRIED_KELP, Items.NETHERITE_SCRAP};
    private final Animation animation = new Decelerate().setMs(300).setValue(1);
    /** Акцентная вспышка пилюли: предмет → время срабатывания бинда. */
    private final Map<Item, Long> pulse_ms = new HashMap<>();
    /** Время первого появления пилюли — стартовая задержка по ряду. */
    private final Map<Item, Long> pill_born_ms = new HashMap<>();
    private final Map<Item, Integer> prev_item_counts = new HashMap<>();
    private final Set<Item> active_pill_items = new HashSet<>();
    private long preview_born_ms;
    private FontRenderer font13;
    private FontRenderer font11;
    private FontRenderer font10;

    static {
        for (int i = 0; i < PROGRESS_COLORS.length; i++) {
            float progress = i / 100f;
            int r;
            int g;
            if (progress <= 0.5f) {
                float t = progress * 2f;
                r = 255;
                g = (int) (165 * t);
            } else {
                float t = (progress - 0.5f) * 2f;
                r = (int) (255 * (1 - t));
                g = (int) (165 * (1 - t) + 255 * t);
            }
            PROGRESS_COLORS[i] = ColorAssist.rgba(r, g, 0, 255);
        }
    }

    private static class BindInfo {
        FTHelper.KeyBind keyBind;
        java.lang.String displayName;
        int color;
        java.lang.String searchName;

        BindInfo(FTHelper.KeyBind keyBind, java.lang.String displayName, int color, java.lang.String searchName) {
            this.keyBind = keyBind;
            this.displayName = displayName;
            this.color = color;
            this.searchName = searchName;
        }
    }

    public Binds() {
        super("Binds", 10, 180, 150, 40, true);
        this.serverHelper = FTHelper.getInstance();
        initializeBinds();
    }

    private void initializeBinds() {
        for (FTHelper.KeyBind keyBind : serverHelper.getKeyBindings()) {
            java.lang.String name = keyBind.setting().getName();
            java.lang.String searchName = getSearchNameForBind(name);
            int color = getColorForBind(name);
            binds.add(new BindInfo(keyBind, name, color, searchName));
        }
    }
    private java.lang.String getSearchNameForBind(java.lang.String name) {
        return switch (name) {
            case "Зелье отрыжки" -> "отрыжки";
            case "Зелье серной кислоты" -> "серная";
            case "Зелье вспышки" -> "вспышка";
            case "Зелье мочи Флеша" -> "моча флеша";
            case "Зелье победителя" -> "победителя";
            case "Зелье агента" -> "агента";
            case "Зелье медика" -> "медика";
            case "Зелье киллера" -> "киллера";
            default -> null;
        };
    }
    private int getColorForBind(java.lang.String name) {
        return switch (name) {
            case "Зелье отрыжки" -> 0xFF5D00;
            case "Зелье серной кислоты" -> 0x00C200;
            case "Зелье вспышки" -> 0xFFFFFF;
            case "Зелье мочи Флеша" -> 0x5CF7FF;
            case "Зелье победителя" -> 0x00FF00;
            case "Зелье агента" -> 0xFFFB00;
            case "Зелье медика" -> 0xFF00DE;
            case "Зелье киллера" -> 0xFF0000;
            default -> -1;
        };
    }

    private ItemStack createColoredPotion(Item item, int color) {
        if (color == -1 || item != Items.SPLASH_POTION) {
            return item.getDefaultStack();
        }
        ItemStack cached = displayStacks.get(item);
        if (cached != null) {
            return cached;
        }
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Optional.empty(), Optional.of(color), List.of(), Optional.empty()));
        displayStacks.put(item, stack);
        return stack;
    }

    private void renderItemStack(DrawContext context, ItemStack stack, float x, float y) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 100);
        matrices.scale((float) 0.5, (float) 0.5, 1);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        DiffuseLighting.enableGuiDepthLighting();

        context.drawItem(stack, 0, 0);

        DiffuseLighting.disableGuiDepthLighting();
        matrices.pop();
    }

    private int countItemInInventory(Item targetItem, java.lang.String searchName) {
        if (HudHelper.nullCheck()) return 0;

        int count = 0;
        for (int i = 0; i < Objects.requireNonNull(mc.player).getInventory().size(); i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (itemStack.isEmpty()) continue;

            boolean useCustomName = FTHelper.getInstance().isCustomNameCheckEnabled();
            if (useCustomName && searchName != null && targetItem == Items.SPLASH_POTION) {
                if (itemStack.getItem() == targetItem &&
                        HudHelper.getCleanName(itemStack.getName()).contains(searchName.toLowerCase())) {
                    count += itemStack.getCount();
                }
            } else {
                if (itemStack.getItem() == targetItem) {
                    count += itemStack.getCount();
                }
            }
        }
        return count;
    }

    private float getCooldownProgress(Item item) {
        if (HudHelper.nullCheck()) return 0;
        ItemStack stack = new ItemStack(item);
        assert mc.player != null;
        return mc.player.getItemCooldownManager().getCooldownProgress(stack, mc.getRenderTickCounter().getTickDelta(false));
    }

    @Override
    public boolean visible() {
        return Hud.getInstance().interfaceSettings.isSelected("Binds") && Hud.getInstance().state && !animation.isFinished(Direction.BACKWARDS);
    }

    @Override
    public void tick() {
        if (HudHelper.nullCheck()) {
            animation.setDirection(Direction.BACKWARDS);
            return;
        }
        // The bind list follows the vanilla hotbar (our FTHelper feed), so it
        // is rebuilt each tick — new slot items appear as new pills.
        binds.clear();
        initializeBinds();
        activeBindsCache.clear();
        for (BindInfo bind : binds) {
            if (isBindActive(bind)) {
                activeBindsCache.add(bind);
            }
        }

        if (!activeBindsCache.isEmpty()) {
            animation.setDirection(Direction.FORWARDS);
        } else if (HudHelper.isChat(mc.currentScreen)) {
            animation.setDirection(Direction.FORWARDS);
        } else {
            animation.setDirection(Direction.BACKWARDS);
        }

        itemCounts.clear();
        activeCooldowns.clear();
        long currentTime = System.currentTimeMillis();
        boolean useCustomName = FTHelper.getInstance().isCustomNameCheckEnabled();
        for (int i = 0; i < Objects.requireNonNull(mc.player).getInventory().size(); i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (itemStack.isEmpty()) continue;

            Item item = itemStack.getItem();
            for (BindInfo bind : binds) {
                if (bind.keyBind.item() != item) continue;
                if (useCustomName && bind.searchName != null && item == Items.SPLASH_POTION
                        && !HudHelper.getCleanName(itemStack.getName()).contains(bind.searchName.toLowerCase())) {
                    continue;
                }
                itemCounts.merge(item, itemStack.getCount(), Integer::sum);
            }

            if (mc.player.getItemCooldownManager().isCoolingDown(itemStack)) {
                if (!cooldownStartTimes.containsKey(item)) {
                    cooldownStartTimes.put(item, currentTime);
                    // бинд «сработал»: предмет использован, кулдаун пошёл
                    pulse_ms.put(item, currentTime);
                }
                activeCooldowns.add(item);
            }
        }
        cooldownStartTimes.keySet().removeIf(item -> !activeCooldowns.contains(item));
        // бинд «сработал»: количество предмета в инвентаре уменьшилось
        for (Map.Entry<Item, Integer> e : itemCounts.entrySet()) {
            Integer prev = prev_item_counts.get(e.getKey());
            if (prev != null && e.getValue() < prev) {
                pulse_ms.put(e.getKey(), currentTime);
            }
        }
        prev_item_counts.clear();
        prev_item_counts.putAll(itemCounts);
        pulse_ms.entrySet().removeIf(e -> currentTime - e.getValue() > 500L);
        if (activeBindsCache.isEmpty() && HudHelper.isChat(mc.currentScreen)) {
            if (currentTime - lastBindChange >= 1000) {
                currentRandomKey1 = PREVIEW_KEYS.get(RANDOM.nextInt(PREVIEW_KEYS.size()));
                currentRandomKey2 = PREVIEW_KEYS.get(RANDOM.nextInt(PREVIEW_KEYS.size()));
                currentRandomKey3 = PREVIEW_KEYS.get(RANDOM.nextInt(PREVIEW_KEYS.size()));
                currentItemIndex1 = (currentItemIndex1 + 1) % EXAMPLE_ITEMS.length;
                currentItemIndex2 = (currentItemIndex2 + 1) % EXAMPLE_ITEMS.length;
                currentItemIndex3 = (currentItemIndex3 + 1) % EXAMPLE_ITEMS.length;
                lastBindChange = currentTime;
            }
        }
    }

    private boolean isBindActive(BindInfo bind) {
        return !HudHelper.nullCheck() && bind.keyBind.setting().isVisible() && bind.keyBind.setting().getKey() != -1 && bind.keyBind.setting().getKey() != 0;
    }

    @Override
    public void drawDraggable(DrawContext context) {
        if (!Hud.getInstance().interfaceSettings.isSelected("Binds") || !Hud.getInstance().state || animation.isFinished(Direction.BACKWARDS))
            return;
        MatrixStack matrix = context.getMatrices();
        final long now = System.currentTimeMillis();
        float animationValue = animation.getOutput().floatValue();
        if (animationValue <= 0) return;
        List<BindInfo> activeBinds = activeBindsCache;
        float padding = 3;
        float iconSize = 16;
        float spacing = 3;
        float rowHeight = iconSize + 2 * padding;
        float posX = getX();
        float posY = getY();
        int totalBinds = activeBinds.size();
        int rowCount = (int) Math.ceil((double) totalBinds / BINDS_PER_ROW);
        List<Float> rowWidths = new ArrayList<>();
        float firstRowWidth = 0;
        if (font13 == null) {
            font13 = Fonts.getSize(13, Fonts.Type.DEFAULT);
            font11 = Fonts.getSize(11, Fonts.Type.DEFAULT);
            font10 = Fonts.getSize(10, Fonts.Type.DEFAULT);
        }
        if (activeBinds.isEmpty() && HudHelper.isChat(mc.currentScreen)) {
            rowCount = 1;
            java.lang.String name = "Example Bind";
            java.lang.String[] keys = {currentRandomKey1, currentRandomKey2, currentRandomKey3};
            float textWidth = 0;
            for (String key : keys) {
                textWidth = Math.max(textWidth, font11.getStringWidth(key));
            }
            float bindWidth = iconSize + padding + textWidth + padding + 4;
            float totalWidth = bindWidth * 3 + spacing * 2;
            rowWidths.add(totalWidth);
            firstRowWidth = totalWidth;
        } else {
            for (int row = 0; row < rowCount; row++) {
                float currentX = padding;
                int bindsInThisRow = Math.min(BINDS_PER_ROW, totalBinds - row * BINDS_PER_ROW);
                for (int i = 0; i < bindsInThisRow; i++) {
                    int bindIndex = row * BINDS_PER_ROW + i;
                    BindInfo bind = activeBinds.get(bindIndex);
                    java.lang.String keyName = StringHelper.getBindName(bind.keyBind.setting().getKey());
                    float textWidth = font13.getStringWidth(keyName);
                    float bindWidth = iconSize + padding + textWidth + padding + 4;
                    currentX += bindWidth + spacing;
                }
                if (bindsInThisRow > 0) {
                    currentX -= spacing;
                }
                rowWidths.add(currentX);
                if (row == 0) {
                    firstRowWidth = currentX;
                }
            }
        }
        float width = firstRowWidth + padding;
        float height = rowHeight * rowCount;
        if (activeBinds.isEmpty() && HudHelper.isChat(mc.currentScreen)) {
            java.lang.String name = "Example Bind";
            java.lang.String[] keys = {currentRandomKey1, currentRandomKey2, currentRandomKey3};
            ItemStack[] stacks = {new ItemStack(EXAMPLE_ITEMS[currentItemIndex1]), new ItemStack(EXAMPLE_ITEMS[currentItemIndex2]), new ItemStack(EXAMPLE_ITEMS[currentItemIndex3])};
            if (preview_born_ms == 0) preview_born_ms = now;
            float offsetX = padding;
            for (int i = 0; i < 3; i++) {
                final float enter = ease_out_cubic(clamp01((now - preview_born_ms - i * 45L) / 200f));
                matrix.push();
                matrix.translate(0f, (1f - enter) * 6f, 0f);
                float textWidth = font11.getStringWidth(keys[i]) + 1.5f;
                float backgroundWidth = iconSize + padding + textWidth + padding + 4;

                int[] gradientColors = ColorAssist.getRectangleGradientColors();
                blur.render(ShapeProperties.create(matrix, posX + offsetX - 1, posY, backgroundWidth, rowHeight - 6)
                        .round(3).quality(com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality())
                        .color(ColorAssist.getBlurColor())
                        .build());
                rectangle.render(ShapeProperties.create(matrix, posX + offsetX - 1, posY, backgroundWidth, rowHeight - 6)
                        .round(3)
                        .thickness(0.1f)
                        .outlineColor(ColorAssist.getOutlineColor())
                        .color(gradientColors[0], gradientColors[0], gradientColors[1], gradientColors[1])
                        .build());
                rectangle.render(ShapeProperties.create(matrix, posX + offsetX - 1, posY, 16, rowHeight - 6)
                        .round(0, 0, 3, 3)
                        .outlineColor(ColorAssist.getOutlineColor())
                        .color(gradientColors[0], gradientColors[0], gradientColors[1], gradientColors[1])
                        .build());

                renderItemStack(context, stacks[i], posX + offsetX + 2, posY + padding + 0.5f);

                float textX = posX + offsetX + iconSize + padding;
                float textY = posY + padding + (iconSize - font11.getStringHeight(keys[i])) / 2;
                int textAlpha = (int) Math.min(255, Math.max(0, 255 * animationValue));
                font11.drawString(matrix, keys[i], textX + 1f, textY + 3f, ColorAssist.getText(textAlpha / 255f));

                font10.drawString(matrix, "64", posX + offsetX + 7, posY + rowHeight - 12, ColorAssist.getText(textAlpha / 255f));
                matrix.pop();

                offsetX += backgroundWidth + spacing;
            }
        } else {
            preview_born_ms = 0;
            // пилюли, показываемые прямо сейчас: время их первого появления не сбрасывается
            active_pill_items.clear();
            for (BindInfo active : activeBinds) {
                active_pill_items.add(active.keyBind.item());
            }
            pill_born_ms.keySet().retainAll(active_pill_items);
            for (int row = 0; row < rowCount; row++) {
                float rowWidth = rowWidths.get(row);
                float offsetX = row == 0 ? padding : padding + (firstRowWidth - rowWidth) / 2;
                float currentY = posY + row * rowHeight;
                int bindsInThisRow = Math.min(BINDS_PER_ROW, totalBinds - row * BINDS_PER_ROW);
                for (int i = 0; i < bindsInThisRow; i++) {
                    int bindIndex = row * BINDS_PER_ROW + i;
                    BindInfo bind = activeBinds.get(bindIndex);
                    ItemStack stack = createColoredPotion(bind.keyBind.item(), bind.color);
                    java.lang.String keyName = StringHelper.getBindName(bind.keyBind.setting().getKey());
                    // Ширина пилюли меряется тем же font13, которым текст рисуется
                    // ниже (см. font13.drawString): font11 давал более узкий
                    // backgroundWidth и надпись вылезала за фон/хитбокс.
                    float textWidth = font13.getStringWidth(keyName) + 1.5f;
                    float backgroundWidth = iconSize + padding + textWidth + padding + 4;

                    int itemCount = itemCounts.getOrDefault(bind.keyBind.item(), 0);
                    boolean isEmpty = itemCount == 0;

                    float cooldownProgress = getCooldownProgress(bind.keyBind.item());
                    float totalHeight = rowHeight - 6;
                    float cooldownHeight = totalHeight * cooldownProgress;
                    float cooldownStartY = currentY + totalHeight - cooldownHeight;

                    // стартовая задержка по позиции пилюли + вспышка «бинд сработал»
                    final long bornAt = pill_born_ms.computeIfAbsent(bind.keyBind.item(), k -> now);
                    final float enter = ease_out_cubic(clamp01((now - bornAt - Math.min(bindIndex, 5) * 40L) / 200f));
                    final float pulse = pulse_env(now - pulse_ms.getOrDefault(bind.keyBind.item(), 0L), 450L);

                    matrix.push();
                    matrix.translate(0f, (1f - enter) * 6f, 0f);
                    if (pulse > 0.01f) {
                        // акцентное свечение вокруг пилюли на время пульса
                        rectangle.render(ShapeProperties.create(matrix, posX + offsetX - 2.5f, currentY - 1.5f,
                                        backgroundWidth + 3f, rowHeight - 3f)
                                .round(4f)
                                .color(ColorAssist.getClientColor(0.9f * pulse))
                                .build());
                    }

                    blur.render(ShapeProperties.create(matrix, posX + offsetX - 1, currentY, backgroundWidth, rowHeight - 6)
                            .round(3).quality(com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality())
                            .color(ColorAssist.getBlurColor())
                            .build());
                    if (isEmpty) {
                        rectangle.render(ShapeProperties.create(matrix, posX + offsetX - 1, currentY, backgroundWidth, rowHeight - 6)
                                .round(3)
                                .outlineColor(ColorAssist.getOutlineColor())
                                .color(EMPTY_BG_1, EMPTY_BG_1, EMPTY_BG_2, EMPTY_BG_2)
                                .build());
                        rectangle.render(ShapeProperties.create(matrix, posX + offsetX - 1, currentY, 16, rowHeight - 6)
                                .round(0, 0, 3, 3)
                                .outlineColor(ColorAssist.getOutlineColor())
                                .color(EMPTY_ICON_1, EMPTY_ICON_2, EMPTY_ICON_2, EMPTY_ICON_2)
                                .build());
                    } else {
                        int[] gradientColors = ColorAssist.getRectangleGradientColors();
                        rectangle.render(ShapeProperties.create(matrix, posX + offsetX - 1, currentY, backgroundWidth, rowHeight - 6)
                                .round(3)
                                .outlineColor(ColorAssist.getOutlineColor())
                                .color(gradientColors[0], gradientColors[0], gradientColors[1], gradientColors[1])
                                .build());
                        if (cooldownProgress > 0) {
                            int progressColor = PROGRESS_COLORS[Math.min(100, Math.max(0, (int) (cooldownProgress * 100)))];

//                            rectangle.render(ShapeProperties.create(matrix, posX + offsetX + 15, cooldownStartY, 15.75f, cooldownHeight)
//                                    .round(cooldownProgress >= 0.85f ? 3 : 0, 3, 0, 0)
//                                    .color(
//                                            progressColorBright.getRGB(),
//                                            progressColorBright.getRGB(),
//                                            progressColorDark.getRGB(),
//                                            progressColorDark.getRGB())
//                                    .build());
                        }

                        rectangle.render(ShapeProperties.create(matrix, posX + offsetX - 1, currentY, 16, rowHeight - 6)
                                .round(0, 0, 3, 3)
                                .outlineColor(ColorAssist.getOutlineColor())
                                .color(gradientColors[0], gradientColors[0], gradientColors[1], gradientColors[1])
                                .build());
                    }

                    // подсветка слота иконки акцентом поверх фона, но под предметом
                    if (pulse > 0.01f) {
                        rectangle.render(ShapeProperties.create(matrix, posX + offsetX - 1, currentY, 16, rowHeight - 6)
                                .round(0, 0, 3, 3)
                                .color(ColorAssist.getClientColor(0.45f * pulse))
                                .build());
                    }

                    renderItemStack(context, stack, posX + offsetX + 2, currentY + padding + 0.5f);

                    float textX = posX + offsetX + iconSize + padding;
                    float textY = currentY + padding + (iconSize - font11.getStringHeight(keyName)) / 2;
                    int textAlpha = (int) Math.min(255, Math.max(0, 255 * animationValue));
                    font13.drawString(matrix, keyName, textX + 1f, textY + 3f, ColorAssist.getText(textAlpha / 255f));

                    font10.drawString(matrix, String.valueOf(itemCount), posX + offsetX + 7, currentY + rowHeight - 12, ColorAssist.getText(textAlpha / 255f));
                    matrix.pop();

                    offsetX += backgroundWidth + spacing;
                }
            }
        }
        // Размер обновляем в ОБЕИХ ветках: в превью (нет активных биндов + открыт чат)
        // hitbox иначе расходился с нарисованным контентом, и элемент было не схватить.
        setWidth((int) width);
        setHeight((int) height);
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : Math.min(1f, value);
    }

    private static float ease_out_cubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /** Огибающая пульса: синус на отрезке [0, duration), иначе 0. */
    private static float pulse_env(long elapsedMs, long durationMs) {
        if (elapsedMs < 0 || elapsedMs >= durationMs) return 0f;
        return (float) Math.sin(Math.PI * (elapsedMs / (double) durationMs));
    }
}
