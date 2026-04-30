package renderer;

//================= Clipper.java =================
import java.util.*;

public class Clipper {

    public static List<Vertex> clipAgainstPlane(
            List<Vertex> in,
            java.util.function.Predicate<Vertex> inside,
            java.util.function.BiFunction<Vertex, Vertex, Vertex> intersect
    ) {
        List<Vertex> out = new ArrayList<>();

        for (int i = 0; i < in.size(); i++) {
            Vertex a = in.get(i);
            Vertex b = in.get((i + 1) % in.size());

            boolean aIn = inside.test(a);
            boolean bIn = inside.test(b);

            if (aIn && bIn) {
                out.add(b);
            } else if (aIn && !bIn) {
                out.add(intersect.apply(a, b));
            } else if (!aIn && bIn) {
                out.add(intersect.apply(a, b));
                out.add(b);
            }
        }

        return out;
    }

    public static List<Vertex> clipNear(List<Vertex> in) {
        List<Vertex> out = new ArrayList<>();

        for (int i = 0; i < in.size(); i++) {
            Vertex a = in.get(i);
            Vertex b = in.get((i + 1) % in.size());

            boolean aIn = a.pos.z + a.pos.w >= 0;
            boolean bIn = b.pos.z + b.pos.w >= 0;

            if (aIn && bIn) {
                out.add(b);
            } else if (aIn && !bIn) {
                out.add(intersect(a, b));
            } else if (!aIn && bIn) {
                out.add(intersect(a, b));
                out.add(b);
            }
        }

        return out;
    }

    private static Vertex intersect(Vertex a, Vertex b) {
        double da = a.pos.z + a.pos.w;
        double db = b.pos.z + b.pos.w;
        double t = da / (da - db);

        Vector4 p = new Vector4(
                a.pos.x + (b.pos.x - a.pos.x) * t,
                a.pos.y + (b.pos.y - a.pos.y) * t,
                a.pos.z + (b.pos.z - a.pos.z) * t,
                a.pos.w + (b.pos.w - a.pos.w) * t
        );

        Vector3 n = a.normal.add(b.normal.sub(a.normal).mul(t));

        double u = a.u + (b.u - a.u) * t;
        double v = a.v + (b.v - a.v) * t;

        return new Vertex(p, n, u, v);
    }

    public static List<Vertex> clipNearView(List<Vertex> in) {
        List<Vertex> out = new ArrayList<>();
        double near = 0.1; // must match projection near plane

        for (int i = 0; i < in.size(); i++) {
            Vertex a = in.get(i);
            Vertex b = in.get((i + 1) % in.size());

            boolean aIn = a.pos.z <= -near;
            boolean bIn = b.pos.z <= -near;

            if (aIn && bIn) {
                out.add(b);
            } else if (aIn && !bIn) {
                out.add(intersectViewNear(a, b, near));
            } else if (!aIn && bIn) {
                out.add(intersectViewNear(a, b, near));
                out.add(b);
            }
        }

        return out;
    }

    private static Vertex intersectViewNear(Vertex a, Vertex b, double near) {
        double az = a.pos.z;
        double bz = b.pos.z;

        double t = ((-near) - az) / (bz - az);

        Vector4 p = new Vector4(
                a.pos.x + (b.pos.x - a.pos.x) * t,
                a.pos.y + (b.pos.y - a.pos.y) * t,
                a.pos.z + (b.pos.z - a.pos.z) * t,
                1.0
        );

        Vector3 n = a.normal.add(b.normal.sub(a.normal).mul(t));

        double u = a.u + (b.u - a.u) * t;
        double v = a.v + (b.v - a.v) * t;

        return new Vertex(p, n, u, v);
    }

    public static List<Vertex> clipLeft(List<Vertex> in) {
        return clipAgainstPlane(
                in,
                v -> v.pos.x + v.pos.w >= 0,
                (a, b) -> intersectLeft(a, b)
        );
    }

    public static List<Vertex> clipRight(List<Vertex> in) {
        return clipAgainstPlane(
                in,
                v -> v.pos.w - v.pos.x >= 0,
                (a, b) -> intersectRight(a, b)
        );
    }

    public static List<Vertex> clipBottom(List<Vertex> in) {
        return clipAgainstPlane(
                in,
                v -> v.pos.y + v.pos.w >= 0,
                (a, b) -> intersectBottom(a, b)
        );
    }

    public static List<Vertex> clipTop(List<Vertex> in) {
        return clipAgainstPlane(
                in,
                v -> v.pos.w - v.pos.y >= 0,
                (a, b) -> intersectTop(a, b)
        );
    }

    private static Vertex intersectLeft(Vertex a, Vertex b) {
        double da = a.pos.x + a.pos.w;
        double db = b.pos.x + b.pos.w;
        return intersectByDistance(a, b, da, db);
    }

    private static Vertex intersectRight(Vertex a, Vertex b) {
        double da = a.pos.w - a.pos.x;
        double db = b.pos.w - b.pos.x;
        return intersectByDistance(a, b, da, db);
    }

    private static Vertex intersectBottom(Vertex a, Vertex b) {
        double da = a.pos.y + a.pos.w;
        double db = b.pos.y + b.pos.w;
        return intersectByDistance(a, b, da, db);
    }

    private static Vertex intersectTop(Vertex a, Vertex b) {
        double da = a.pos.w - a.pos.y;
        double db = b.pos.w - b.pos.y;
        return intersectByDistance(a, b, da, db);
    }

    private static Vertex intersectByDistance(Vertex a, Vertex b, double da, double db) {
        double t = da / (da - db);

        Vector4 p = new Vector4(
                a.pos.x + (b.pos.x - a.pos.x) * t,
                a.pos.y + (b.pos.y - a.pos.y) * t,
                a.pos.z + (b.pos.z - a.pos.z) * t,
                a.pos.w + (b.pos.w - a.pos.w) * t
        );

        Vector3 n = a.normal.add(b.normal.sub(a.normal).mul(t));

        double u = a.u + (b.u - a.u) * t;
        double v = a.v + (b.v - a.v) * t;

        return new Vertex(p, n, u, v);
    }
}