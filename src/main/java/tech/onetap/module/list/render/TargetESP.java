package tech.onetap.module.list.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import tech.onetap.Onetap;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ColorSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.math.MathUtil;
import tech.onetap.util.render.providers.ColorProvider;

@ModuleInformation(moduleName = "Target ESP", moduleDesc = "Визуальный эффект на текущей цели", moduleCategory = ModuleCategory.RENDER)
public class TargetESP extends Module {

    private static final Identifier GLOW_TEXTURE = Identifier.of("mre", "images/glow.png");

    private final ModeSetting mode = new ModeSetting("Режим", "Призраки", "Призраки", "Маркер", "Круг", "Кристаллы", "Celestial");
    private final BooleanSetting redOnAuraHit = new BooleanSetting("Покраснение при ударе", true);
    private final BooleanSetting twoColorsTheme = new BooleanSetting("Два цвета", true);
    private final BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", false).setVisible(() -> mode.is("Celestial"));

    private final SliderSetting markerSpeed = new SliderSetting("Скорость вращения", 1.0f, 0.1f, 5.0f, 0.1f).setVisible(() -> mode.is("Маркер"));
    private final SliderSetting jelloSpeed = new SliderSetting("Скорость волны", 1.0f, 0.1f, 5.0f, 0.1f).setVisible(() -> mode.is("Круг"));
    private final SliderSetting ghostsSpeed = new SliderSetting("Скорость орбиты", 1.0f, 0.1f, 5.0f, 0.1f).setVisible(() -> mode.is("Призраки"));

    private final SliderSetting crystalSize = new SliderSetting("Размер кристаллов", 0.12f, 0.01f, 0.5f, 0.01f).setVisible(() -> mode.is("Кристаллы"));
    private final SliderSetting crystalSpeed = new SliderSetting("Скорость вращения", 2.5f, 0.0f, 10.0f, 0.1f).setVisible(() -> mode.is("Кристаллы"));
    private final SliderSetting crystalCount = new SliderSetting("Кол-во кристаллов", 6f, 1f, 12f, 1f).setVisible(() -> mode.is("Кристаллы"));

    private final ColorSetting celestialColorA = new ColorSetting("Цвет 1", 0xFF5B9AFF).setVisible(() -> mode.is("Celestial"));
    private final ColorSetting celestialColorB = new ColorSetting("Цвет 2", 0xFFC45CFF).setVisible(() -> mode.is("Celestial"));
    private final SliderSetting celestialSpeed = new SliderSetting("Скорость вращения", 1.0f, 0.2f, 3.0f, 0.1f).setVisible(() -> mode.is("Celestial"));
    private final SliderSetting celestialSize = new SliderSetting("Размер спиралей", 1.0f, 0.5f, 2.0f, 0.05f).setVisible(() -> mode.is("Celestial"));
    private final SliderSetting celestialArms = new SliderSetting("Количество рук", 4f, 2f, 8f, 1f).setVisible(() -> mode.is("Celestial"));
    private final SliderSetting celestialSegments = new SliderSetting("Сегменты на руку", 14f, 8f, 24f, 1f).setVisible(() -> mode.is("Celestial"));

    private final Animation animation = new Animation(Easing.EXPO_OUT, 500);

    private Entity lastTarget = null;
    private float rotationAngle = 0.0F;
    private float rotationSpeed = 0.0F;
    private boolean isReversing = false;
    private boolean registered = false;

    private float animationNurik = 0;
    private long currentTime = System.currentTimeMillis();

    private static final double CELESTIAL_ARC = Math.toRadians(60.0);
    private static final double CELESTIAL_VERTICAL_AMPLITUDE = 0.7;
    private static final double CELESTIAL_TWO_PI = Math.PI * 2.0;
    private float celestialFade = 0.0f;
    private float celestialLastTime = -1.0f;

    private final WorldRenderEvents.Last listener = context -> {
        onRenderWorldLast(context.matrixStack(), context.camera(), context.tickCounter().getTickDelta(true));
    };

    @Override
    public void onEnable() {
        if (!registered) {
            WorldRenderEvents.LAST.register(listener);
            registered = true;
        }
        super.onEnable();
    }

