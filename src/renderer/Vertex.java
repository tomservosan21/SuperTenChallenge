package renderer;

public class Vertex {
    public Vector4 pos;
    public Vector3 normal;
    public double clipZ;
    public double clipW;

    public double u;
    public double v;

    public Vertex(Vector4 pos, Vector3 normal) {
        this(pos, normal, 0.0, 0.0);
    }

    public Vertex(Vector4 pos, Vector3 normal, double u, double v) {
        this.pos = pos;
        this.normal = normal;
        this.clipZ = pos.z;
        this.clipW = pos.w;
        this.u = u;
        this.v = v;
    }
}