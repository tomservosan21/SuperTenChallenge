package renderer;

public class RotationMatrices {

    public static void main(String[] args) {
        double angle = Math.toRadians(45);

        double[][] rotXp = rotationX(angle);
        double[][] rotXn = rotationX(-angle);

        double[][] rotYp = rotationY(angle);
        double[][] rotYn = rotationY(-angle);

        double[][] rotZp = rotationZ(angle);
        double[][] rotZn = rotationZ(-angle);

        print("Rotation +X", rotXp);
        print("Rotation -X", rotXn);
        print("Rotation +Y", rotYp);
        print("Rotation -Y", rotYn);
        print("Rotation +Z", rotZp);
        print("Rotation -Z", rotZn);
    }

    // ---------------- ROTATION ABOUT X ----------------
    public static double[][] rotationX(double theta) {
        double c = Math.cos(theta);
        double s = Math.sin(theta);

        return new double[][] {
            {1, 0, 0, 0},
            {0, c, -s, 0},
            {0, s,  c, 0},
            {0, 0, 0, 1}
        };
    }

    // ---------------- ROTATION ABOUT Y ----------------
    public static double[][] rotationY(double theta) {
        double c = Math.cos(theta);
        double s = Math.sin(theta);

        return new double[][] {
            { c, 0, s, 0},
            { 0, 1, 0, 0},
            {-s, 0, c, 0},
            { 0, 0, 0, 1}
        };
    }

    // ---------------- ROTATION ABOUT Z ----------------
    public static double[][] rotationZ(double theta) {
        double c = Math.cos(theta);
        double s = Math.sin(theta);

        return new double[][] {
            {c, -s, 0, 0},
            {s,  c, 0, 0},
            {0,  0, 1, 0},
            {0,  0, 0, 1}
        };
    }

    // ---------------- PRINT MATRIX ----------------
    public static void print(String label, double[][] m) {
        System.out.println(label + ":");
        for (int i = 0; i < 4; i++) {
            System.out.printf("[%.4f  %.4f  %.4f  %.4f]%n",
                    m[i][0], m[i][1], m[i][2], m[i][3]);
        }
        System.out.println();
    }
}