    private void onRenderWorldLast(MatrixStack matrices, Camera camera, float tickDelta) {
        if (!isEnabled()) return;

        Entity target = Onetap.getInstance().getModuleStorage().get(KillAura.class).getTarget();

        if (target != null && target != mc.player && !(target instanceof ArmorStandEntity)) {
            if (lastTarget != target) {
            }
            lastTarget = target;
            animation.run(1);
        } else {
            animation.run(0);
            if (animation.getValue() == 0) {
                lastTarget = null;
            }
        }

        if (lastTarget == null || animation.getValue() <= 0.01) return;

        switch (mode.getValue()) {
            case "Маркер" -> drawMarker(matrices, camera, tickDelta);
            case "Круг" -> drawJelloMode(matrices, camera, tickDelta);
            case "Призраки" -> drawGhosts(matrices, camera, tickDelta);
            case "Кристаллы" -> drawCrystals(matrices, camera, tickDelta);
            case "Celestial" -> drawCelestial(matrices, camera, tickDelta);
        }
    }

    private float getAuraHurtFactor(Entity entity) {
        if (redOnAuraHit.getValue() && entity instanceof LivingEntity living) {
            return MathHelper.clamp(living.hurtTime / 10.0f, 0.0f, 1.0f);
        }
        return 0.0f;
    }

    private int mixWithHurt(int baseColor, float hurt) {
        if (hurt <= 0.0f) return baseColor;
        return interpolateColor(baseColor, 0xFFFF5555, hurt);
    }

    private int interpolateColor(int color1, int color2, float factor) {
        if (factor <= 0.0F) return color1;
        if (factor >= 1.0F) return color2;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        return (0xFF << 24) | ((int) (r1 + (r2 - r1) * factor) << 16) | ((int) (g1 + (g2 - g1) * factor) << 8) | (int) (b1 + (b2 - b1) * factor);
    }

    private int getGradient(int color1, int color2, float factor) {
        return interpolateColor(color1, color2, factor);
    }

    private double interpolate(double current, double old, double scale) {
        return old + (current - old) * scale;
    }

    private void updateRotation() {
        float speed = (float) markerSpeed.getValue();
        if (!isReversing) {
            rotationSpeed += 0.01F * speed;
            if (rotationSpeed > 2.3F * speed) {
                rotationSpeed = 2.3F * speed;
                isReversing = true;
            }
        } else {
            rotationSpeed -= 0.01F * speed;
            if (rotationSpeed < -2.3F * speed) {
                rotationSpeed = -2.3F * speed;
                isReversing = false;
            }
        }
        rotationAngle += rotationSpeed;
        rotationAngle %= 360.0F;
    }

