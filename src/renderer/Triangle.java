package renderer;

import java.awt.image.BufferedImage;
import java.util.Random;

public class Triangle {
    public Vertex v0, v1, v2;

    int red;
    int green;
    int blue;

    int lightingRed;
    int lightingGreen;
    int lightingBlue;

    Vector3 normal;

    BufferedImage texture;

    // Dynamic render metadata
    int faceType = -1;
    int gridRow = -1;
    int gridCol = -1;

    Vector3 getCenter() {
        return new Vector3(
            (v0.pos.x + v1.pos.x + v2.pos.x) / 3.0,
            (v0.pos.y + v1.pos.y + v2.pos.y) / 3.0,
            (v0.pos.z + v1.pos.z + v2.pos.z) / 3.0
        );
    }

    Random random = new Random();

    public Triangle(Vertex v0, Vertex v1, Vertex v2,
                    int red, int green, int blue,
                    int lightingRed, int lightingGreen, int lightingBlue) {
        this(v0, v1, v2, red, green, blue, lightingRed, lightingGreen, lightingBlue, null);
    }

    public Triangle(Vertex v0, Vertex v1, Vertex v2,
                    int red, int green, int blue,
                    int lightingRed, int lightingGreen, int lightingBlue,
                    BufferedImage texture) {
        this.v0 = v0;
        this.v1 = v1;
        this.v2 = v2;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.lightingRed = lightingRed;
        this.lightingGreen = lightingGreen;
        this.lightingBlue = lightingBlue;
        this.texture = texture;
    }

    public void setGridMetadata(int faceType, int row, int col) {
        this.faceType = faceType;
        this.gridRow = row;
        this.gridCol = col;
    }

    public void copyGridMetadataFrom(Triangle other) {
        this.faceType = other.faceType;
        this.gridRow = other.gridRow;
        this.gridCol = other.gridCol;
    }
}