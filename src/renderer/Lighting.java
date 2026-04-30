package renderer;

public class Lighting {

    // Bright enough overall, but not so high that it flattens contrast.
    static double ambientIntensity = 0.27;

    // Small global fill.
    static double diffuseIntensity = 0.20;

    static public Vector3 lightDir = new Vector3(-1, 1, -0.5).normalize();

    // Torch tuning: brighter locally, tighter falloff, stronger separation
    static double torchIntensity = 1.42;
    static double torchRange = 12.5;
    static double torchForwardBias = 0.62;
    static double minimumLight = 0.12;

    public static void computeLighting(
            Triangle tri,
            Vector3 viewLight
    ) {
        Vector3 center = tri.getCenter();
        Vector3 normal = tri.normal.normalize();

        double intensity = computeIntensityAt(center, normal, viewLight);

        tri.lightingRed = clamp255((int) Math.round(tri.red * intensity));
        tri.lightingGreen = clamp255((int) Math.round(tri.green * intensity));
        tri.lightingBlue = clamp255((int) Math.round(tri.blue * intensity));
    }

    public static double computeIntensityAt(
            Vector3 viewPos,
            Vector3 normal,
            Vector3 viewLight
    ) {
        Vector3 n = normal.normalize();

        // ------------------------------------------------------------
        // 1) Directional
        // ------------------------------------------------------------
        double diffuse = Math.max(0.0, n.dot(viewLight));
        double directionalTerm = diffuseIntensity * diffuse;

        // ------------------------------------------------------------
        // 2) Torch
        // ------------------------------------------------------------
        Vector3 torchPos = new Vector3(0.0, -0.35, 0.0);
        Vector3 toLight = torchPos.sub(viewPos);

        double dist = Math.sqrt(
                toLight.x * toLight.x +
                toLight.y * toLight.y +
                toLight.z * toLight.z
        );

        Vector3 torchDir = (dist > 1e-9)
                ? toLight.mul(1.0 / dist)
                : new Vector3(0, 0, 1);

        double torchLambert = Math.max(0.0, n.dot(torchDir));

        // Tighter and steeper falloff for stronger near/far contrast
        double normalizedDist = dist / torchRange;
        double distanceFalloff = 1.0 / (1.0 + normalizedDist * normalizedDist * 3.2);

        double forwardFactor = 1.0;
        if (viewPos.z < 0.0) {
            double frontness = Math.min(1.0, (-viewPos.z) / torchRange);
            forwardFactor += torchForwardBias * frontness;
        } else {
            forwardFactor *= 0.34;
        }

        double torchTerm = torchIntensity * torchLambert * distanceFalloff * forwardFactor;

        // ------------------------------------------------------------
        // 3) Combine
        // ------------------------------------------------------------
        double intensity = ambientIntensity + directionalTerm + torchTerm;

        // Preserve some brightness while keeping better separation
        intensity = Math.pow(intensity, 0.95);

        intensity = Math.max(minimumLight, intensity);
        intensity = Math.min(1.0, Math.max(0.0, intensity));

        return intensity;
    }

    public static void computeLitColorAt(
            Vector3 viewPos,
            Vector3 normal,
            Vector3 viewLight,
            int baseRed,
            int baseGreen,
            int baseBlue,
            Point3D out
    ) {
        double intensity = computeIntensityAt(viewPos, normal, viewLight);

        out.r = clamp255f((float) (baseRed * intensity));
        out.g = clamp255f((float) (baseGreen * intensity));
        out.b = clamp255f((float) (baseBlue * intensity));
    }

    private static int clamp255(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    private static float clamp255f(float v) {
        if (v < 0.0f) return 0.0f;
        if (v > 255.0f) return 255.0f;
        return v;
    }
}