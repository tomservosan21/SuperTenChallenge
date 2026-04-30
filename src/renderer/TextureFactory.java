package renderer;

import java.awt.image.BufferedImage;
import java.util.Random;

public class TextureFactory {

    public static BufferedImage makeBrickTexture(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(12345);

        int mortar = rgb(70, 70, 70);

        // background mortar
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, mortar);
            }
        }

        int brickH = 16;
        int brickW = 32;
        int mortarThick = 2;

        for (int row = 0; row < height; row += brickH) {
            boolean offset = ((row / brickH) % 2 == 1);
            int xStart = offset ? -(brickW / 2) : 0;

            for (int col = xStart; col < width; col += brickW) {
                int baseR = 130 + rand.nextInt(40);
                int baseG = 45 + rand.nextInt(25);
                int baseB = 30 + rand.nextInt(20);

                for (int y = row + mortarThick; y < row + brickH - mortarThick && y < height; y++) {
                    for (int x = col + mortarThick; x < col + brickW - mortarThick; x++) {
                        if (x < 0 || x >= width) continue;

                        int noise = rand.nextInt(21) - 10;
                        int r = clamp(baseR + noise);
                        int g = clamp(baseG + noise);
                        int b = clamp(baseB + noise);

                        img.setRGB(x, y, rgb(r, g, b));
                    }
                }
            }
        }

        return img;
    }
    
    public static BufferedImage makeRegionCheckerFloor(
            int width, int height, int cellSize,
            int darkR, int darkG, int darkB,
            int lightR, int lightG, int lightB,
            int seed
    ) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cx = x / cellSize;
                int cy = y / cellSize;
                boolean lightCell = ((cx + cy) & 1) == 0;

                int baseR = lightCell ? lightR : darkR;
                int baseG = lightCell ? lightG : darkG;
                int baseB = lightCell ? lightB : darkB;

                double macro = fbm(x, y, seed, 4, 0.040, 1.0, 0.55);
                double micro = fbm(x, y, seed + 29, 2, 0.20, 1.0, 0.52);
                double mottling = fbm(x, y, seed + 51, 2, 0.10, 1.0, 0.50);

                int lx = x % cellSize;
                int ly = y % cellSize;

                // Strong checker identity, but not razor-sharp borders.
                double edgeShade = 0.0;
                if (lx == 0 || ly == 0) edgeShade -= 1.0;
                if (lx == cellSize - 1 || ly == cellSize - 1) edgeShade += 1.0;

                int r = clamp((int)Math.round(baseR + macro * 10.0 + micro * 5.0 + mottling * 3.0 + edgeShade));
                int g = clamp((int)Math.round(baseG + macro * 10.0 + micro * 5.0 + mottling * 3.0 + edgeShade));
                int b = clamp((int)Math.round(baseB + macro * 10.0 + micro * 5.0 + mottling * 3.0 + edgeShade));

                img.setRGB(x, y, rgb(r, g, b));
            }
        }

        return img;
    }
    
    public static BufferedImage makeDieFourKeyTexture(
            int width, int height,
            int panelDarkR, int panelDarkG, int panelDarkB,
            int panelLightR, int panelLightG, int panelLightB,
            int pipDarkR, int pipDarkG, int pipDarkB,
            int pipLightR, int pipLightG, int pipLightB
    ) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int cx = width / 2;
        int cy = height / 2;

        // ------------------------------------------------------------
        // 1) Very dark base (near black plate)
        // ------------------------------------------------------------
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                // subtle vignette so edges are darker
                double dx = (x - cx) / (double) width;
                double dy = (y - cy) / (double) height;
                double dist = Math.sqrt(dx * dx + dy * dy);

                double v = 0.08 + 0.12 * (1.0 - dist * 1.8);
                if (v < 0.0) v = 0.0;

                int r = clamp((int)(panelDarkR * v));
                int g = clamp((int)(panelDarkG * v));
                int b = clamp((int)(panelDarkB * v));

                // hard dark frame
                if (x < 3 || y < 3 || x >= width - 3 || y >= height - 3) {
                    r = 0;
                    g = 0;
                    b = 0;
                }

                img.setRGB(x, y, rgb(r, g, b));
            }
        }

        // ------------------------------------------------------------
        // 2) Four bright neon pips
        // ------------------------------------------------------------
        int offset = width / 5;
        int pipRadius = Math.max(6, width / 10);

        int[][] centers = new int[][] {
            {cx - offset, cy - offset},
            {cx + offset, cy - offset},
            {cx - offset, cy + offset},
            {cx + offset, cy + offset}
        };

        for (int[] p : centers) {
            int px = p[0];
            int py = p[1];

            for (int y = py - pipRadius - 8; y <= py + pipRadius + 8; y++) {
                for (int x = px - pipRadius - 8; x <= px + pipRadius + 8; x++) {
                    if (x < 0 || x >= width || y < 0 || y >= height) continue;

                    int dx = x - px;
                    int dy = y - py;
                    double dist = Math.sqrt(dx * dx + dy * dy);

                    int rgb0 = img.getRGB(x, y);
                    int r = (rgb0 >> 16) & 0xFF;
                    int g = (rgb0 >> 8) & 0xFF;
                    int b = rgb0 & 0xFF;

                    // outer glow (large + soft)
                    if (dist <= pipRadius + 7) {
                        double t = 1.0 - (dist / (pipRadius + 7));
                        double glow = 0.6 * t;

                        r = clamp((int)(r + pipLightR * glow));
                        g = clamp((int)(g + pipLightG * glow));
                        b = clamp((int)(b + pipLightB * glow));
                    }

                    // main body (very saturated)
                    if (dist <= pipRadius) {
                        double t = 1.0 - (dist / pipRadius);

                        double blend = 0.65 + 0.35 * t;

                        r = clamp((int)(pipDarkR * (1.0 - blend) + pipLightR * blend));
                        g = clamp((int)(pipDarkG * (1.0 - blend) + pipLightG * blend));
                        b = clamp((int)(pipDarkB * (1.0 - blend) + pipLightB * blend));

                        // strong brightness boost
                        r = clamp((int)(r * 1.4));
                        g = clamp((int)(g * 1.4));
                        b = clamp((int)(b * 1.35));
                    }

                    // ultra bright core (almost glowing white-tinted color)
                    if (dist <= pipRadius * 0.35) {
                        r = clamp((int)(pipLightR * 1.4));
                        g = clamp((int)(pipLightG * 1.4));
                        b = clamp((int)(pipLightB * 1.4));
                    }

                    img.setRGB(x, y, rgb(r, g, b));
                }
            }
        }

        return img;
    }
    
    public static BufferedImage makeDieFiveKeyTexture(
            int width, int height,
            int panelDarkR, int panelDarkG, int panelDarkB,
            int panelLightR, int panelLightG, int panelLightB,
            int pipDarkR, int pipDarkG, int pipDarkB,
            int pipLightR, int pipLightG, int pipLightB
    ) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int cx = width / 2;
        int cy = height / 2;

        // ------------------------------------------------------------
        // 1) Dark base (near black)
        // ------------------------------------------------------------
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                double dx = (x - cx) / (double) width;
                double dy = (y - cy) / (double) height;
                double dist = Math.sqrt(dx * dx + dy * dy);

                double v = 0.08 + 0.12 * (1.0 - dist * 1.8);
                if (v < 0.0) v = 0.0;

                int r = clamp((int)(panelDarkR * v));
                int g = clamp((int)(panelDarkG * v));
                int b = clamp((int)(panelDarkB * v));

                if (x < 3 || y < 3 || x >= width - 3 || y >= height - 3) {
                    r = 0; g = 0; b = 0;
                }

                img.setRGB(x, y, rgb(r, g, b));
            }
        }

        // ------------------------------------------------------------
        // 2) Five pip positions (die face 5)
        // ------------------------------------------------------------
        int offset = width / 5;
        int pipRadius = Math.max(6, width / 10);

        int[][] centers = new int[][] {
            {cx - offset, cy - offset}, // top-left
            {cx + offset, cy - offset}, // top-right
            {cx - offset, cy + offset}, // bottom-left
            {cx + offset, cy + offset}, // bottom-right
            {cx, cy}                    // center
        };

        // ------------------------------------------------------------
        // 3) Draw glowing pips
        // ------------------------------------------------------------
        for (int[] p : centers) {
            int px = p[0];
            int py = p[1];

            for (int y = py - pipRadius - 8; y <= py + pipRadius + 8; y++) {
                for (int x = px - pipRadius - 8; x <= px + pipRadius + 8; x++) {
                    if (x < 0 || x >= width || y < 0 || y >= height) continue;

                    int dx = x - px;
                    int dy = y - py;
                    double dist = Math.sqrt(dx * dx + dy * dy);

                    int rgb0 = img.getRGB(x, y);
                    int r = (rgb0 >> 16) & 0xFF;
                    int g = (rgb0 >> 8) & 0xFF;
                    int b = rgb0 & 0xFF;

                    // glow halo
                    if (dist <= pipRadius + 7) {
                        double t = 1.0 - (dist / (pipRadius + 7));
                        double glow = 0.6 * t;

                        r = clamp((int)(r + pipLightR * glow));
                        g = clamp((int)(g + pipLightG * glow));
                        b = clamp((int)(b + pipLightB * glow));
                    }

                    // main body
                    if (dist <= pipRadius) {
                        double t = 1.0 - (dist / pipRadius);
                        double blend = 0.65 + 0.35 * t;

                        r = clamp((int)(pipDarkR * (1.0 - blend) + pipLightR * blend));
                        g = clamp((int)(pipDarkG * (1.0 - blend) + pipLightG * blend));
                        b = clamp((int)(pipDarkB * (1.0 - blend) + pipLightB * blend));

                        r = clamp((int)(r * 1.4));
                        g = clamp((int)(g * 1.4));
                        b = clamp((int)(b * 1.35));
                    }

                    // bright core
                    if (dist <= pipRadius * 0.35) {
                        r = clamp((int)(pipLightR * 1.4));
                        g = clamp((int)(pipLightG * 1.4));
                        b = clamp((int)(pipLightB * 1.4));
                    }

                    img.setRGB(x, y, rgb(r, g, b));
                }
            }
        }

        return img;
    }
    
    public static BufferedImage makeProductionStoneWall(
            int width, int height,
            int baseR, int baseG, int baseB,
            int accentR, int accentG, int accentB,
            int seed
    ) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double macro = fbm(x, y, seed, 4, 0.026, 1.0, 0.56);
                double mid = fbm(x, y, seed + 17, 3, 0.082, 1.0, 0.55);
                double fine = fbm(x, y, seed + 51, 2, 0.200, 1.0, 0.50);

                double blend = 0.42 + 0.26 * (macro + 1.0) * 0.5;
                double crack = ridgeNoise(x, y, seed + 71, 0.075);

                int r = (int)Math.round(baseR * (1.0 - blend) + accentR * blend + macro * 12.0 + mid * 8.0 + fine * 4.0);
                int g = (int)Math.round(baseG * (1.0 - blend) + accentG * blend + macro * 12.0 + mid * 8.0 + fine * 4.0);
                int b = (int)Math.round(baseB * (1.0 - blend) + accentB * blend + macro * 12.0 + mid * 8.0 + fine * 4.0);

                // Subtle stone joints, not graphic brick outlines.
                int jointX = x % 16;
                int jointY = y % 16;
                if (jointX == 0 || jointY == 0) {
                    r -= 2;
                    g -= 2;
                    b -= 2;
                }

                if (crack > 0.80) {
                    r -= 14;
                    g -= 14;
                    b -= 14;
                } else if (crack > 0.72) {
                    r -= 7;
                    g -= 7;
                    b -= 7;
                }

                img.setRGB(x, y, rgb(clamp(r), clamp(g), clamp(b)));
            }
        }

        return img;
    }

    public static BufferedImage makeProductionConcreteWall(
            int width, int height,
            int baseR, int baseG, int baseB,
            int accentR, int accentG, int accentB,
            int seed
    ) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double macro = fbm(x, y, seed, 4, 0.024, 1.0, 0.56);
                double dust = fbm(x, y, seed + 19, 3, 0.10, 1.0, 0.52);
                double pitting = fbm(x, y, seed + 37, 2, 0.24, 1.0, 0.50);

                double blend = 0.34 + 0.24 * (macro + 1.0) * 0.5;

                int r = (int)Math.round(baseR * (1.0 - blend) + accentR * blend + macro * 10.0 + dust * 7.0 + pitting * 3.0);
                int g = (int)Math.round(baseG * (1.0 - blend) + accentG * blend + macro * 10.0 + dust * 7.0 + pitting * 3.0);
                int b = (int)Math.round(baseB * (1.0 - blend) + accentB * blend + macro * 10.0 + dust * 7.0 + pitting * 3.0);

                double stain = ridgeNoise(x, y, seed + 101, 0.05);
                if (stain > 0.82) {
                    r -= 8;
                    g -= 8;
                    b -= 8;
                }

                img.setRGB(x, y, rgb(clamp(r), clamp(g), clamp(b)));
            }
        }

        return img;
    }

    public static BufferedImage makeProductionMetalWall(
            int width, int height,
            int baseR, int baseG, int baseB,
            int accentR, int accentG, int accentB,
            int seed
    ) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double macro = fbm(x, y, seed + 13, 3, 0.030, 1.0, 0.56);
                double brushed = fbm(x, y, seed, 3, 0.12, 1.0, 0.45);
                double speck = fbm(x, y, seed + 27, 2, 0.28, 1.0, 0.50);

                double blend = 0.42 + 0.18 * (macro + 1.0) * 0.5;

                int r = (int)Math.round(baseR * (1.0 - blend) + accentR * blend + macro * 10.0 + brushed * 5.0 + speck * 3.0);
                int g = (int)Math.round(baseG * (1.0 - blend) + accentG * blend + macro * 10.0 + brushed * 5.0 + speck * 3.0);
                int b = (int)Math.round(baseB * (1.0 - blend) + accentB * blend + macro * 10.0 + brushed * 5.0 + speck * 3.0);

                if (x < 2 || y < 2 || x >= width - 2 || y >= height - 2) {
                    r -= 12;
                    g -= 12;
                    b -= 12;
                }

                if (x == 6 || y == 6 || x == width - 7 || y == height - 7) {
                    r += 6;
                    g += 6;
                    b += 6;
                }

                img.setRGB(x, y, rgb(clamp(r), clamp(g), clamp(b)));
            }
        }

        addRivet(img, 9, 9, accentR, accentG, accentB);
        addRivet(img, width - 10, 9, accentR, accentG, accentB);
        addRivet(img, 9, height - 10, accentR, accentG, accentB);
        addRivet(img, width - 10, height - 10, accentR, accentG, accentB);

        return img;
    }

    public static BufferedImage makeProductionLandmarkWall(
            int width, int height,
            int baseR, int baseG, int baseB,
            int accentR, int accentG, int accentB,
            int seed
    ) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int organicMode = Math.floorMod(seed, 3);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                double macro = fbm(x, y, seed, 4, 0.030, 1.0, 0.55);
                double ridge = ridgeNoise(x, y, seed + 101, 0.040);

                double blend;
                double shade;

                switch (organicMode) {
                    case 0:
                        // EARTH / moss: painterly blotches
                        blend = 0.48 + 0.34 * macro + 0.10 * ridge;
                        shade = macro * 4.0;
                        break;

                    case 1:
                        // STONE: broad mineral veining
                        blend = 0.46 + 0.26 * macro + 0.22 * ridge;
                        shade = macro * 3.5;

                        if (ridge > 0.76) {
                            shade += 5.0;
                        }
                        break;

                    default:
                        // WOOD / roots: flowing brush-like grain
                        double flow = Math.sin(
                                x * 0.105 +
                                y * 0.032 +
                                fbm(x, y, seed + 211, 3, 0.045, 1.0, 0.55) * 3.0
                        );

                        blend = 0.50 + 0.34 * flow;
                        shade = flow * 4.0;
                        break;
                }

                if (blend < 0.0) blend = 0.0;
                if (blend > 1.0) blend = 1.0;

             // Softer two-color blending.
             // Keeps two dominant colors, but removes harsh blocky separation.
             double t = blend;

             // Smooth transition between base and accent.
             t = t * t * (3.0 - 2.0 * t);

             // Keep it from becoming fully muddy or fully neon.
             blend = 0.18 + t * 0.64;

             if (blend > 0.78) {
                 shade += 2.0;
             }

                int r = (int)Math.round(baseR * (1.0 - blend) + accentR * blend + shade);
                int g = (int)Math.round(baseG * (1.0 - blend) + accentG * blend + shade);
                int b = (int)Math.round(baseB * (1.0 - blend) + accentB * blend + shade);

                // Very soft vignette so walls feel hand-painted, not flat.
                double dx = (x - width * 0.5) / width;
                double dy = (y - height * 0.5) / height;
                double vignette = 1.0 - Math.min(0.16, (dx * dx + dy * dy) * 0.55);

                r = (int)Math.round(r * vignette);
                g = (int)Math.round(g * vignette);
                b = (int)Math.round(b * vignette);

                img.setRGB(x, y, rgb(clamp(r), clamp(g), clamp(b)));
            }
        }

        return img;
    }
    
    public static BufferedImage makeProductionCeiling(
            int width, int height,
            int baseGray,
            int seed
    ) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double macro = fbm(x, y, seed, 3, 0.028, 1.0, 0.55);
                double fine = fbm(x, y, seed + 23, 2, 0.18, 1.0, 0.50);

                int g = clamp((int)Math.round(baseGray + macro * 8.0 + fine * 3.0));

                if (x % 20 == 0 || y % 20 == 0) {
                    g = clamp(g - 4);
                }

                img.setRGB(x, y, rgb(g, g, g));
            }
        }

        return img;
    }

    private static void addRivet(BufferedImage img, int cx, int cy, int r0, int g0, int b0) {
        for (int y = cy - 2; y <= cy + 2; y++) {
            for (int x = cx - 2; x <= cx + 2; x++) {
                if (x < 0 || x >= img.getWidth() || y < 0 || y >= img.getHeight()) continue;
                int dx = x - cx;
                int dy = y - cy;
                int d2 = dx * dx + dy * dy;

                if (d2 <= 4) {
                    int rr = clamp(r0 + 20 - d2 * 4);
                    int gg = clamp(g0 + 20 - d2 * 4);
                    int bb = clamp(b0 + 20 - d2 * 4);
                    img.setRGB(x, y, rgb(rr, gg, bb));
                }
            }
        }
    }

    private static double ridgeNoise(int x, int y, int seed, double frequency) {
        double n = fbm(x, y, seed, 3, frequency, 1.0, 0.5);
        return 1.0 - Math.abs(n);
    }

    private static double fbm(int x, int y, int seed, int octaves, double frequency, double amplitude, double gain) {
        double sum = 0.0;
        double amp = amplitude;
        double freq = frequency;
        double norm = 0.0;

        for (int i = 0; i < octaves; i++) {
            sum += valueNoise(x * freq, y * freq, seed + i * 97) * amp;
            norm += amp;
            amp *= gain;
            freq *= 2.0;
        }

        if (norm <= 1e-9) {
            return 0.0;
        }

        return sum / norm;
    }

    private static double valueNoise(double x, double y, int seed) {
        int x0 = (int)Math.floor(x);
        int y0 = (int)Math.floor(y);
        int x1 = x0 + 1;
        int y1 = y0 + 1;

        double sx = smooth(x - x0);
        double sy = smooth(y - y0);

        double n00 = hashNoise(x0, y0, seed);
        double n10 = hashNoise(x1, y0, seed);
        double n01 = hashNoise(x0, y1, seed);
        double n11 = hashNoise(x1, y1, seed);

        double ix0 = lerp(n00, n10, sx);
        double ix1 = lerp(n01, n11, sx);

        return lerp(ix0, ix1, sy);
    }

    private static double hashNoise(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= (h >>> 16);

        int masked = h & 0x7fffffff;
        return (masked / 1073741824.0) - 1.0;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static BufferedImage makeStoneTexture(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(54321);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = 90 + rand.nextInt(80);
                img.setRGB(x, y, rgb(gray, gray, gray));
            }
        }

        // add darker cracks / seams
        for (int y = 0; y < height; y += 16) {
            for (int x = 0; x < width; x++) {
                for (int t = 0; t < 2 && y + t < height; t++) {
                    int gray = 50 + rand.nextInt(20);
                    img.setRGB(x, y + t, rgb(gray, gray, gray));
                }
            }
        }

        for (int x = 0; x < width; x += 16) {
            for (int y = 0; y < height; y++) {
                for (int t = 0; t < 2 && x + t < width; t++) {
                    int gray = 50 + rand.nextInt(20);
                    img.setRGB(x + t, y, rgb(gray, gray, gray));
                }
            }
        }

        return img;
    }

    public static BufferedImage makeCheckerTexture(int width, int height, int cellSize,
                                                   int r1, int g1, int b1,
                                                   int r2, int g2, int b2) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int c1 = rgb(r1, g1, b1);
        int c2 = rgb(r2, g2, b2);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cx = x / cellSize;
                int cy = y / cellSize;
                img.setRGB(x, y, ((cx + cy) & 1) == 0 ? c1 : c2);
            }
        }

        return img;
    }

    public static BufferedImage makeBrickTextureAlt(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(24680);

        int mortar = rgb(85, 85, 85);

        // fill mortar background
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, mortar);
            }
        }

        int brickH = 16;
        int brickW = 32;
        int mortarThick = 2;

        for (int row = 0; row < height; row += brickH) {
            boolean offset = ((row / brickH) % 2 == 1);
            int xStart = offset ? -(brickW / 2) : 0;

            for (int col = xStart; col < width; col += brickW) {
                int baseR = 110 + rand.nextInt(25);
                int baseG = 70 + rand.nextInt(20);
                int baseB = 35 + rand.nextInt(20);

                for (int y = row + mortarThick; y < row + brickH - mortarThick && y < height; y++) {
                    for (int x = col + mortarThick; x < col + brickW - mortarThick; x++) {
                        if (x < 0 || x >= width) continue;

                        int noise = rand.nextInt(21) - 10;

                        int r = clamp(baseR + noise);
                        int g = clamp(baseG + noise);
                        int b = clamp(baseB + noise);

                        img.setRGB(x, y, rgb(r, g, b));
                    }
                }
            }
        }

        return img;
    }

    public static BufferedImage makeStoneTextureDark(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(13579);

        // brighter stone base
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = 95 + rand.nextInt(50);
                img.setRGB(x, y, rgb(gray, gray, gray));
            }
        }

        // horizontal seams
        for (int y = 0; y < height; y += 16) {
            for (int x = 0; x < width; x++) {
                int g = 70 + rand.nextInt(15);
                img.setRGB(x, y, rgb(g, g, g));
            }
        }

        // vertical seams
        for (int x = 0; x < width; x += 16) {
            for (int y = 0; y < height; y++) {
                int g = 70 + rand.nextInt(15);
                img.setRGB(x, y, rgb(g, g, g));
            }
        }

        return img;
    }

    public static BufferedImage makeMetalPanelTexture(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(999);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = 110 + rand.nextInt(30);
                img.setRGB(x, y, rgb(gray, gray, gray + Math.min(15, rand.nextInt(10))));
            }
        }

        // panel borders
        for (int x = 0; x < width; x++) {
            img.setRGB(x, 0, rgb(60, 60, 60));
            img.setRGB(x, height - 1, rgb(60, 60, 60));
        }
        for (int y = 0; y < height; y++) {
            img.setRGB(0, y, rgb(60, 60, 60));
            img.setRGB(width - 1, y, rgb(60, 60, 60));
        }

        // rivets
        int[][] rivets = {
            {8, 8}, {width - 9, 8}, {8, height - 9}, {width - 9, height - 9}
        };

        for (int[] p : rivets) {
            drawFilledCircle(img, p[0], p[1], 2, rgb(180, 180, 180));
            drawFilledCircle(img, p[0] - 1, p[1] - 1, 1, rgb(220, 220, 220));
        }

        return img;
    }

    public static BufferedImage makeExitTexture(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cx = x / 8;
                int cy = y / 8;

                boolean checker = ((cx + cy) & 1) == 0;

                int r = 255;
                int g = checker ? 245 : 180;
                int b = checker ? 40 : 20;

                // bright border
                if (x < 2 || y < 2 || x >= width - 2 || y >= height - 2) {
                    r = 255;
                    g = 255;
                    b = 180;
                }

                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        return img;
    }

    private static void drawFilledCircle(BufferedImage img, int cx, int cy, int radius, int color) {
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                if (x < 0 || x >= img.getWidth() || y < 0 || y >= img.getHeight()) continue;
                int dx = x - cx;
                int dy = y - cy;
                if (dx * dx + dy * dy <= radius * radius) {
                    img.setRGB(x, y, color);
                }
            }
        }
    }

    private static int rgb(int r, int g, int b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
   
    public static BufferedImage tintTexture(BufferedImage src, int tintR, int tintG, int tintB, double strength) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);

        strength = Math.max(0.0, Math.min(1.0, strength));

        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int rgb = src.getRGB(x, y);

                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                int nr = (int) Math.round(r * (1.0 - strength) + tintR * strength);
                int ng = (int) Math.round(g * (1.0 - strength) + tintG * strength);
                int nb = (int) Math.round(b * (1.0 - strength) + tintB * strength);

                out.setRGB(x, y, (clamp(nr) << 16) | (clamp(ng) << 8) | clamp(nb));
            }
        }

        return out;
    }
    
    public static BufferedImage makePressurePlateTexture(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int cx = width / 2;
        int cy = height / 2;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Base full-tile metal slab so checkerboard does not visually dominate
                int r = 78;
                int g = 88;
                int b = 94;

                int noise = ((x * 13 + y * 17) & 15) - 7;
                r = clamp(r + noise);
                g = clamp(g + noise);
                b = clamp(b + noise);

                // Outer heavy frame
                if (x < 3 || y < 3 || x >= width - 3 || y >= height - 3) {
                    r = 42;
                    g = 48;
                    b = 54;
                }

                // Inner frame border
                if (x == 5 || y == 5 || x == width - 6 || y == height - 6) {
                    r = 105;
                    g = 115;
                    b = 122;
                }

                // Large inset panel
                if (x >= 8 && x < width - 8 && y >= 8 && y < height - 8) {
                    r = 98;
                    g = 108;
                    b = 116;

                    int innerNoise = ((x * 7 + y * 9) & 7) - 3;
                    r = clamp(r + innerNoise);
                    g = clamp(g + innerNoise);
                    b = clamp(b + innerNoise);
                }

                int dx = x - cx;
                int dy = y - cy;
                int d2 = dx * dx + dy * dy;

                int ringOuterR = width / 2 - 12;
                int ringInnerR = width / 2 - 20;
                int discR = width / 2 - 24;

                int ringOuter2 = ringOuterR * ringOuterR;
                int ringInner2 = ringInnerR * ringInnerR;
                int disc2 = discR * discR;

                // Bright cyan outer ring
                if (d2 <= ringOuter2 && d2 >= ringInner2) {
                    r = 28;
                    g = 185;
                    b = 200;
                }

                // Shadow ring just inside accent ring
                if (d2 < ringInner2 && d2 >= disc2) {
                    r = 55;
                    g = 68;
                    b = 74;
                }

                // Raised center disc
                if (d2 < disc2) {
                    r = 128;
                    g = 140;
                    b = 148;

                    // Strong top-left highlight
                    if (dx + dy < 0) {
                        r = clamp(r + 18);
                        g = clamp(g + 18);
                        b = clamp(b + 18);
                    }

                    // Strong lower-right shadow
                    if (dx + dy > 8) {
                        r = clamp(r - 12);
                        g = clamp(g - 12);
                        b = clamp(b - 12);
                    }
                }

                // Central cap
                int capR = 8;
                int cap2 = capR * capR;
                if (d2 < cap2) {
                    r = 165;
                    g = 178;
                    b = 186;

                    if (dx + dy > 4) {
                        r = clamp(r - 16);
                        g = clamp(g - 16);
                        b = clamp(b - 16);
                    }
                }

                // Corner bolts
                if ((x - 12) * (x - 12) + (y - 12) * (y - 12) < 8 ||
                    (x - (width - 13)) * (x - (width - 13)) + (y - 12) * (y - 12) < 8 ||
                    (x - 12) * (x - 12) + (y - (height - 13)) * (y - (height - 13)) < 8 ||
                    (x - (width - 13)) * (x - (width - 13)) + (y - (height - 13)) * (y - (height - 13)) < 8) {
                    r = 150;
                    g = 160;
                    b = 166;
                }

                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        return img;
    }

    public static BufferedImage makePressedPressurePlateTexture(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int cx = width / 2;
        int cy = height / 2;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Same overall slab, slightly darker
                int r = 72;
                int g = 80;
                int b = 86;

                int noise = ((x * 13 + y * 17) & 15) - 7;
                r = clamp(r + noise);
                g = clamp(g + noise);
                b = clamp(b + noise);

                // Outer heavy frame
                if (x < 3 || y < 3 || x >= width - 3 || y >= height - 3) {
                    r = 36;
                    g = 42;
                    b = 48;
                }

                // Inner frame border
                if (x == 5 || y == 5 || x == width - 6 || y == height - 6) {
                    r = 90;
                    g = 98;
                    b = 104;
                }

                // Large inset panel
                if (x >= 8 && x < width - 8 && y >= 8 && y < height - 8) {
                    r = 86;
                    g = 94;
                    b = 100;

                    int innerNoise = ((x * 7 + y * 9) & 7) - 3;
                    r = clamp(r + innerNoise);
                    g = clamp(g + innerNoise);
                    b = clamp(b + innerNoise);
                }

                int dx = x - cx;
                int dy = y - cy;
                int d2 = dx * dx + dy * dy;

                int ringOuterR = width / 2 - 12;
                int ringInnerR = width / 2 - 20;
                int discR = width / 2 - 24;

                int ringOuter2 = ringOuterR * ringOuterR;
                int ringInner2 = ringInnerR * ringInnerR;
                int disc2 = discR * discR;

                // Dimmed cyan ring
                if (d2 <= ringOuter2 && d2 >= ringInner2) {
                    r = 20;
                    g = 110;
                    b = 120;
                }

                // Darker shadow ring
                if (d2 < ringInner2 && d2 >= disc2) {
                    r = 38;
                    g = 46;
                    b = 52;
                }

                // Sunken center disc
                if (d2 < disc2) {
                    r = 58;
                    g = 68;
                    b = 74;

                    // Darker lower-right, lighter upper-left to imply recess
                    if (dx + dy > 0) {
                        r = clamp(r - 16);
                        g = clamp(g - 16);
                        b = clamp(b - 16);
                    } else {
                        r = clamp(r + 6);
                        g = clamp(g + 6);
                        b = clamp(b + 6);
                    }
                }

                // Very dark pressed cap in the middle
                int capR = 8;
                int cap2 = capR * capR;
                if (d2 < cap2) {
                    r = 34;
                    g = 40;
                    b = 44;

                    if (dx + dy < 0) {
                        r = clamp(r + 5);
                        g = clamp(g + 5);
                        b = clamp(b + 5);
                    }
                }

                // Darker bolts
                if ((x - 12) * (x - 12) + (y - 12) * (y - 12) < 8 ||
                    (x - (width - 13)) * (x - (width - 13)) + (y - 12) * (y - 12) < 8 ||
                    (x - 12) * (x - 12) + (y - (height - 13)) * (y - (height - 13)) < 8 ||
                    (x - (width - 13)) * (x - (width - 13)) + (y - (height - 13)) * (y - (height - 13)) < 8) {
                    r = 105;
                    g = 112;
                    b = 118;
                }

                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        return img;
    }
    
    public static BufferedImage makeStoneFloorTexture(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(55555);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = 96 + rand.nextInt(12);
                img.setRGB(x, y, rgb(gray, gray, gray));
            }
        }

        // subtle slab seams
        for (int y = 0; y < height; y += 16) {
            for (int x = 0; x < width; x++) {
                int gray = 78 + rand.nextInt(8);
                img.setRGB(x, y, rgb(gray, gray, gray));
                if (y + 1 < height) {
                    img.setRGB(x, y + 1, rgb(gray, gray, gray));
                }
            }
        }

        for (int x = 0; x < width; x += 16) {
            for (int y = 0; y < height; y++) {
                int gray = 78 + rand.nextInt(8);
                img.setRGB(x, y, rgb(gray, gray, gray));
                if (x + 1 < width) {
                    img.setRGB(x + 1, y, rgb(gray, gray, gray));
                }
            }
        }

        // mild noise pass
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb0 = img.getRGB(x, y);
                int r = (rgb0 >> 16) & 0xFF;
                int g = (rgb0 >> 8) & 0xFF;
                int b = rgb0 & 0xFF;

                int noise = rand.nextInt(7) - 3;
                r = clamp(r + noise);
                g = clamp(g + noise);
                b = clamp(b + noise);

                img.setRGB(x, y, rgb(r, g, b));
            }
        }

        return img;
    }

    public static BufferedImage makeStoneCeilingTexture(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(66666);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = 82 + rand.nextInt(10);
                img.setRGB(x, y, rgb(gray, gray, gray));
            }
        }

        // wider, softer ceiling seams
        for (int y = 0; y < height; y += 20) {
            for (int x = 0; x < width; x++) {
                int gray = 68 + rand.nextInt(6);
                img.setRGB(x, y, rgb(gray, gray, gray));
            }
        }

        for (int x = 0; x < width; x += 20) {
            for (int y = 0; y < height; y++) {
                int gray = 68 + rand.nextInt(6);
                img.setRGB(x, y, rgb(gray, gray, gray));
            }
        }

        // light mottling so it does not look flat
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb0 = img.getRGB(x, y);
                int r = (rgb0 >> 16) & 0xFF;
                int g = (rgb0 >> 8) & 0xFF;
                int b = rgb0 & 0xFF;

                int noise = rand.nextInt(5) - 2;
                r = clamp(r + noise);
                g = clamp(g + noise);
                b = clamp(b + noise);

                img.setRGB(x, y, rgb(r, g, b));
            }
        }

        return img;
    }
    
    public static BufferedImage makeStoneTextureLightGray(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(77701);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = 120 + rand.nextInt(32);   // lower than before
                img.setRGB(x, y, rgb(gray, gray, gray));
            }
        }

        for (int y = 0; y < height; y += 16) {
            for (int x = 0; x < width; x++) {
                int gray = 88 + rand.nextInt(16);
                img.setRGB(x, y, rgb(gray, gray, gray));
                if (y + 1 < height) img.setRGB(x, y + 1, rgb(gray, gray, gray));
            }
        }

        for (int x = 0; x < width; x += 16) {
            for (int y = 0; y < height; y++) {
                int gray = 88 + rand.nextInt(16);
                img.setRGB(x, y, rgb(gray, gray, gray));
                if (x + 1 < width) img.setRGB(x + 1, y, rgb(gray, gray, gray));
            }
        }

        return img;
    }

    public static BufferedImage makeStoneTextureDarkGray(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(77702);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = 55 + rand.nextInt(35);
                img.setRGB(x, y, rgb(gray, gray, gray));
            }
        }

        for (int y = 0; y < height; y += 16) {
            for (int x = 0; x < width; x++) {
                int gray = 28 + rand.nextInt(16);
                img.setRGB(x, y, rgb(gray, gray, gray));
                if (y + 1 < height) img.setRGB(x, y + 1, rgb(gray, gray, gray));
            }
        }

        for (int x = 0; x < width; x += 16) {
            for (int y = 0; y < height; y++) {
                int gray = 28 + rand.nextInt(16);
                img.setRGB(x, y, rgb(gray, gray, gray));
                if (x + 1 < width) img.setRGB(x + 1, y, rgb(gray, gray, gray));
            }
        }

        return img;
    }

    public static BufferedImage makeMetalTextureBlack(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(77703);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = 26 + rand.nextInt(18);
                int blue = clamp(gray + 4);
                img.setRGB(x, y, rgb(gray, gray, blue));
            }
        }

        for (int x = 0; x < width; x++) {
            img.setRGB(x, 0, rgb(8, 8, 10));
            img.setRGB(x, height - 1, rgb(8, 8, 10));
        }
        for (int y = 0; y < height; y++) {
            img.setRGB(0, y, rgb(8, 8, 10));
            img.setRGB(width - 1, y, rgb(8, 8, 10));
        }

        int[][] rivets = {
            {8, 8}, {width - 9, 8}, {8, height - 9}, {width - 9, height - 9}
        };

        for (int[] p : rivets) {
            drawFilledCircle(img, p[0], p[1], 2, rgb(90, 90, 96));
            drawFilledCircle(img, p[0] - 1, p[1] - 1, 1, rgb(130, 130, 138));
        }

        return img;
    }

    public static BufferedImage makeStoneTextureOffWhite(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(77704);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int base = 165 + rand.nextInt(22);   // was much brighter
                int r = clamp(base + 3);
                int g = clamp(base + 1);
                int b = clamp(base - 2);
                img.setRGB(x, y, rgb(r, g, b));
            }
        }

        for (int y = 0; y < height; y += 16) {
            for (int x = 0; x < width; x++) {
                int gray = 118 + rand.nextInt(14);
                img.setRGB(x, y, rgb(gray, gray, gray));
            }
        }

        for (int x = 0; x < width; x += 16) {
            for (int y = 0; y < height; y++) {
                int gray = 118 + rand.nextInt(14);
                img.setRGB(x, y, rgb(gray, gray, gray));
            }
        }

        return img;
    }
    
    public static BufferedImage makeLandmarkStripeTexture(int width, int height, int r1, int g1, int b1, int r2, int g2, int b2) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int c1 = rgb(r1, g1, b1);
        int c2 = rgb(r2, g2, b2);
        int border = rgb(clamp(r2 - 30), clamp(g2 - 30), clamp(b2 - 30));

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int stripe = (x / 8) % 2;
                img.setRGB(x, y, stripe == 0 ? c1 : c2);

                if (x < 2 || y < 2 || x >= width - 2 || y >= height - 2) {
                    img.setRGB(x, y, border);
                }
            }
        }

        return img;
    }

    public static BufferedImage makeLandmarkBandTexture(int width, int height, int r1, int g1, int b1, int r2, int g2, int b2) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int base = rgb(r1, g1, b1);
        int band = rgb(r2, g2, b2);
        int dark = rgb(clamp(r1 - 35), clamp(g1 - 35), clamp(b1 - 35));

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, base);

                if (y >= 10 && y < 18) img.setRGB(x, y, band);
                if (y >= 28 && y < 36) img.setRGB(x, y, band);
                if (y >= 46 && y < 54) img.setRGB(x, y, band);

                if (x < 2 || y < 2 || x >= width - 2 || y >= height - 2) {
                    img.setRGB(x, y, dark);
                }
            }
        }

        return img;
    }

    public static BufferedImage makeLandmarkBraceTexture(int width, int height, int r1, int g1, int b1, int r2, int g2, int b2) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int base = rgb(r1, g1, b1);
        int brace = rgb(r2, g2, b2);
        int border = rgb(clamp(r1 - 30), clamp(g1 - 30), clamp(b1 - 30));

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, base);

                if (Math.abs(x - y) < 3 || Math.abs((width - 1 - x) - y) < 3) {
                    img.setRGB(x, y, brace);
                }

                if (x < 3 || y < 3 || x >= width - 3 || y >= height - 3) {
                    img.setRGB(x, y, border);
                }
            }
        }

        return img;
    }

    public static BufferedImage makeLandmarkPanelTexture(int width, int height, int r1, int g1, int b1, int r2, int g2, int b2) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int outer = rgb(r1, g1, b1);
        int inner = rgb(r2, g2, b2);
        int trim = rgb(clamp(r2 + 20), clamp(g2 + 20), clamp(b2 + 20));

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, outer);

                if (x >= 8 && x < width - 8 && y >= 8 && y < height - 8) {
                    img.setRGB(x, y, inner);
                }

                if (x == 6 || y == 6 || x == width - 7 || y == height - 7) {
                    img.setRGB(x, y, trim);
                }

                if (x < 2 || y < 2 || x >= width - 2 || y >= height - 2) {
                    img.setRGB(x, y, rgb(clamp(r1 - 25), clamp(g1 - 25), clamp(b1 - 25)));
                }
            }
        }

        return img;
    }
    
    public static BufferedImage makeBrickTextureRed(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(11111);

        int mortar = rgb(65, 55, 50);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, mortar);
            }
        }

        int brickH = 16;
        int brickW = 32;
        int mortarThick = 2;

        for (int row = 0; row < height; row += brickH) {
            boolean offset = ((row / brickH) % 2 == 1);
            int xStart = offset ? -(brickW / 2) : 0;

            for (int col = xStart; col < width; col += brickW) {
                int baseR = 155 + rand.nextInt(45);
                int baseG = 55 + rand.nextInt(20);
                int baseB = 40 + rand.nextInt(15);

                for (int y = row + mortarThick; y < row + brickH - mortarThick && y < height; y++) {
                    for (int x = col + mortarThick; x < col + brickW - mortarThick; x++) {
                        if (x < 0 || x >= width) continue;

                        int noise = rand.nextInt(31) - 15;
                        int r = clamp(baseR + noise);
                        int g = clamp(baseG + noise);
                        int b = clamp(baseB + noise);

                        img.setRGB(x, y, rgb(r, g, b));
                    }
                }
            }
        }

        return img;
    }

    public static BufferedImage makeStoneTextureBlue(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(22222);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = 70 + rand.nextInt(35);
                int g = 90 + rand.nextInt(35);
                int b = 120 + rand.nextInt(45);
                img.setRGB(x, y, rgb(r, g, b));
            }
        }

        for (int y = 0; y < height; y += 16) {
            for (int x = 0; x < width; x++) {
                int r = 45 + rand.nextInt(15);
                int g = 60 + rand.nextInt(15);
                int b = 90 + rand.nextInt(20);
                img.setRGB(x, y, rgb(r, g, b));
            }
        }

        for (int x = 0; x < width; x += 16) {
            for (int y = 0; y < height; y++) {
                int r = 45 + rand.nextInt(15);
                int g = 60 + rand.nextInt(15);
                int b = 90 + rand.nextInt(20);
                img.setRGB(x, y, rgb(r, g, b));
            }
        }

        return img;
    }

    public static BufferedImage makeStoneTextureGreen(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(33333);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = 75 + rand.nextInt(25);
                int g = 110 + rand.nextInt(40);
                int b = 75 + rand.nextInt(25);
                img.setRGB(x, y, rgb(r, g, b));
            }
        }

        for (int y = 0; y < height; y += 16) {
            for (int x = 0; x < width; x++) {
                int r = 50 + rand.nextInt(15);
                int g = 75 + rand.nextInt(20);
                int b = 50 + rand.nextInt(15);
                img.setRGB(x, y, rgb(r, g, b));
            }
        }

        for (int x = 0; x < width; x += 16) {
            for (int y = 0; y < height; y++) {
                int r = 50 + rand.nextInt(15);
                int g = 75 + rand.nextInt(20);
                int b = 50 + rand.nextInt(15);
                img.setRGB(x, y, rgb(r, g, b));
            }
        }

        return img;
    }

    public static BufferedImage makeMetalTextureBronze(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(44444);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = 125 + rand.nextInt(30);
                int g = 90 + rand.nextInt(20);
                int b = 55 + rand.nextInt(15);
                img.setRGB(x, y, rgb(r, g, b));
            }
        }

        for (int x = 0; x < width; x++) {
            img.setRGB(x, 0, rgb(70, 50, 30));
            img.setRGB(x, height - 1, rgb(70, 50, 30));
        }
        for (int y = 0; y < height; y++) {
            img.setRGB(0, y, rgb(70, 50, 30));
            img.setRGB(width - 1, y, rgb(70, 50, 30));
        }

        int[][] rivets = {
            {8, 8}, {width - 9, 8}, {8, height - 9}, {width - 9, height - 9}
        };

        for (int[] p : rivets) {
            drawFilledCircle(img, p[0], p[1], 2, rgb(190, 150, 90));
            drawFilledCircle(img, p[0] - 1, p[1] - 1, 1, rgb(230, 190, 120));
        }

        return img;
    }

    public static BufferedImage brightenTexture(BufferedImage src, int amount) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int rgb = src.getRGB(x, y);

                int r = clamp(((rgb >> 16) & 0xFF) + amount);
                int g = clamp(((rgb >> 8) & 0xFF) + amount);
                int b = clamp((rgb & 0xFF) + amount);

                out.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        return out;
    }

    public static BufferedImage makeRemovableWallTexture(int size, int seed) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        int bgA = 0x3F3F3F;
        int bgB = 0x4A4A4A;
        int panelDark = 0x2C2C2C;
        int panelMid = 0x3A3A3A;
        int panelLight = 0x606060;
        int groove = 0x242424;
        int screw = 0x707070;

        int tile = Math.max(8, size / 3);
        int checker = Math.max(4, size / 8);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean baseChecker = ((x / checker) ^ (y / checker)) != 0;
                int base = baseChecker ? bgA : bgB;

                int grain = (((x * 37 + y * 53 + seed * 11) & 15) - 7);

                int r = ((base >> 16) & 0xFF) + grain;
                int g = ((base >> 8) & 0xFF) + grain;
                int b = (base & 0xFF) + grain;

                int localX = x % tile;
                int localY = y % tile;

                boolean verticalGroove = localX <= 1 || localX >= tile - 2;
                boolean horizontalGroove = localY <= 1 || localY >= tile - 2;

                boolean innerLeft = localX == 5;
                boolean innerTop = localY == 5;
                boolean innerRight = localX == tile - 6;
                boolean innerBottom = localY == tile - 6;

                if (verticalGroove || horizontalGroove) {
                    r = blendChannel(r, (groove >> 16) & 0xFF, 0.75);
                    g = blendChannel(g, (groove >> 8) & 0xFF, 0.75);
                    b = blendChannel(b, groove & 0xFF, 0.75);
                }

                if (innerLeft || innerTop) {
                    r = blendChannel(r, (panelLight >> 16) & 0xFF, 0.35);
                    g = blendChannel(g, (panelLight >> 8) & 0xFF, 0.35);
                    b = blendChannel(b, panelLight & 0xFF, 0.35);
                }

                if (innerRight || innerBottom) {
                    r = blendChannel(r, (panelDark >> 16) & 0xFF, 0.45);
                    g = blendChannel(g, (panelDark >> 8) & 0xFF, 0.45);
                    b = blendChannel(b, panelDark & 0xFF, 0.45);
                }

                int inset = 8;
                if (localX > inset && localX < tile - inset && localY > inset && localY < tile - inset) {
                    r = blendChannel(r, (panelMid >> 16) & 0xFF, 0.18);
                    g = blendChannel(g, (panelMid >> 8) & 0xFF, 0.18);
                    b = blendChannel(b, panelMid & 0xFF, 0.18);
                }

                boolean screwSpot =
                        (localX == 3 || localX == tile - 4) &&
                        (localY == 3 || localY == tile - 4);

                if (screwSpot) {
                    r = blendChannel(r, (screw >> 16) & 0xFF, 0.65);
                    g = blendChannel(g, (screw >> 8) & 0xFF, 0.65);
                    b = blendChannel(b, screw & 0xFF, 0.65);
                }

                img.setRGB(
                        x,
                        y,
                        (clamp(r, 0, 255) << 16) |
                        (clamp(g, 0, 255) << 8) |
                        clamp(b, 0, 255)
                );
            }
        }

        return img;
    }

    private static int blendChannel(int a, int b, double t) {
        return clamp((int) Math.round(a + (b - a) * t), 0, 255);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}