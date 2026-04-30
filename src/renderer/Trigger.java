package renderer;

public class Trigger {
    public final String id;
    public final CellPos cell;
    public final String targetBarrierId;
    public boolean hasFired;

    public Trigger(String id, CellPos cell, String targetBarrierId) {
        this.id = id;
        this.cell = cell;
        this.targetBarrierId = targetBarrierId;
        this.hasFired = false;
    }
}