    private void drawMarker(MatrixStack matrices, Camera camera, float tickDelta) {
        double x = interpolate(lastTarget.getX(), lastTarget.lastRenderX, tickDelta);
        double y = interpolate(lastTarget.getY(), lastTarget.lastRenderY, tickDelta) + lastTarget.getHeight() / 2.0;
        double z = interpolate(lastTarget.getZ(), lastTarget.lastRenderZ, tickDelta);
        Vec3d camPos = camera.getPos();

        matrices.push();
        matrices.translate(x - camPos.x, y - camPos.y, z - camPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

        float hurtFactor = getAuraHurtFactor(lastTarget);
        float baseScale = (float) (0.125f * animation.getValue());
        float scale = baseScale * (1.0f + 0.25f * hurtFactor);
        matrices.scale(-scale, -scale, scale);

        updateRotation();
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotationAngle));

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, Identifier.of("mre", "images/target.png"));
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        float alpha = (float) animation.getValue();
        float size = 6.5f;
        int color = mixWithHurt(ColorProvider.getThemeColor(), hurtFactor);
        color = ColorProvider.setAlpha(color, (int) (alpha * 255));

        Matrix4f mat = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        buffer.vertex(mat, -size, size, 0.0f).texture(0.0f, 1.0f).color(color);
        buffer.vertex(mat, size, size, 0.0f).texture(1.0f, 1.0f).color(color);
        buffer.vertex(mat, size, -size, 0.0f).texture(1.0f, 0.0f).color(color);
        buffer.vertex(mat, -size, -size, 0.0f).texture(0.0f, 0.0f).color(color);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
        matrices.pop();
    }

    private void drawJelloMode(MatrixStack matrices, Camera camera, float tickDelta) {
        double tPosX = interpolate(lastTarget.getX(), lastTarget.lastRenderX, tickDelta) - camera.getPos().x;
        double tPosY = interpolate(lastTarget.getY(), lastTarget.lastRenderY, tickDelta) - camera.getPos().y;
        double tPosZ = interpolate(lastTarget.getZ(), lastTarget.lastRenderZ, tickDelta) - camera.getPos().z;

        float height = lastTarget.getHeight() + 0.3f;
        double duration = 2500.0 / jelloSpeed.getValue();
        double elapsed = (System.currentTimeMillis() % (long) duration);
        boolean side = elapsed > duration / 2.0;
        double progress = elapsed / (duration / 2.0);

        if (side) {
            --progress;
        } else {
            progress = 1 - progress;
        }

        progress = progress < 0.5 ? 2.0 * progress * progress : 1.0 - Math.pow(-2.0 * progress + 2.0, 2.0) / 2.0;
        double eased = (height / 1.5) * (progress > 0.5 ? 1.0 - progress : progress) * (side ? -1 : 1);

        matrices.push();
        matrices.translate(tPosX, tPosY, tPosZ);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.disableCull();

        if (mc.player.canSee(lastTarget)) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.disableDepthTest();
        }

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        int color = ColorProvider.getThemeColor();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (int i = 0; i <= 360; ++i) {
            double angle = Math.toRadians(i);
            float x = (float) (Math.cos(angle) * lastTarget.getWidth());
            float z = (float) (Math.sin(angle) * lastTarget.getWidth());
            int coreColor = ColorProvider.setAlpha(color, 1);
            buffer.vertex(matrix, x, (float) (height * progress + eased), z).color(coreColor);
            buffer.vertex(matrix, x, (float) (height * progress), z).color(color);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= 360; ++i) {
            double angle = Math.toRadians(i);
            float x = (float) (Math.cos(angle) * lastTarget.getWidth());
            float z = (float) (Math.sin(angle) * lastTarget.getWidth());
            buffer.vertex(matrix, x, (float) (height * progress), z).color(color);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        RenderSystem.enableCull();
        if (mc.player.canSee(lastTarget)) RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        matrices.pop();
    }

    private void drawGhosts(MatrixStack matrices, Camera camera, float tickDelta) {
        float tProgress = (float) animation.getValue();
        double x = interpolate(lastTarget.getX(), lastTarget.lastRenderX, tickDelta) - camera.getPos().getX();
        double y = interpolate(lastTarget.getY(), lastTarget.lastRenderY, tickDelta) - camera.getPos().getY() + (lastTarget.getHeight() / 2.0);
        double z = interpolate(lastTarget.getZ(), lastTarget.lastRenderZ, tickDelta) - camera.getPos().getZ();

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, GLOW_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        float hurt = getAuraHurtFactor(lastTarget);
        int c1 = mixWithHurt(ColorProvider.getThemeColor(), hurt);
        int c2 = mixWithHurt(ColorProvider.getThemeColorTwo(), hurt);
        int finalConstantColor = c1;

        matrices.push();
        matrices.translate(x, y, z);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float time = (System.currentTimeMillis() % 2500) / 2500f * (float) Math.PI * 2f * (float) ghostsSpeed.getValue();
        float radius = lastTarget.getWidth() * 1.65f;
        int trailSegments = 76;

        float electronBaseScale = 0.25f * tProgress;

        for (int i = 0; i < 3; i++) {
            float offset = i * ((float) Math.PI / 1.5f);
            float currentOrbitTime = time * 4.0f + offset;

            for (int j = 1; j <= trailSegments; j++) {
                float trailTime = currentOrbitTime - ((j / (float) trailSegments) * 4f);
                float fade = 1.0f - (j / (float) (trailSegments + 1));

                float tx = (float) (radius * Math.cos(trailTime) * Math.cos(offset) - radius * Math.sin(trailTime) * Math.sin(offset) * 0.5f);
                float ty = (float) (radius * Math.sin(trailTime) * 0.8f);
                float tz = (float) (radius * Math.cos(trailTime) * Math.sin(offset) + radius * Math.sin(trailTime) * Math.cos(offset) * 0.5f);

                int trailAlpha = (int) (tProgress * 180f * fade * fade);
                int currentTrailColor = finalConstantColor;
                if (twoColorsTheme.getValue()) {
                    currentTrailColor = getGradient(c1, c2, (float) (Math.sin(trailTime * 1.5f + i) * 0.5f + 0.5f));
                }
                drawQuad(buffer, matrices, camera, tx, ty, tz, electronBaseScale * (0.4f + 0.6f * fade), ColorProvider.setAlpha(currentTrailColor, trailAlpha));
            }

            float ex = (float) (radius * Math.cos(currentOrbitTime) * Math.cos(offset) - radius * Math.sin(currentOrbitTime) * Math.sin(offset) * 0.5f);
            float ey = (float) (radius * Math.sin(currentOrbitTime) * 0.8f);
            float ez = (float) (radius * Math.cos(currentOrbitTime) * Math.sin(offset) + radius * Math.sin(currentOrbitTime) * Math.cos(offset) * 0.5f);

            int currentHeadColor = finalConstantColor;
            if (twoColorsTheme.getValue()) {
                currentHeadColor = getGradient(c1, c2, (float) (Math.sin(currentOrbitTime * 1.5f + i) * 0.5f + 0.5f));
            }
            drawQuad(buffer, matrices, camera, ex, ey, ez, electronBaseScale, ColorProvider.setAlpha(currentHeadColor, (int) (tProgress * 255f)));
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
    }

    private void drawCrystals(MatrixStack matrices, Camera camera, float tickDelta) {
        float tProgress = (float) animation.getValue();
        double targetX = interpolate(lastTarget.getX(), lastTarget.lastRenderX, tickDelta);
        double targetY = interpolate(lastTarget.getY(), lastTarget.lastRenderY, tickDelta);
        double targetZ = interpolate(lastTarget.getZ(), lastTarget.lastRenderZ, tickDelta);
        Vec3d camPos = camera.getPos();

        float hurtFactor = getAuraHurtFactor(lastTarget);
        int themeColor = ColorProvider.getThemeColor();
        int baseColor = mixWithHurt(themeColor, hurtFactor);
        int secondColor = mixWithHurt(interpolateColor(themeColor, 0xFFFFFFFF, 0.5f), hurtFactor);

        float width = lastTarget.getWidth() * 1.6f;
        float timeOffset = (System.currentTimeMillis() % 4000) / 4000f * 360f;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.disableCull();
        if (mc.player.canSee(lastTarget)) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();

        int numLayers = 4;
        float crystalsPerLayer = (float) crystalCount.getValue();
        float speed = (float) crystalSpeed.getValue();

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        int crystalIdx = 0;
        for (int layer = 0; layer < numLayers; layer++) {
            float baseHeight = lastTarget.getHeight() * ((layer + 0.5f) / numLayers);
            for (int i = 0; i < 360; i += (360 / crystalsPerLayer)) {
                float val = 1.2f - 0.5f * tProgress;
                float angle = i + (layer * 25f) + timeOffset * speed;
                float sin = (float) (Math.sin(Math.toRadians(angle)) * (width * val));
                float cos = (float) (Math.cos(Math.toRadians(angle)) * (width * val));

                float crystalAppearProgress = Math.max(0, Math.min(1, (tProgress - (crystalIdx * 0.03f)) / (1.0f - (crystalIdx * 0.03f))));
                if (crystalAppearProgress >= 0.01f) {
                    float size = (float) (crystalSize.getValue() * crystalAppearProgress);
                    float heightOffset = baseHeight + ((1.0f - crystalAppearProgress) * -0.5f);

                    matrices.push();
                    matrices.translate(targetX - camPos.x + sin, targetY - camPos.y + heightOffset, targetZ - camPos.z + cos);
                    Vector3f directionToTarget = new Vector3f((float) -sin, (float) (lastTarget.getHeight() / 2.0 - heightOffset), (float) -cos).normalize();
                    matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0.0f, 1.0f, 0.0f), directionToTarget));

                    int currentCrystalColor = baseColor;
                    if (twoColorsTheme.getValue()) {
                        currentCrystalColor = getGradient(baseColor, secondColor, (float) (Math.sin(Math.toRadians(angle) + crystalIdx) * 0.5f + 0.5f));
                    }
                    drawSolidCrystalToBuffer(buffer, matrices, size, ColorProvider.setAlpha(currentCrystalColor, (int) (255 * tProgress * crystalAppearProgress)));
                    matrices.pop();
                }
                crystalIdx++;
            }
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, GLOW_TEXTURE);
        BufferBuilder glowBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        crystalIdx = 0;
        for (int layer = 0; layer < numLayers; layer++) {
            float baseHeight = lastTarget.getHeight() * ((layer + 0.5f) / numLayers);
            for (int i = 0; i < 360; i += (360 / crystalsPerLayer)) {
                float angle = i + (layer * 25f) + timeOffset * speed;
                float sin = (float) (Math.sin(Math.toRadians(angle)) * (width * (1.2f - 0.5f * tProgress)));
                float cos = (float) (Math.cos(Math.toRadians(angle)) * (width * (1.2f - 0.5f * tProgress)));

                float crystalAppearProgress = Math.max(0, Math.min(1, (tProgress - (crystalIdx * 0.03f)) / (1.0f - (crystalIdx * 0.03f))));
                if (crystalAppearProgress >= 0.01f) {
                    float heightOffset = baseHeight + ((1.0f - crystalAppearProgress) * -0.5f);

                    int currentGlowColor = baseColor;
                    if (twoColorsTheme.getValue()) {
                        currentGlowColor = getGradient(baseColor, secondColor, (float) (Math.sin(Math.toRadians(angle) + crystalIdx) * 0.5f + 0.5f));
                    }
                    float bloomAlpha = tProgress * crystalAppearProgress * (0.35f + 0.1f * (float) Math.sin(System.currentTimeMillis() / 300.0 + crystalIdx * 0.5));

                    matrices.push();
                    matrices.translate(targetX - camPos.x + sin, targetY - camPos.y + heightOffset, targetZ - camPos.z + cos);
                    matrices.multiply(camera.getRotation());
                    Matrix4f m = matrices.peek().getPositionMatrix();
                    float hs = (float) ((crystalSize.getValue() * 6.5f * crystalAppearProgress) / 2.0f);
                    int color = ColorProvider.setAlpha(currentGlowColor, (int) (255 * bloomAlpha));

                    glowBuffer.vertex(m, -hs, -hs, 0).texture(0, 0).color(color);
                    glowBuffer.vertex(m, -hs, hs, 0).texture(0, 1).color(color);
                    glowBuffer.vertex(m, hs, hs, 0).texture(1, 1).color(color);
                    glowBuffer.vertex(m, hs, -hs, 0).texture(1, 0).color(color);
                    matrices.pop();
                }
                crystalIdx++;
            }
        }
        BufferRenderer.drawWithGlobalProgram(glowBuffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private void drawCelestial(MatrixStack matrices, Camera camera, float tickDelta) {
        if (mc.player == null || mc.world == null || lastTarget == null) return;

        float frameTime = (mc.player.age + tickDelta) / 20.0f;
        float delta;
        if (celestialLastTime < 0.0f) {
            delta = 0.0f;
        } else {
            delta = MathHelper.clamp(frameTime - celestialLastTime, 0.0f, 0.1f);
        }
        celestialLastTime = frameTime;
        celestialFade = MathHelper.clamp(celestialFade + delta / 0.5f, 0.0f, 1.0f);

        float fade = celestialFade;
        if (fade <= 0.001f) return;

        double tPosX = interpolate(lastTarget.getX(), lastTarget.lastRenderX, tickDelta) - camera.getPos().x;
        double tPosY = interpolate(lastTarget.getY(), lastTarget.lastRenderY, tickDelta) - camera.getPos().y;
        double tPosZ = interpolate(lastTarget.getZ(), lastTarget.lastRenderZ, tickDelta) - camera.getPos().z;

        double time = frameTime;
        double spin = time * celestialSpeed.getValue() % CELESTIAL_TWO_PI;
        double arcStep = CELESTIAL_ARC / celestialSegments.getValue();
        double radius = lastTarget.getWidth() * 1.2;
        double verticalCenter = lastTarget.getHeight() / 2.0 + 0.2;
        int alpha = (int) (255.0 * fade * animation.getValue());

        int arms = (int) celestialArms.getValue();
        int segments = (int) celestialSegments.getValue();

        float hurtFactor = getAuraHurtFactor(lastTarget);
        int colorA = mixWithHurt(celestialColorA.getValue(), hurtFactor);
        int colorB = mixWithHurt(celestialColorB.getValue(), hurtFactor);

        Quaternionf cameraRotation = camera.getRotation();

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, GLOW_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        matrices.push();

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (int arm = 0; arm < arms; arm++) {
            double verticalTime = time + arm * 15.0;
            double armPhase = arm * (Math.PI / 2.0);

            for (int segment = 0; segment <= segments; segment++) {
                double arc = segment * arcStep;
                double angle = arc + spin + armPhase;
                double x = tPosX + radius * Math.cos(angle);
                double y = tPosY +
                        Math.sin(verticalTime + arc + arm) * CELESTIAL_VERTICAL_AMPLITUDE +
                        verticalCenter;
                double z = tPosZ + radius * Math.sin(angle);

                float progress = (float) segment / segments;
                float size = (float) (0.4 * (0.5 + progress) * celestialSize.getValue());

                double phase = time * 1.5 + segment * 0.035;
                float mix = (float) (0.5 + 0.5 * Math.sin(phase));
                int r = (int) (((colorA >> 16) & 0xFF) + (((colorB >> 16) & 0xFF) - ((colorA >> 16) & 0xFF)) * mix);
                int g = (int) (((colorA >> 8) & 0xFF) + (((colorB >> 8) & 0xFF) - ((colorA >> 8) & 0xFF)) * mix);
                int b = (int) ((colorA & 0xFF) + ((colorB & 0xFF) - (colorA & 0xFF)) * mix);
                int segmentAlpha = (int) (alpha * (0.5f + progress * 0.5f));
                int color = (MathHelper.clamp(segmentAlpha, 0, 255) << 24) |
                        (MathHelper.clamp(r, 0, 255) << 16) |
                        (MathHelper.clamp(g, 0, 255) << 8) |
                        MathHelper.clamp(b, 0, 255);

                matrices.push();
                matrices.translate(x, y, z);
                matrices.multiply(cameraRotation);
                Matrix4f matrix = matrices.peek().getPositionMatrix();

                float halfSize = size / 2.0f;
                buffer.vertex(matrix, -halfSize, -halfSize, 0).texture(0f, 1f).color(color);
                buffer.vertex(matrix, halfSize, -halfSize, 0).texture(1f, 1f).color(color);
                buffer.vertex(matrix, halfSize, halfSize, 0).texture(1f, 0f).color(color);
                buffer.vertex(matrix, -halfSize, halfSize, 0).texture(0f, 0f).color(color);

                matrices.pop();
            }
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
    }

    private void drawQuad(BufferBuilder buffer, MatrixStack ms, Camera camera, float x, float y, float z, float scale, int color) {
        ms.push();
        ms.translate(x, y, z);
        ms.scale(scale, scale, scale);
        ms.multiply(camera.getRotation());
        Matrix4f m = ms.peek().getPositionMatrix();
        buffer.vertex(m, -1f, 1f, 0.0f).texture(0.0f, 1.0f).color(color);
        buffer.vertex(m, 1f, 1f, 0.0f).texture(1.0f, 1.0f).color(color);
        buffer.vertex(m, 1f, -1f, 0.0f).texture(1.0f, 0.0f).color(color);
        buffer.vertex(m, -1f, -1f, 0.0f).texture(0.0f, 0.0f).color(color);
        ms.pop();
    }

    private void drawSolidCrystalToBuffer(BufferBuilder b, MatrixStack ms, float size, int color) {
        Matrix4f m = ms.peek().getPositionMatrix();
        float w = size / 2f, h = size;
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, bCol = color & 0xFF, a = (color >> 24) & 0xFF;
        int darkColor = (((int) (r * 0.6f)) << 16) | (((int) (g * 0.6f)) << 8) | ((int) (bCol * 0.6f)) | (a << 24);

        b.vertex(m, 0, h, 0).color(color); b.vertex(m, -w, 0, -w).color(color); b.vertex(m, w, 0, -w).color(color);
        b.vertex(m, 0, h, 0).color(darkColor); b.vertex(m, w, 0, -w).color(darkColor); b.vertex(m, w, 0, w).color(darkColor);
        b.vertex(m, 0, h, 0).color(color); b.vertex(m, w, 0, w).color(color); b.vertex(m, -w, 0, w).color(color);
        b.vertex(m, 0, h, 0).color(darkColor); b.vertex(m, -w, 0, w).color(darkColor); b.vertex(m, -w, 0, -w).color(darkColor);
        b.vertex(m, 0, -h, 0).color(darkColor); b.vertex(m, w, 0, -w).color(darkColor); b.vertex(m, -w, 0, -w).color(darkColor);
        b.vertex(m, 0, -h, 0).color(color); b.vertex(m, w, 0, w).color(color); b.vertex(m, w, 0, -w).color(color);
        b.vertex(m, 0, -h, 0).color(darkColor); b.vertex(m, -w, 0, w).color(darkColor); b.vertex(m, w, 0, w).color(darkColor);
        b.vertex(m, 0, -h, 0).color(color); b.vertex(m, -w, 0, -w).color(color); b.vertex(m, -w, 0, w).color(color);
    }

    @Override
    public void onDisable() {
        celestialFade = 0.0f;
        celestialLastTime = -1.0f;
        super.onDisable();
    }
}
