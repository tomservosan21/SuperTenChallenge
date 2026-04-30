package renderer;

import java.util.Objects;

public class CellPos {
    public final int row;
    public final int col;

    public CellPos(int row, int col) {
        this.row = row;
        this.col = col;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CellPos)) return false;
        CellPos other = (CellPos) o;
        return row == other.row && col == other.col;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col);
    }
}