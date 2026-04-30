package renderer;

public class Door {
    public final String id;
    public final CellPos cell;
    public final String requiredKeyId;
    public final int colorIndex;   // 0=red, 1=blue, 2=green, 3=yellow, etc.

    public boolean isUnlocked;

    public Door(String id, CellPos cell, String requiredKeyId, int colorIndex) {
        this.id = id;
        this.cell = cell;
        this.requiredKeyId = requiredKeyId;
        this.colorIndex = colorIndex;
        this.isUnlocked = false;
    }
}