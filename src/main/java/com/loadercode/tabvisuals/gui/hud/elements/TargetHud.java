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
    private final StopWatch lastAttackTimer = new StopWatch().setMs(4000);
    private static final long HOLYWORLD_ATTACK_HOLD_MS = 4000L;

    // Появление/исчезновение и «хвост» убытия здоровья — временные шкалы (мс),
    // прогресс пересчитывается каждый кадр от currentTimeMillis, не от FPS.
    private static final long LOCK_IN_MS = 280L;
    private static final long HIT_FLASH_MS = 350L;
    private static final float HEALTH_TAU = 0.12f;
    private static final float DRAIN_TAU = 0.35f;
    private static final float ABSORPTION_TAU = 0.20f;
    private static final float DISTANCE_TAU = 0.25f;
    private static final float FADE_TAU = 0.08f;

    private LivingEntity lastTarget;
    private LivingEntity lastAttackedTarget;
    private Item lastItem = Items.AIR;
    private float health;
    private float absorption;
    private float displayedDistance;
    private LivingEntity lockInTarget;
    private long lockInStartMs;
    private long lastFrameMs;
    private float frameDt = 0.05f;
    private float fadeProgress = 1f;
    private float laggedHealth;
    private float lastHealth = -1f;
    private int lastHurtTime;
    private long hitFlashStartMs;
    private float hitFlash;

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
        if (!(Hud.getInstance().interfaceSettings.isSelected("Target Hud") && Hud.getInstance().state)) {
            return;
        }
        if (lastTarget == null || shouldHideTarget(lastTarget)) {
            return;
        }

        long now = System.currentTimeMillis();
        frameDt = frame_delta_seconds(now);

        // затухание при потере цели: гаснет плавно, пока драйвер ещё рисует хвост закрытия
        fadeProgress += (1f - fadeProgress) * (1f - (float) Math.exp(-frameDt / FADE_TAU));

        // scale-in при захвате новой цели (или первом показе)
        if (lastTarget != lockInTarget) {
            lockInTarget = lastTarget;
            lockInStartMs = now;
        }
        float lock = ease_out_back(clamp01((now - lockInStartMs) / (float) LOCK_IN_MS));
        float scale = (0.92f + 0.08f * lock) * (0.85f + 0.15f * fadeProgress);

        setWidth(110);
        setHeight(41);

        MatrixStack matrix = context.getMatrices();
        float centerX = getX() + getWidth() / 2f;
        float centerY = getY() + (getHeight() - 10) / 2f;
        matrix.push();
        try {
            if (Math.abs(scale - 1f) > 0.001f) {
                matrix.translate(centerX, centerY, 0f);
                matrix.scale(scale, scale, 1f);
                matrix.translate(-centerX, -centerY, 0f);
            }
            Calculate.setAlpha(fadeProgress, () -> {
                com.loadercode.tabvisuals.render.geometry.Render2D.renderElementShadow(matrix, getX(), getY(), getWidth(), getHeight());
                drawMain(context, matrix);
                drawArmor(context, matrix);
                drawFace(context);
            });
        } finally {
            matrix.pop();
        }
    }

    /** Дельта кадра в секундах, ограничена сверху — защита от фризов. */
    private float frame_delta_seconds(long now) {
        float dt = lastFrameMs <= 0L ? 0.05f : (now - lastFrameMs) / 1000f;
        lastFrameMs = now;
        return MathHelper.clamp(dt, 0.001f, 0.1f);
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }

    /** Плавное притяжение к цели по экспоненте — одинаково на любом FPS. */
    private static float smooth_exp(float current, float target, float tau, float dt) {
        return current + (target - current) * (1f - (float) Math.exp(-dt / Math.max(0.001f, tau)));
    }

    /** easeOutBack: пружинный scale-in с лёгким перелётом. */
    private static float ease_out_back(float p) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float q = p - 1f;
        return 1f + c3 * q * q * q + c1 * q * q;
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
        long nowMs = System.currentTimeMillis();
        float hpTarget = MathHelper.clamp(hp / lastTarget.getMaxHealth() * 360f, 0f, 360f);
        health = MathHelper.clamp(smooth_exp(health, hpTarget, HEALTH_TAU, frameDt), 0f, 360f);
        // «хвост» убытия: догоняет основную дугу медленнее — видно при падении HP
        laggedHealth = MathHelper.clamp(smooth_exp(laggedHealth, hpTarget, DRAIN_TAU, frameDt), 0f, 360f);

        // вспышка при уроне: падение здоровья или rising edge hurtTime
        if (lastHealth >= 0f && hp < lastHealth - 0.05f) {
            hitFlashStartMs = nowMs;
        }
        lastHealth = hp;
        if (lastTarget.hurtTime > lastHurtTime) {
            hitFlashStartMs = nowMs;
        }
        lastHurtTime = lastTarget.hurtTime;
        hitFlash = 1f - clamp01((nowMs - hitFlashStartMs) / (float) HIT_FLASH_MS);

        float absorptionAmount = HudHelper.getAbsorption(lastTarget);
        absorption = MathHelper.clamp(smooth_exp(absorption, absorptionAmount / 20.0F * 360f, ABSORPTION_TAU, frameDt), 0f, 360f);
        // Не assert: в продакшене он выключен, а HUD рисуется и в момент
        // выгрузки мира, когда игрока уже нет.
        if (mc.player == null) return;
        float actualDistance = mc.player.distanceTo(lastTarget);
        float roundedDistance = Math.round(actualDistance * 2) / 2.0f;
        int poppedTotems = lastTarget instanceof PlayerEntity player && TotemStats.getInstance() != null && TotemStats.getInstance().isState()
                ? TotemStats.getInstance().getPopCount(player)
                : 0;
        displayedDistance = MathHelper.clamp(smooth_exp(displayedDistance, roundedDistance, DISTANCE_TAU, frameDt), 0f, 100f);
        String distanceText = String.format("%.1f", displayedDistance);

        float nameWidth = font.getStringWidth(lastTarget.getName().getString());

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

        // красная вспышка поверх панели при получении урона
        if (hitFlash > 0.01f) {
            rectangle.render(ShapeProperties.create(matrix, getX(), getY(), getWidth(), getHeight() - 10)
                    .round(6)
                    .color(ColorAssist.pack(255, 70, 70, Math.round(95 * hitFlash)))
                    .build());
        }

        arc.render(ShapeProperties.create(matrix, getX() + getWidth() - 28.5f, getY() + 2.5f, 26, 26).round(0.26F).thickness(0.30f).end(361)
                .color(new Color(255,255,255,25).getRGB()).build());
        // медленный «хвост» убытия под основной дугой здоровья
        if (laggedHealth > health + 1f) {
            arc.render(ShapeProperties.create(matrix, getX() + getWidth() - 28.5f, getY() + 2.5f, 26, 26).round(0.26F).thickness(0.30f).end(laggedHealth)
                    .color(ColorAssist.multAlpha(ColorAssist.fade(0), 0.55f), ColorAssist.multAlpha(ColorAssist.fade(200), 0.55f),
                            ColorAssist.multAlpha(ColorAssist.fade(0), 0.55f), ColorAssist.multAlpha(ColorAssist.fade(200), 0.55f))
                    .build());
        }
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
        float alpha = faceAlphaAnimation.getOutput().floatValue() * fadeProgress;
        Calculate.setAlpha(alpha, () -> {
            Render2D.drawTexture(context, textureLocation, getX() + 5, getY() + 5.5F, 20, 4, 8, 8, 64, ColorAssist.getRect(1), ColorAssist.multRed(-1, 1 + lastTarget.hurtTime / 4F));
        });
    }
}
