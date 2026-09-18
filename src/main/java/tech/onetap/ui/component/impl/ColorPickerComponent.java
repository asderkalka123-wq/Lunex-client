package tech.onetap.ui.component.impl;

import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;
import tech.onetap.module.settings.ColorSetting;
import tech.onetap.ui.component.Component;
import tech.onetap.util.cursor.CursorManager;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.math.Scissor;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.awt.Color;

public class ColorPickerComponent extends Component {
    private final ColorSetting setting;
    private boolean opened;
    private final Animation openAnim = new Animation(Easing.QUINTIC_OUT, 300);

    private boolean draggingSV, draggingHue;
    private float hue, saturation, value;

    private String hexText = "";
    private boolean hexFocused = false;

    public ColorPickerComponent(ColorSetting setting) {
        this.setting = setting;
        updateHSV();
    }

    private void updateHSV() {
        Color c = new Color(setting.getValue(), true);
        float[] hsv = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
    }

    private void updateHexFromColor() {
        int rgb = setting.getValue() & 0x00FFFFFF;
        hexText = String.format("%06X", rgb);
    }

    private void applyHexToColor() {
        try {
            if (hexText.length() == 6) {
                int rgb = Integer.parseInt(hexText, 16);
                setting.setValue(rgb | 0xFF000000);
                updateHSV();
            }
        } catch (NumberFormatException ignored) {}
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        float alpha = Math.min(getAlphaAnimSetting().getValue(), 1);
        int alphaInt = (int) (255 * alpha);

        openAnim.run(opened);
        float expandedHeight = 70f;

        DrawUtil.drawText(Fonts.SFREGULAR.get(), setting.getName(), x + 4.5f, y + 3, ColorProvider.rgba(255, 255, 255, alphaInt), 6.5f);

        float previewSize = 8f;
        float previewX = x + width - previewSize - 5;
        float previewY = y + 2.5f;

        if (HoverUtil.isHovered(mouseX, mouseY, previewX, previewY, previewSize, previewSize)) CursorManager.requestHand();

        DrawUtil.drawRound(previewX - 0.5f, previewY - 0.5f, previewSize + 1, previewSize + 1, 1.5f, ColorProvider.rgba(60, 60, 65, (int)(150 * alpha)));
        DrawUtil.drawRound(previewX, previewY, previewSize, previewSize, 1.5f, setting.getValue());

        if (openAnim.getValue() > 0.01f) {
            float pickerY = y + 13;
            float animH = openAnim.getValue() * expandedHeight;
            float animAlpha = (float) (alpha * openAnim.getValue());
            int animAlphaInt = (int)(255 * animAlpha);

            Scissor.push();
            Scissor.setFromComponentCoordinates(x, pickerY, width, animH);

            DrawUtil.drawRound(x + 4.5f, pickerY - 0.5f, width - 9, expandedHeight + 1, 2.5f, ColorProvider.rgba(60, 60, 65, (int)(150 * animAlpha)));
            DrawUtil.drawRound(x + 5, pickerY, width - 10, expandedHeight, 2.5f, ColorProvider.rgba(20, 20, 25, animAlphaInt));

            float svX = x + 9;
            float svY = pickerY + 4;
            float svSize = 40;

            int cHue        = ColorProvider.setAlpha(Color.HSBtoRGB(hue, 1F, 1F), animAlphaInt);
            int cWhite      = ColorProvider.rgba(255, 255, 255, animAlphaInt);
            int cClearWhite = ColorProvider.rgba(255, 255, 255, 0);
            int cBlack      = ColorProvider.rgba(0, 0, 0, animAlphaInt);
            int cClearBlack = ColorProvider.rgba(0, 0, 0, 0);

            DrawUtil.drawRound(svX, svY, svSize, svSize, 2f, cHue);
            DrawUtil.drawRound(svX, svY, svSize, svSize, 2f, cWhite, cWhite, cClearWhite, cClearWhite);
            DrawUtil.drawRound(svX, svY, svSize, svSize, 2f, cClearBlack, cBlack, cBlack, cClearBlack);

            float svCursorX = svX + saturation * svSize;
            float svCursorY = svY + (1 - value) * svSize;
            DrawUtil.drawRound(svCursorX - 2.5f, svCursorY - 2.5f, 5, 5, 2.5f, ColorProvider.rgba(0, 0, 0, (int)(180 * animAlpha)));
            DrawUtil.drawRound(svCursorX - 1.5f, svCursorY - 1.5f, 3, 3, 1.5f, ColorProvider.rgba(255, 255, 255, animAlphaInt));

            float hueX = svX + svSize + 6;
            float hueY = svY;
            float hueW = 6;

            for (float i = 0; i <= svSize; i += 0.5f) {
                int color = ColorProvider.setAlpha(Color.HSBtoRGB(i / svSize, 1F, 1F), animAlphaInt);
                DrawUtil.drawRound(hueX, hueY + i, hueW, 1f, 0f, color);
            }

            float hueCursorY = hueY + hue * svSize;
            DrawUtil.drawRound(hueX - 1.5f, hueCursorY - 2.5f, hueW + 3f, 5f, 2f, ColorProvider.rgba(0, 0, 0, (int)(180 * animAlpha)));
            DrawUtil.drawRound(hueX - 0.5f, hueCursorY - 1.5f, hueW + 1f, 3f, 1f, ColorProvider.rgba(255, 255, 255, animAlphaInt));

            // Hex input field
            float hexX = svX;
            float hexY = svY + svSize + 6;
            float hexW = svSize + hueW + 6;
            float hexH = 12;

            DrawUtil.drawRound(hexX - 0.5f, hexY - 0.5f, hexW + 1, hexH + 1, 3f, ColorProvider.rgba(80, 80, 85, hexFocused ? 180 : 80));
            DrawUtil.drawRound(hexX, hexY, hexW, hexH, 2.5f, ColorProvider.rgba(30, 30, 35, animAlphaInt));

            if (HoverUtil.isHovered(mouseX, mouseY, hexX, hexY, hexW, hexH)) CursorManager.requestIBeam();

            String displayHex = hexText.isEmpty() && !hexFocused ? "#FFFFFF" : "#" + hexText;
            String cursor = hexFocused && System.currentTimeMillis() % 1000 > 500 ? "_" : "";
            DrawUtil.drawText(Fonts.SFREGULAR.get(), displayHex + cursor, hexX + 4, hexY + 3,
                    hexText.isEmpty() && !hexFocused ? ColorProvider.rgba(150, 150, 150, animAlphaInt) : ColorProvider.rgba(255, 255, 255, animAlphaInt), 6f);

            // Color preview next to hex
            DrawUtil.drawRound(hexX + hexW + 4, hexY, hexH, hexH, 2.5f, setting.getValue());

            if (draggingSV) {
                saturation = Math.max(0, Math.min(1, (mouseX - svX) / svSize));
                value = 1F - Math.max(0, Math.min(1, (mouseY - svY) / svSize));
                syncColor();
            } else if (draggingHue) {
                hue = Math.max(0, Math.min(1, (mouseY - hueY) / svSize));
                syncColor();
            }

            Scissor.unset();
            Scissor.pop();
        }

        setHeight(13 + (openAnim.getValue() * expandedHeight));
    }

