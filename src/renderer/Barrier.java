package renderer;

public class Barrier {
    public final String id;
    public final CellPos cell;
    public boolean isOpen;

    public Barrier(String id, CellPos cell) {
        this.id = id;
        this.cell = cell;
        this.isOpen = false;
    }
}