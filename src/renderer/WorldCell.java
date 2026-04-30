package renderer;

public class WorldCell {
    public final int row;
    public final int col;
    public final CellType baseType;

    public String barrierId;
    public String triggerId;

    public String doorId;
    public String keyId;

    public int landmarkType; // 0 = none, 1..4 = landmark families

    public WorldCell(int row, int col, CellType baseType) {
        this.row = row;
        this.col = col;
        this.baseType = baseType;

        this.barrierId = null;
        this.triggerId = null;
        this.doorId = null;
        this.keyId = null;

        this.landmarkType = 0;
    }
}