    private void syncColor() {
        int rgb = Color.HSBtoRGB(hue, saturation, value);
        setting.setValue(rgb | 0xFF000000);
        updateHexFromColor();
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        float previewSize = 8f;
        float previewX = x + width - previewSize - 5;
        float previewY = y + 2.5f;

        if (HoverUtil.isHovered(mouseX, mouseY, previewX, previewY, previewSize, previewSize) && button == 1) {
            opened = !opened;
            if (opened) updateHexFromColor();
            return;
        }

        if (opened && button == 0) {
            float svX = x + 9;
            float svY = y + 13 + 4;
            float svSize = 40;
            float hueX = svX + svSize + 6;

            float hexX = svX;
            float hexY = svY + svSize + 6;
            float hexW = svSize + 6 + 6;
            float hexH = 12;

            if (HoverUtil.isHovered(mouseX, mouseY, hexX, hexY, hexW, hexH)) {
                hexFocused = true;
                updateHexFromColor();
            } else {
                hexFocused = false;
            }

            if (HoverUtil.isHovered(mouseX, mouseY, svX, svY, svSize, svSize)) draggingSV = true;
            else if (HoverUtil.isHovered(mouseX, mouseY, hueX - 2, svY, 10, svSize)) draggingHue = true;
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        draggingSV = false;
        draggingHue = false;
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!hexFocused) return;
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !hexText.isEmpty()) {
            hexText = hexText.substring(0, hexText.length() - 1);
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            applyHexToColor();
            hexFocused = false;
        }
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        if (!hexFocused) return;
        if (hexText.length() < 6 && "0123456789ABCDEFabcdef".indexOf(chr) >= 0) {
            hexText += Character.toUpperCase(chr);
        }
    }

    @Override
    public boolean isVisible() {
        return setting.visible.get();
    }
}