/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;
import com.loadercode.tabvisuals.gui.hud.compat.TotemStats;
import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import com.loadercode.tabvisuals.render.math.time.StopWatch;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.gui.hud.compat.HolyWorldModuleGuard;
import com.loadercode.tabvisuals.common.animation.Animation;
import com.loadercode.tabvisuals.common.animation.Direction;
import com.loadercode.tabvisuals.common.animation.implement.Decelerate;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.math.calc.Calculate;
import com.loadercode.tabvisuals.render.geometry.Render2D;
import com.loadercode.tabvisuals.render.scissor.ScissorAssist;
import java.awt.*;

public class TargetHud extends AbstractDraggable {
    private final Animation animation = new Decelerate().setMs(650).setValue(1);
    private final Animation faceAlphaAnimation = new Decelerate().setMs(125).setValue(1);
    private final StopWatch stopWatch = new StopWatch();
    private final StopWatch distanceUpdateTimer = new StopWatch();
    private final StopWatch lastAttackTimer = new StopWatch().setMs(4000);
    private static final long HOLYWORLD_ATTACK_HOLD_MS = 4000L;
    private LivingEntity lastTarget;
    private LivingEntity lastAttackedTarget;
    private Item lastItem = Items.AIR;
    private float health;
    private float absorption;
    private float displayedDistance;

    public TargetHud() {
        super("Target Hud", 10, 80, 100, 36, true);
    }

    @Override
    public boolean visible() {
        return scaleAnimation.isDirection(Direction.FORWARDS);
    }

    @Override
    public void tick() {
        Entity targetedEntity = mc.targetedEntity;
        if (targetedEntity instanceof LivingEntity livingEntity) {
            if (shouldHideTarget(livingEntity)) {
                if (stopWatch.finished(500)) {
                    stopAnimation();
                    faceAlphaAnimation.setDirection(Direction.BACKWARDS);
                }
                return;
            }
            lastTarget = livingEntity;
            startAnimation();
            faceAlphaAnimation.setDirection(Direction.FORWARDS);
            stopWatch.reset();
        } else if (shouldHoldLastAttackedTarget()) {
            lastTarget = lastAttackedTarget;
            startAnimation();
            faceAlphaAnimation.setDirection(Direction.FORWARDS);
            stopWatch.reset();
        } else if (HudHelper.isChat(mc.currentScreen)) {
            lastTarget = mc.player;
            startAnimation();
            faceAlphaAnimation.setDirection(Direction.FORWARDS);
            stopWatch.reset();
        } else if (stopWatch.finished(500)) {
            stopAnimation();
            faceAlphaAnimation.setDirection(Direction.BACKWARDS);
        }
    }

    public void onAttack(Entity target) {
        if (!HolyWorldModuleGuard.isHolyWorldServer()) return;
        if (target instanceof PlayerEntity player && player != mc.player) {
            lastAttackedTarget = player;
            lastTarget = player;
            lastAttackTimer.reset();
            stopWatch.reset();
            startAnimation();
            faceAlphaAnimation.setDirection(Direction.FORWARDS);
        }
    }

    private boolean shouldHoldLastAttackedTarget() {
        return HolyWorldModuleGuard.isHolyWorldServer()
                && lastAttackedTarget != null
                && lastAttackedTarget.isAlive()
                && !lastAttackedTarget.isRemoved()
                && !lastAttackTimer.finished(HOLYWORLD_ATTACK_HOLD_MS);
    }

    @Override
    public void drawDraggable(DrawContext context) {
        if (Hud.getInstance().interfaceSettings.isSelected("Target Hud") && Hud.getInstance().state) {
            if (lastTarget != null) {
                if (shouldHideTarget(lastTarget)) {
                    return;
                }
                MatrixStack matrix = context.getMatrices();
                com.loadercode.tabvisuals.render.geometry.Render2D.renderElementShadow(matrix, getX(), getY(), getWidth(), getHeight());
                drawMain(context, matrix);
                drawArmor(context, matrix);
                drawFace(context);
            }
        }
    }

