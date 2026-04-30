package renderer;

public class Point3D {
    public float x, y, z;
    public float invDepth;

    public float u;
    public float v;

    // Gouraud-lit vertex color
    public float r;
    public float g;
    public float b;

    public Point3D(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.invDepth = 0;
        this.u = 0;
        this.v = 0;
        this.r = 255.0f;
        this.g = 255.0f;
        this.b = 255.0f;
    }

    public Point3D(Vector3 vec) {
        this.x = (float) vec.x;
        this.y = (float) vec.y;
        this.z = (float) vec.z;
        this.invDepth = 0;
        this.u = 0;
        this.v = 0;
        this.r = 255.0f;
        this.g = 255.0f;
        this.b = 255.0f;
    }

    public Point3D(Vertex vec) {
        this.x = (float) vec.pos.x;
        this.y = (float) vec.pos.y;
        this.z = (float) vec.pos.z;
        this.invDepth = 0;
        this.u = (float) vec.u;
        this.v = (float) vec.v;
        this.r = 255.0f;
        this.g = 255.0f;
        this.b = 255.0f;
    }
}