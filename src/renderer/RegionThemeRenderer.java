package renderer;

import java.awt.Color;

public final class RegionThemeRenderer {
    private RegionThemeRenderer() {
    }

    public static Color getBaseWallColor(RegionTheme theme) {
        switch (theme.paletteFamily) {
            case COOL:
                return applyVariant(new Color(70, 110, 145), theme.paletteVariant);
            case WARM:
                return applyVariant(new Color(145, 95, 70), theme.paletteVariant);
            case EARTH:
                return applyVariant(new Color(95, 125, 80), theme.paletteVariant);
            case ARCANE:
                return applyVariant(new Color(95, 80, 135), theme.paletteVariant);
            default:
                return new Color(110, 110, 110);
        }
    }

    public static Color getFloorColor(RegionTheme theme) {
        return scale(getBaseWallColor(theme), 0.55);
    }

    public static Color getCeilingColor(RegionTheme theme) {
        return scale(getBaseWallColor(theme), 0.42);
    }

    public static Color sampleWallColor(RegionTheme theme, int texX, int texY) {
        Color base = getBaseWallColor(theme);
        double factor;

        switch (theme.wallPattern) {
            case SMOOTH:
                factor = smoothPattern(texX, texY);
                break;
            case BRICK:
                factor = brickPattern(texX, texY);
                break;
            case ROUGH:
                factor = roughPattern(texX, texY);
                break;
            case LINES:
                factor = linesPattern(texX, texY);
                break;
            default:
                factor = 1.0;
                break;
        }

        return scale(base, factor);
    }

    private static Color applyVariant(Color c, int variant) {
        switch (variant) {
            case 1:
                return c; // balanced
            case 2:
                return scale(c, 0.78); // darker
            case 3:
                return saturate(scale(c, 1.05), 1.18); // richer
            case 4:
                return mixToward(c, new Color(185, 185, 185), 0.24); // lighter/faded
            default:
                return c;
        }
    }

    private static double smoothPattern(int x, int y) {
        int v = ((x * 17 + y * 11) & 7);
        return 0.92 + (v / 7.0) * 0.10;
    }

    private static double brickPattern(int x, int y) {
        int brickH = 8;
        int brickW = 16;
        int row = y / brickH;
        int offset = (row % 2) * (brickW / 2);

        boolean mortarY = (y % brickH) == 0;
        boolean mortarX = ((x + offset) % brickW) == 0;

        if (mortarX || mortarY) {
            return 0.70;
        }

        return 0.92 + (((x * 13 + y * 7) & 3) * 0.03);
    }

    private static double roughPattern(int x, int y) {
        int n = pseudoNoise(x, y);
        return 0.75 + (n / 255.0) * 0.35;
    }

    private static double linesPattern(int x, int y) {
        boolean line = (x % 6) == 0 || (x % 6) == 1;
        double base = line ? 0.72 : 0.98;
        int wobble = ((y * 19 + x * 3) & 7);
        return base + wobble * 0.01;
    }

    private static int pseudoNoise(int x, int y) {
        int n = x * 374761393 + y * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        n ^= (n >>> 16);
        return n & 255;
    }

    private static Color scale(Color c, double factor) {
        return new Color(
            clamp((int) Math.round(c.getRed() * factor)),
            clamp((int) Math.round(c.getGreen() * factor)),
            clamp((int) Math.round(c.getBlue() * factor))
        );
    }

    private static Color mixToward(Color a, Color b, double t) {
        double u = 1.0 - t;
        return new Color(
            clamp((int) Math.round(a.getRed() * u + b.getRed() * t)),
            clamp((int) Math.round(a.getGreen() * u + b.getGreen() * t)),
            clamp((int) Math.round(a.getBlue() * u + b.getBlue() * t))
        );
    }

    private static Color saturate(Color c, double amount) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hsb[1] = (float) Math.max(0.0, Math.min(1.0, hsb[1] * amount));
        return Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}