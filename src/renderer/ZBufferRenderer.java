package renderer;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;

public class ZBufferRenderer {

    private int width;
    private int height;

    // Cache-friendly layout: rows first, then columns
    private float[][] zBuffer;

    private BufferedImage image;
    private int[] pixels;

    public ZBufferRenderer(int width, int height) {
        this.width = width;
        this.height = height;

        zBuffer = new float[height][width];

        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        clear();
    }

    public void clear() {
        for (int y = 0; y < height; y++) {
            float[] zRow = zBuffer[y];
            int rowBase = y * width;

            for (int x = 0; x < width; x++) {
                zRow[x] = Float.NEGATIVE_INFINITY;
                pixels[rowBase + x] = 0;
            }
        }
    }

    public BufferedImage getImage() {
        return image;
    }

    /**
     * Returns a completed snapshot copy of the current renderer image.
     * This avoids sharing the mutable render target with Swing's paint thread.
     */
    public BufferedImage copyImage() {
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return copy;
    }

    // Backward-compatible overload if anything still calls the old flat-shaded path.
    public void drawTriangle(Point3D p0, Point3D p1, Point3D p2,
                             int litRed, int litGreen, int litBlue,
                             BufferedImage texture) {
        p0.r = litRed;   p0.g = litGreen;   p0.b = litBlue;
        p1.r = litRed;   p1.g = litGreen;   p1.b = litBlue;
        p2.r = litRed;   p2.g = litGreen;   p2.b = litBlue;
        drawTriangle(p0, p1, p2, texture);
    }

