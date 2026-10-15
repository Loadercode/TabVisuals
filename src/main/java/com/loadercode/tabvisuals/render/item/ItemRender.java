/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.item;

import com.loadercode.tabvisuals.render.geometry.Render2D;
import lombok.experimental.UtilityClass;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.ComponentMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.math.ColorHelper;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.color.ColorAssist;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@UtilityClass
public class ItemRender implements QuickImports {
    private final int SPRITE_CACHE_LIMIT = 4096;

    // Сентинел вместо null: computeIfAbsent не кэширует null, air/стек без модели
    // иначе пересчитывался бы каждый кадр.
    private final CachedSprite NO_SPRITE = new CachedSprite(null, 0, 0xFFFFFF);

    // LRU с accessOrder: get() переносит ключ в конец, переполнение вытесняет
    // самый старый ключ, а не весь кэш целиком.
    public final Map<SpriteKey, CachedSprite> SPRITE_CACHE = new LinkedHashMap<>(64, 0.75F, true);

    public void drawItem(MatrixStack matrix, ItemStack stack, float x, float y, boolean count, boolean bar) {
        CachedSprite cachedSprite = getSpriteTexture(stack);
        if (cachedSprite != null) {
            Render2D.drawSprite(matrix, cachedSprite.sprite, x, y,  16, 16);
        }
        if (count) drawCount(matrix, stack, x, y);
        if (bar) drawBar(matrix, stack, x, y);
    }

    private void drawCount(MatrixStack matrix, ItemStack stack, float x, float y) {
        int count = stack.getCount();
        String text = count > 1 ? count + "" : "";
        if (!text.isEmpty()) {
            FontRenderer font = Fonts.getSize(16);
            font.drawString(matrix, text, x + 16 - font.getStringWidth(text), y + 11, ColorAssist.getText());
        }
    }

    private void drawBar(MatrixStack matrix, ItemStack stack, float x, float y) {
        if (stack.isItemBarVisible()) {
            rectangle.render(ShapeProperties.create(matrix, x + 1.5F, y + 13, 13, 2).color(-16777216).build());
            rectangle.render(ShapeProperties.create(matrix, x + 1.5F, y + 13, stack.getItemBarStep(), 1).color(ColorHelper.fullAlpha(stack.getItemBarColor())).build());
        }
    }

    private CachedSprite getSpriteTexture(ItemStack stack) {
        SpriteKey key = sprite_key(stack);
        CachedSprite cached = SPRITE_CACHE.get(key);
        if (cached == null) {
            cached = resolve_sprite(stack);
            // Кладём и NO_SPRITE: закэшированный промах не пересчитывается каждый кадр.
            SPRITE_CACHE.put(key, cached);
            if (SPRITE_CACHE.size() > SPRITE_CACHE_LIMIT) {
                Iterator<SpriteKey> eldest = SPRITE_CACHE.keySet().iterator();
                eldest.next();
                eldest.remove();
            }
        }
        return cached == NO_SPRITE ? null : cached;
    }

    private SpriteKey sprite_key(ItemStack stack) {
        // Снимок, а не живой стек: ItemStack не переопределяет equals/hashCode,
        // мутации стека не меняют его identity-хэш. getImmutableComponents() —
        // value-снимок (MergedComponentMap с copy-on-write), он не меняется после
        // мутаций исходного стека. count входит в ключ: NumericProperties
        // регистрирует "count" (CountProperty читает getCount()), пак может
        // менять модель по количеству.
        return new SpriteKey(stack.getItem(), stack.getCount(), stack.getDamage(), stack.getImmutableComponents());
    }

    private CachedSprite resolve_sprite(ItemStack stack) {
        ItemRenderState state = new ItemRenderState();
        mc.getItemModelManager().update(state, stack, ModelTransformationMode.GUI, mc.world, null, 0);

        Sprite sprite = getFirstParticleSprite(state);
        if (sprite != null) {
            // glId берётся у атласной текстуры TextureManager — её владелец не мы,
            // поэтому при вытеснении записи ничего не удаляем (иначе сломали бы общий атлас).
            int glId = mc.getTextureManager().getTexture(sprite.getAtlasId()).getGlId();
            return new CachedSprite(sprite, glId, 0xFFFFFF);
        }

        return NO_SPRITE;
    }

    private Sprite getFirstParticleSprite(ItemRenderState state) {
        // Порядок важен: индекс 0 трогаем только если в стеке есть хоть один
        // слой — иначе пустой слой давал ArrayIndexOutOfBoundsException,
        // которую выше по стеку глотал per-drawable catch и часть HUD просто
        // пропадала из кадра.
        if (state.layerCount == 0) return null;
        BakedModel model = state.layers[0].model;
        if (model == null) return null;
        return model.getParticleSprite();
    }

    public record SpriteKey(Item item, int count, int damage, ComponentMap components) {}

    public record CachedSprite(Sprite sprite, int glId, int color) {}
}