    private boolean shouldHideTarget(LivingEntity entity) {
        return entity instanceof PlayerEntity
                && entity.isInvisible()
                && !hasArmor(entity);
    }

    private boolean hasArmor(LivingEntity entity) {
        ItemStack head = entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD);
        ItemStack chest = entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
        ItemStack legs = entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS);
        ItemStack feet = entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET);
        
        return (!head.isEmpty() && head.getItem() instanceof ArmorItem) ||
               (!chest.isEmpty() && chest.getItem() instanceof ArmorItem) ||
               (!legs.isEmpty() && legs.getItem() instanceof ArmorItem) ||
               (!feet.isEmpty() && feet.getItem() instanceof ArmorItem);
    }

    private void drawMain(DrawContext context, MatrixStack matrix) {

        FontRenderer font = Fonts.getSize(18, Fonts.Type.REGULAR);
        FontRenderer distancefont = Fonts.getSize(12, Fonts.Type.SEMI);
        float hp = HudHelper.getHealth(lastTarget);
        String stringHp = (lastTarget.isInvisible() && !HolyWorldModuleGuard.isHolyWorldServer()) ? " ??" : HudHelper.getHealthString(hp);
        health = MathHelper.clamp(Calculate.interpolateSmooth(1, health, hp / lastTarget.getMaxHealth() * 360), 0, 360);
        float absorptionAmount = HudHelper.getAbsorption(lastTarget);
        absorption = MathHelper.clamp(Calculate.interpolateSmooth(1, absorption, absorptionAmount / 20.0F * 360), 0, 360);
        // Не assert: в продакшене он выключен, а HUD рисуется и в момент
        // выгрузки мира, когда игрока уже нет.
        if (mc.player == null) return;
        float actualDistance = mc.player.distanceTo(lastTarget);
        float roundedDistance = Math.round(actualDistance * 2) / 2.0f;
        int poppedTotems = lastTarget instanceof PlayerEntity player && TotemStats.getInstance() != null && TotemStats.getInstance().isState()
                ? TotemStats.getInstance().getPopCount(player)
                : 0;
        if (distanceUpdateTimer.finished(10)) {
            displayedDistance = MathHelper.clamp(Calculate.interpolateSmooth(0.5f, displayedDistance, roundedDistance), 0, 100);
            distanceUpdateTimer.reset();
        }
        String distanceText = String.format("%.1f", displayedDistance);

        float nameWidth = font.getStringWidth(lastTarget.getName().getString());
        float baseWidth = Math.max(34 + 36 + 10, 100);
        setWidth((int) baseWidth + 10);
        setHeight((int) 41);

        int[] gradientColors = ColorAssist.getRectangleGradientColors();
        blur.render(ShapeProperties.create(matrix, getX(), getY(), getWidth(), getHeight() - 10)
                    .round(6).quality(com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality())
                    .color(ColorAssist.getBlurColor())
                    .build());
            rectangle.render(ShapeProperties.create(matrix, getX(), getY(), getWidth(), getHeight() - 10)
                    .round(6)
                    .thickness(0.1f)
                    .outlineColor(ColorAssist.getOutlineColor())
                .color(gradientColors[0], gradientColors[1], gradientColors[2], gradientColors[3])
                .build());

        arc.render(ShapeProperties.create(matrix, getX() + getWidth() - 28.5f, getY() + 2.5f, 26, 26).round(0.26F).thickness(0.30f).end(361)
                .color(new Color(255,255,255,25).getRGB()).build());
        arc.render(ShapeProperties.create(matrix, getX() + getWidth() - 28.5f, getY() + 2.5f, 26, 26).round(0.26F).thickness(0.30f).end(health)
                .color(ColorAssist.fade(0), ColorAssist.fade(200), ColorAssist.fade(0), ColorAssist.fade(200)).build());
        // Золотые сердечки (поглощение от золотых и зачарованных яблок) показываем
        // на всех серверах — раньше они гаслись на FUNTIME-серверах.
        if (absorption > 0) {
            arc.render(ShapeProperties.create(matrix, getX() + getWidth() - 28.5f, getY() + 2.5f, 26, 26).round(0.26F).thickness(0.30f)
                    .end(absorption - 2.5f)
                    .color(new Color(255, 215, 0, 255).getRGB(), new Color(255, 128, 0, 255).getRGB(), new Color(255, 215, 0, 255).getRGB(), new Color(255, 128, 0, 255).getRGB())
                    .build());
        }

        if (nameWidth > 50) {
            ScissorAssist scissorManager = ScissorAssist.INSTANCE;
            scissorManager.push(matrix.peek().getPositionMatrix(), getX(), getY(), getWidth() - 29, getHeight());
            font.drawGradientString(matrix, lastTarget.getName().getString(), getX() + 29, getY() + 9f, ColorAssist.getText(), ColorAssist.getText(0.15F));
            distancefont.drawString(matrix, "Distance: " + distanceText, getX() + 29, getY() + 19f, ColorAssist.getText());
            scissorManager.pop();
        } else {
            font.drawString(matrix, lastTarget.getName().getString(), getX() + 29, getY() + 9f, ColorAssist.getText());
            distancefont.drawString(matrix, "Distance: " + distanceText, getX() + 29, getY() + 19f, ColorAssist.getText());
        }

        float arcCenterX = getX() + getWidth() - 30.5f / 2.0F;
        float arcCenterY = getY() + 30 / 2.0F;
        Fonts.getSize(11, Fonts.Type.BOLD).drawCenteredString(matrix, stringHp, arcCenterX, arcCenterY, ColorAssist.getText(225 / 255f));

        if (poppedTotems > 0) {
            String popText = String.valueOf(poppedTotems);
            FontRenderer totemCountFont = Fonts.getSize(10, Fonts.Type.BOLD);
            float textWidth = totemCountFont.getStringWidth(popText);
            float iconScale = 0.40f;
            float iconWidth = 16.0f * iconScale;
            float gap = 2.0f;
            float blockWidth = iconWidth + gap + textWidth;
            float leftBound = getX() + 31.0f;
            float rightBound = getX() + getWidth() - 35.0f;
            float totemX = MathHelper.clamp(getX() + 56.0f, leftBound, rightBound - blockWidth);
            float totemY = getY() + 21.5f;
            float countX = totemX + iconWidth + gap;
            float countY = totemY + 0.5f;
            Render2D.defaultDrawStack(context, Items.TOTEM_OF_UNDYING.getDefaultStack(), totemX, totemY, false, false, iconScale);
            totemCountFont.drawString(matrix, popText, countX, countY, ColorAssist.getText());
        }
    }

    private void drawArmor(DrawContext context, MatrixStack matrix) {
        ItemStack[] slots = new ItemStack[] {
                lastTarget.getMainHandStack(),
                lastTarget.getOffHandStack(),
                lastTarget.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD),
                lastTarget.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST),
                lastTarget.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS),
                lastTarget.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET)
        };
        float x = getX() + 21f;
        float y = getY() + 17;
        float slotSize = 16 * 0.5F + 2;
        matrix.push();
        matrix.translate(x, y, 0);
        int[] slotGradient = ColorAssist.getRectangleGradientColors();
        for (int i = 0; i < 6; i++) {
            float currentX = i * 11.5f;

            blur.render(ShapeProperties.create(matrix, currentX - 0.5f, 15F, slotSize + 1, slotSize + 1)
                        .round(3).quality(12)
                        .color(ColorAssist.getBlurColor())
                        .build());
                rectangle.render(ShapeProperties.create(matrix, currentX - 0.5f, 15F, slotSize + 1, slotSize + 1)
                        .round(3)
                        .thickness(0.1f)
                        .outlineColor(ColorAssist.getOutlineColor())
                        .color(slotGradient[0], slotGradient[1], slotGradient[2], slotGradient[3])
                    .build());

            if (!slots[i].isEmpty()) {
                Render2D.defaultDrawStack(context, slots[i], currentX, 15.5F, false, false, 0.5F);
            } else {
                String xText = "x";
                FontRenderer font = Fonts.getSize(12, Fonts.Type.DEFAULT);
                float textWidth = font.getStringWidth(xText);
                float textHeight = font.getStringHeight(xText);
                float textX = currentX + (slotSize - textWidth) / 2.0F;
                float textY = 15.5F + (slotSize - textHeight) / 2.0F;
                font.drawString(matrix, xText, textX, textY + 6.25f, ColorAssist.getText());
            }
        }
        matrix.pop();
    }

    private void drawUsingItem(DrawContext context, MatrixStack matrix) {
        animation.setDirection(lastTarget.isUsingItem() ? Direction.FORWARDS : Direction.BACKWARDS);
        if (!lastTarget.getActiveItem().isEmpty() && lastTarget.getActiveItem().getCount() != 0) {
            lastItem = lastTarget.getActiveItem().getItem();
        }
        if (!animation.isFinished(Direction.BACKWARDS) && !lastItem.equals(Items.AIR)) {
            int size = 24;
            float anim = animation.getOutput().floatValue();
            float progress = Math.min(360f, (lastTarget.getItemUseTime() + tickCounter.getTickDelta(false)) / maxUseTicks(lastItem) * 360);
            float x = getX() - (size + 5) * anim;
            float y = getY() + 4;
            ScissorAssist scissorManager = ScissorAssist.INSTANCE;
            scissorManager.push(matrix.peek().getPositionMatrix(), getX() - 50, getY(), 50, getHeight());
            Calculate.setAlpha(anim, () -> {
                blur.render(ShapeProperties.create(matrix, x, y, size, size).quality(com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality())
                        .round(12).softness(1).thickness(2).outlineColor(ColorAssist.getOutline(0)).color(ColorAssist.getRect(0.7F)).build());
                arc.render(ShapeProperties.create(matrix, x, y, size, size).round(0.38F).thickness(0.30f).end(progress)
                        .color(ColorAssist.fade(0), ColorAssist.fade(200), ColorAssist.fade(0), ColorAssist.fade(200)).build());
                Render2D.defaultDrawStack(context, lastItem.getDefaultStack(), x + 3f, y + 3f, false, false, 1f);
            });
            scissorManager.pop();
        }
    }

    /** Vanilla use-time in ticks to full charge (ItemTask stand-in). */
    private static float maxUseTicks(Item item) {
        if (item == Items.BOW || item == Items.SPYGLASS) {
            return 20f;
        }
        if (item == Items.CROSSBOW) {
            return 25f;
        }
        if (item == Items.SHIELD) {
            return 5f;
        }
        return 32f;
    }

    private void drawFace(DrawContext context) {
        EntityRenderer<? super LivingEntity, ?> baseRenderer = mc.getEntityRenderDispatcher().getRenderer(lastTarget);
        if (!(baseRenderer instanceof LivingEntityRenderer<?, ?, ?>)) {
            return;
        }
        @SuppressWarnings("unchecked")
        LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?> renderer = (LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>) baseRenderer;
        LivingEntityRenderState state = renderer.getAndUpdateRenderState(lastTarget, tickCounter.getTickDelta(false));
        Identifier textureLocation = renderer.getTexture(state);
        float alpha = faceAlphaAnimation.getOutput().floatValue();
        Calculate.setAlpha(alpha, () -> {
            Render2D.drawTexture(context, textureLocation, getX() + 5, getY() + 5.5F, 20, 4, 8, 8, 64, ColorAssist.getRect(1), ColorAssist.multRed(-1, 1 + lastTarget.hurtTime / 4F));
        });
    }
}
