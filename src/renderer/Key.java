package renderer;

public class Key {
    public final String id;
    public final CellPos cell;
    public final int colorIndex;   // must match its door's color

    public boolean isCollected;

    public Key(String id, CellPos cell, int colorIndex) {
        this.id = id;
        this.cell = cell;
        this.colorIndex = colorIndex;
        this.isCollected = false;
    }
}