    public void drawTriangle(Point3D p0, Point3D p1, Point3D p2,
                             BufferedImage texture) {

        int minX = (int) Math.max(0, Math.floor(Math.min(p0.x, Math.min(p1.x, p2.x))));
        int maxX = (int) Math.min(width - 1, Math.ceil(Math.max(p0.x, Math.max(p1.x, p2.x))));
        int minY = (int) Math.max(0, Math.floor(Math.min(p0.y, Math.min(p1.y, p2.y))));
        int maxY = (int) Math.min(height - 1, Math.ceil(Math.max(p0.y, Math.max(p1.y, p2.y))));

        if (minX > maxX || minY > maxY) {
            return;
        }

        float area = edge(p0, p1, p2);
        if (area == 0.0f) {
            return;
        }

        // Optional tiny-triangle reject
        if (Math.abs(area) < 0.25f) {
            return;
        }

        float invArea = 1.0f / area;

        // These coefficients match your existing edge() sign convention:
        // edge(a,b,p) = (p.x-a.x)*(b.y-a.y) - (p.y-a.y)*(b.x-a.x)
        float A0 = p2.y - p1.y;
        float B0 = p1.x - p2.x;
        float C0 = p2.x * p1.y - p1.x * p2.y;

        float A1 = p0.y - p2.y;
        float B1 = p2.x - p0.x;
        float C1 = p0.x * p2.y - p2.x * p0.y;

        float A2 = p1.y - p0.y;
        float B2 = p0.x - p1.x;
        float C2 = p1.x * p0.y - p0.x * p1.y;

        // Start at center of first pixel
        float startX = minX + 0.5f;
        float startY = minY + 0.5f;

        float w0Row = A0 * startX + B0 * startY + C0;
        float w1Row = A1 * startX + B1 * startY + C1;
        float w2Row = A2 * startX + B2 * startY + C2;

        float p0InvZ = p0.invDepth;
        float p1InvZ = p1.invDepth;
        float p2InvZ = p2.invDepth;

        float p0UOverZ = p0.u * p0InvZ;
        float p0VOverZ = p0.v * p0InvZ;

        float p1UOverZ = p1.u * p1InvZ;
        float p1VOverZ = p1.v * p1InvZ;

        float p2UOverZ = p2.u * p2InvZ;
        float p2VOverZ = p2.v * p2InvZ;

        // Vertex colors for Gouraud interpolation
        float p0R = p0.r;
        float p0G = p0.g;
        float p0B = p0.b;

        float p1R = p1.r;
        float p1G = p1.g;
        float p1B = p1.b;

        float p2R = p2.r;
        float p2G = p2.g;
        float p2B = p2.b;

        // Fast texture access when backing storage is int[]
        int[] texData = null;
        int texW = 0;
        int texH = 0;
        boolean useFastTexture = false;

        if (texture != null) {
            texW = texture.getWidth();
            texH = texture.getHeight();

            DataBuffer db = texture.getRaster().getDataBuffer();
            if (db instanceof DataBufferInt) {
                texData = ((DataBufferInt) db).getData();
                useFastTexture = true;
            }
        }

        final float DEPTH_EPS = 1e-4f;

        for (int y = minY; y <= maxY; y++) {
            float[] zRow = zBuffer[y];
            int rowBase = y * width;

            float w0 = w0Row;
            float w1 = w1Row;
            float w2 = w2Row;

            for (int x = minX; x <= maxX; x++) {
                boolean inside =
                        (w0 >= 0.0f && w1 >= 0.0f && w2 >= 0.0f) ||
                        (w0 <= 0.0f && w1 <= 0.0f && w2 <= 0.0f);

                if (inside) {
                    float b0 = w0 * invArea;
                    float b1 = w1 * invArea;
                    float b2 = w2 * invArea;

                    float invDepth =
                            b0 * p0InvZ +
                            b1 * p1InvZ +
                            b2 * p2InvZ;

                    if (invDepth > 0.0f && invDepth > zRow[x] + DEPTH_EPS) {
                        // Gouraud color interpolation
                        int litRed = clamp255(Math.round(
                                b0 * p0R +
                                b1 * p1R +
                                b2 * p2R
                        ));
                        int litGreen = clamp255(Math.round(
                                b0 * p0G +
                                b1 * p1G +
                                b2 * p2G
                        ));
                        int litBlue = clamp255(Math.round(
                                b0 * p0B +
                                b1 * p1B +
                                b2 * p2B
                        ));

                        int rgb;

                        if (texture != null) {
                            float uOverZ =
                                    b0 * p0UOverZ +
                                    b1 * p1UOverZ +
                                    b2 * p2UOverZ;

                            float vOverZ =
                                    b0 * p0VOverZ +
                                    b1 * p1VOverZ +
                                    b2 * p2VOverZ;

                            float recip = 1.0f / invDepth;

                            float u = uOverZ * recip;
                            float v = vOverZ * recip;

                            // Wrap for tiling
                            u = u - (float) Math.floor(u);
                            v = v - (float) Math.floor(v);

                            int tx = (int) (u * texW);
                            int ty = (int) (v * texH);

                            if (tx < 0) tx = 0;
                            else if (tx >= texW) tx = texW - 1;

                            if (ty < 0) ty = 0;
                            else if (ty >= texH) ty = texH - 1;

                            int texRgb;
                            if (useFastTexture) {
                                texRgb = texData[ty * texW + tx];
                            } else {
                                texRgb = texture.getRGB(tx, ty);
                            }

                            int tr = (texRgb >> 16) & 0xFF;
                            int tg = (texRgb >> 8) & 0xFF;
                            int tb = texRgb & 0xFF;

                            tr = tr * litRed / 255;
                            tg = tg * litGreen / 255;
                            tb = tb * litBlue / 255;

                            rgb = (tr << 16) | (tg << 8) | tb;
                        } else {
                            rgb = (litRed << 16) | (litGreen << 8) | litBlue;
                        }

                        zRow[x] = invDepth;
                        pixels[rowBase + x] = rgb;
                    }
                }

                // Advance one pixel in +X
                w0 += A0;
                w1 += A1;
                w2 += A2;
            }

            // Advance one row in +Y
            w0Row += B0;
            w1Row += B1;
            w2Row += B2;
        }
    }

    private int clamp255(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    private float edge(Point3D a, Point3D b, Point3D c) {
        return (c.x - a.x) * (b.y - a.y) - (c.y - a.y) * (b.x - a.x);
    }
}