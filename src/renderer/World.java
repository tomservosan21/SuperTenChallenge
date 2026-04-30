package renderer;

//================= World.java =================
import java.util.*;
import java.util.Collections;
import java.util.Random;

public class World {
    public final int rows;
    public final int cols;
    public final WorldCell[][] cells;

    public final Map<String, Barrier> barriersById = new LinkedHashMap<>();
    public final Map<String, Trigger> triggersById = new LinkedHashMap<>();

    public final Map<String, Door> doorsById = new LinkedHashMap<>();
    public final Map<String, Key> keysById = new LinkedHashMap<>();

    public final int startRow;
    public final int startCol;
    public final int exitRow;
    public final int exitCol;

    private final Random rng = new Random();

    private static final int[][] DIRS = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };

    public final int[][] regionIndexByCell;

    private World(WorldCell[][] cells, int startRow, int startCol, int exitRow, int exitCol) {
        this.cells = cells;
        this.rows = cells.length;
        this.cols = cells[0].length;
        this.startRow = startRow;
        this.startCol = startCol;
        this.exitRow = exitRow;
        this.exitCol = exitCol;
        this.regionIndexByCell = new int[rows][cols];
    }

    public static World fromMaze(char[][] mazeGrid) {
        return fromMaze(mazeGrid, 2);
    }

    private static World buildBaseWorld(char[][] mazeGrid) {
        int rows = mazeGrid.length;
        int cols = mazeGrid[0].length;

        WorldCell[][] cells = new WorldCell[rows][cols];

        int exitRow = -1;
        int exitCol = -1;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                char ch = mazeGrid[r][c];
                CellType type;
                if (ch == 'X') {
                    type = CellType.WALL;
                } else if (ch == 'E') {
                    type = CellType.EXIT;
                    exitRow = r;
                    exitCol = c;
                } else {
                    type = CellType.CORRIDOR;
                }
                cells[r][c] = new WorldCell(r, c, type);
            }
        }

        CellPos start = findStartCell(cells);
        if (start == null) {
            throw new IllegalStateException("Could not find a start corridor cell.");
        }
        if (exitRow < 0 || exitCol < 0) {
            throw new IllegalStateException("Could not find exit cell.");
        }

        return new World(cells, start.row, start.col, exitRow, exitCol);
    }

    public static World fromMaze(char[][] mazeGrid, int requiredDoorKeyPairs) {
        int rows = mazeGrid.length;
        int cols = mazeGrid[0].length;
        int sizeMetric = Math.min(rows, cols);

        int desiredSpecialPairs = 2 + (int) Math.round((sizeMetric - 21) * (14.0 / 180.0));
        if (desiredSpecialPairs < 2) {
            desiredSpecialPairs = 2;
        }
        if (desiredSpecialPairs > 16) {
            desiredSpecialPairs = 16;
        }

        World world = buildBaseWorld(mazeGrid);

        boolean placedSpecials = world.placeSpecialPairsWithValidation(120, desiredSpecialPairs);
        if (!placedSpecials ||
                world.triggersById.size() != desiredSpecialPairs ||
                world.barriersById.size() != desiredSpecialPairs) {
            throw new IllegalStateException(
                    "Could not place required pressure plate/barrier pairs. Expected " +
                    desiredSpecialPairs +
                    ", got pressure plates=" + world.triggersById.size() +
                    ", barriers=" + world.barriersById.size()
            );
        }

        boolean placedDoors = world.tryPlaceDoorKeyPairs(requiredDoorKeyPairs, 160);

        if (!placedDoors ||
                world.doorsById.size() != requiredDoorKeyPairs ||
                world.keysById.size() != requiredDoorKeyPairs) {
            throw new IllegalStateException(
                    "Could not generate a valid door/key layout with exactly " +
                    requiredDoorKeyPairs + " pairs."
            );
        }

        world.validateDoorKeyIntegrity(requiredDoorKeyPairs);

        if (!world.validateProgression()) {
            throw new IllegalStateException("Generated world failed final progression validation.");
        }

        world.buildRegionMap();
        world.placeLandmarks();
        world.resetRuntimeState();

        return world;
    }

    private void clearDoorKeyPairs() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                cells[r][c].doorId = null;
                cells[r][c].keyId = null;
            }
        }

        doorsById.clear();
        keysById.clear();
    }

    private int manhattan(CellPos a, CellPos b) {
        return Math.abs(a.row - b.row) + Math.abs(a.col - b.col);
    }

    private void validateDoorKeyIntegrity(int expectedPairs) {
        if (doorsById.size() != expectedPairs) {
            throw new IllegalStateException(
                    "Expected " + expectedPairs + " doors, got " + doorsById.size());
        }

        if (keysById.size() != expectedPairs) {
            throw new IllegalStateException(
                    "Expected " + expectedPairs + " keys, got " + keysById.size());
        }

        int[] doorColorCounts = new int[4];
        int[] keyColorCounts = new int[4];

        for (Door door : doorsById.values()) {
            if (door == null) {
                throw new IllegalStateException("Null door in doorsById");
            }

            if (!inBounds(door.cell.row, door.cell.col)) {
                throw new IllegalStateException("Door out of bounds: " + door.id);
            }

            String cellDoorId = cells[door.cell.row][door.cell.col].doorId;
            if (!door.id.equals(cellDoorId)) {
                throw new IllegalStateException("Door/cell mismatch for " + door.id);
            }

            if (door.colorIndex < 0 || door.colorIndex > 3) {
                throw new IllegalStateException("Door has invalid color index: " + door.id);
            }

            doorColorCounts[door.colorIndex]++;
        }

        for (Key key : keysById.values()) {
            if (key == null) {
                throw new IllegalStateException("Null key in keysById");
            }

            if (!inBounds(key.cell.row, key.cell.col)) {
                throw new IllegalStateException("Key out of bounds: " + key.id);
            }

            String cellKeyId = cells[key.cell.row][key.cell.col].keyId;
            if (!key.id.equals(cellKeyId)) {
                throw new IllegalStateException("Key/cell mismatch for " + key.id);
            }

            if (key.colorIndex < 0 || key.colorIndex > 3) {
                throw new IllegalStateException("Key has invalid color index: " + key.id);
            }

            if (cells[key.cell.row][key.cell.col].doorId != null) {
                throw new IllegalStateException("Key shares cell with door: " + key.id);
            }

            if (cells[key.cell.row][key.cell.col].barrierId != null) {
                throw new IllegalStateException("Key shares cell with barrier: " + key.id);
            }

            if (cells[key.cell.row][key.cell.col].triggerId != null) {
                throw new IllegalStateException("Key shares cell with trigger: " + key.id);
            }

            keyColorCounts[key.colorIndex]++;
        }

        for (int color = 0; color < 4; color++) {
            if (keyColorCounts[color] < doorColorCounts[color]) {
                throw new IllegalStateException(
                        "Not enough keys for color " + color +
                        ": doors=" + doorColorCounts[color] +
                        ", keys=" + keyColorCounts[color]);
            }
        }
    }
    
    private boolean tryPlaceDoorKeyPairs(int requiredPairs, int maxAttempts) {
    	for (int attempt = 0; attempt < maxAttempts; attempt++) {
    	clearDoorKeyPairs();
    	placeDoorKeyPairsOnce(requiredPairs);

    	    if (doorsById.size() != requiredPairs || keysById.size() != requiredPairs) {
    	        continue;
    	    }

    	    try {
    	        validateDoorKeyIntegrity(requiredPairs);

    	        // ? Require BOTH checks
    	        if (validateProgression() && runSolvabilityCheck()) {
    	            resetRuntimeState();
    	            return true;
    	        }
    	    } catch (Exception e) {
    	        // retry
    	    }
    	}

    	clearDoorKeyPairs();
    	resetRuntimeState();
    	return false;


    	}


    public int getLandmarkTypeAt(int row, int col) {
        if (!inBounds(row, col)) {
            return 0;
        }
        return cells[row][col].landmarkType;
    }
    
    private CellPos findKeyCellInBranch(CellPos start, Set<CellPos> forbidden) {
    	ArrayDeque<CellPos> queue = new ArrayDeque<>();
    	Set<CellPos> visited = new HashSet<>();

    	queue.add(start);
    	visited.add(start);

    	while (!queue.isEmpty()) {
    	    CellPos cur = queue.poll();

    	    // Skip forbidden cells (main path before door)
    	    if (forbidden.contains(cur)) continue;

    	    WorldCell wc = cells[cur.row][cur.col];

    	    // Valid key placement candidate
    	    if (wc.baseType == CellType.CORRIDOR &&
    	        wc.barrierId == null &&
    	        wc.triggerId == null &&
    	        wc.doorId == null &&
    	        wc.keyId == null &&
    	        !(cur.row == startRow && cur.col == startCol) &&
    	        !(cur.row == exitRow && cur.col == exitCol)) {

    	        return cur;
    	    }

    	    for (int[] d : DIRS) {
    	        int nr = cur.row + d[0];
    	        int nc = cur.col + d[1];

    	        if (!inBounds(nr, nc)) continue;

    	        CellPos next = new CellPos(nr, nc);

    	        if (visited.contains(next)) continue;
    	        if (forbidden.contains(next)) continue;

    	        WorldCell nextCell = cells[nr][nc];

    	        // Only traverse walkable base cells
    	        if (nextCell.baseType != CellType.CORRIDOR) continue;

    	        visited.add(next);
    	        queue.add(next);
    	    }
    	}

    	return null;

    	}

    
    private void placeDoorKeyPairsOnce(int numPairs) {
    	clearDoorKeyPairs();

    	if (numPairs <= 0) {
    	    return;
    	}

    	// Allow up to 16 pairs, reuse 4 colors
    	numPairs = Math.min(numPairs, 16);

    	for (Barrier b : barriersById.values()) {
    	    b.isOpen = true;
    	}

    	CellPos start = new CellPos(startRow, startCol);
    	CellPos exit = new CellPos(exitRow, exitCol);

    	List<CellPos> fullPath = shortestPath(start, exit, Collections.emptySet());

    	for (Barrier b : barriersById.values()) {
    	    b.isOpen = false;
    	}

    	int minRequiredPathLength = Math.max(18, 10 + numPairs * 6);
    	if (fullPath == null || fullPath.size() < minRequiredPathLength) {
    	    return;
    	}

    	for (int pairIndex = 0; pairIndex < numPairs; pairIndex++) {
    	    List<Integer> candidateDoorIndices = new ArrayList<>();

    	    double bandStart = 0.18 + pairIndex * (0.60 / Math.max(1, numPairs));
    	    double bandEnd   = 0.18 + (pairIndex + 1) * (0.60 / Math.max(1, numPairs));

    	    int minDoorIndex = Math.max(6, (int)Math.floor(fullPath.size() * bandStart));
    	    int maxDoorIndex = Math.max(minDoorIndex, (int)Math.floor(fullPath.size() * bandEnd));

    	    maxDoorIndex = Math.min(maxDoorIndex, fullPath.size() - 4);

    	    for (int i = minDoorIndex; i <= maxDoorIndex; i++) {
    	        candidateDoorIndices.add(i);
    	    }

    	    Collections.shuffle(candidateDoorIndices, rng);

    	    boolean placed = false;

    	    for (int doorPathIndex : candidateDoorIndices) {
    	        CellPos doorCell = fullPath.get(doorPathIndex);
    	        WorldCell doorWorldCell = cells[doorCell.row][doorCell.col];

    	        if (doorWorldCell.baseType != CellType.CORRIDOR) continue;
    	        if (doorWorldCell.barrierId != null || doorWorldCell.triggerId != null ||
    	            doorWorldCell.doorId != null || doorWorldCell.keyId != null) continue;
    	        if (doorCell.row == startRow && doorCell.col == startCol) continue;
    	        if (doorCell.row == exitRow && doorCell.col == exitCol) continue;

    	        Set<CellPos> mainRouteBeforeDoor = new HashSet<>();
    	        for (int i = 0; i < doorPathIndex; i++) {
    	            mainRouteBeforeDoor.add(fullPath.get(i));
    	        }

    	        List<CellPos> keyCandidates = new ArrayList<>();

    	        for (int i = 0; i < doorPathIndex; i++) {
    	            CellPos pathCell = fullPath.get(i);

    	            for (int[] d : DIRS) {
    	                int nr = pathCell.row + d[0];
    	                int nc = pathCell.col + d[1];

    	                if (!inBounds(nr, nc)) continue;

    	                CellPos branchStart = new CellPos(nr, nc);

    	                if (mainRouteBeforeDoor.contains(branchStart)) continue;

    	                WorldCell wc = cells[nr][nc];
    	                if (wc.baseType != CellType.CORRIDOR) continue;
    	                if (wc.barrierId != null || wc.triggerId != null ||
    	                    wc.doorId != null || wc.keyId != null) continue;
    	                if (nr == startRow && nc == startCol) continue;
    	                if (nr == exitRow && nc == exitCol) continue;

    	                CellPos keyCell = findKeyCellInBranch(branchStart, mainRouteBeforeDoor);
    	                if (keyCell == null) continue;

    	                WorldCell keyWorldCell = cells[keyCell.row][keyCell.col];
    	                if (keyWorldCell.baseType != CellType.CORRIDOR) continue;
    	                if (keyWorldCell.barrierId != null || keyWorldCell.triggerId != null ||
    	                    keyWorldCell.doorId != null || keyWorldCell.keyId != null) continue;
    	                if (keyCell.row == startRow && keyCell.col == startCol) continue;
    	                if (keyCell.row == exitRow && keyCell.col == exitCol) continue;
    	                if (keyCell.equals(doorCell)) continue;

    	                if (!keyCandidates.contains(keyCell)) {
    	                    keyCandidates.add(keyCell);
    	                }
    	            }
    	        }

    	        keyCandidates.sort((a, b) -> {
    	            boolean aDead = isDeadEndCell(a.row, a.col);
    	            boolean bDead = isDeadEndCell(b.row, b.col);
    	            if (aDead != bDead) return aDead ? -1 : 1;

    	            boolean aTurn = isTurnCell(a.row, a.col);
    	            boolean bTurn = isTurnCell(b.row, b.col);
    	            if (aTurn != bTurn) return aTurn ? -1 : 1;

    	            int aDeg = countBaseWalkableNeighbors(a.row, a.col);
    	            int bDeg = countBaseWalkableNeighbors(b.row, b.col);
    	            return Integer.compare(aDeg, bDeg);
    	        });

    	        if (keyCandidates.isEmpty()) {
    	            continue;
    	        }

    	        CellPos keyCell = keyCandidates.get(0);

    	        String keyId = "key_" + pairIndex;
    	        String doorId = "door_" + pairIndex;

    	        // âœ… FIX: reuse colors
    	        int colorIndex = pairIndex % 4;

    	        Key key = new Key(keyId, keyCell, colorIndex);
    	        Door door = new Door(doorId, doorCell, keyId, colorIndex);

    	        keysById.put(keyId, key);
    	        doorsById.put(doorId, door);

    	        cells[keyCell.row][keyCell.col].keyId = keyId;
    	        cells[doorCell.row][doorCell.col].doorId = doorId;

    	        placed = true;
    	        break;
    	    }

    	    if (!placed) {
    	        return;
    	    }
    	}
    }



    private boolean placeOneSpecialPair(int pairIndex) {
        CellPos start = new CellPos(startRow, startCol);
        CellPos exit = new CellPos(exitRow, exitCol);

        List<CellPos> fullPath = shortestPath(start, exit, Collections.emptySet());
        if (fullPath == null || fullPath.size() < 12) {
            return false;
        }

        List<Integer> candidateBarrierIndices = new ArrayList<>();
        int minBarrierIndex = Math.max(5, fullPath.size() / 3);
        int maxBarrierIndex = Math.max(minBarrierIndex, fullPath.size() - 5);

        for (int i = minBarrierIndex; i <= maxBarrierIndex; i++) {
            CellPos barrierCell = fullPath.get(i);
            if (!isAvailableForBarrier(barrierCell)) {
                continue;
            }
            candidateBarrierIndices.add(i);
        }

        Collections.shuffle(candidateBarrierIndices, rng);

        for (int barrierPathIndex : candidateBarrierIndices) {
            CellPos barrierCell = fullPath.get(barrierPathIndex);
            Set<CellPos> mainPathPrefix = new HashSet<>();
            for (int i = 0; i < barrierPathIndex; i++) {
                mainPathPrefix.add(fullPath.get(i));
            }

            List<CellPos> triggerCandidates = new ArrayList<>();

            for (int i = Math.max(2, barrierPathIndex / 5); i < barrierPathIndex; i++) {
                CellPos pathCell = fullPath.get(i);

                for (int[] d : DIRS) {
                    int nr = pathCell.row + d[0];
                    int nc = pathCell.col + d[1];

                    if (!inBounds(nr, nc)) continue;

                    CellPos branchStart = new CellPos(nr, nc);

                    if (mainPathPrefix.contains(branchStart)) continue;
                    if (!isAvailableForTrigger(branchStart)) continue;

                    CellPos triggerCell = findTriggerCellInBranch(branchStart, mainPathPrefix);

                    if (triggerCell != null) {
                        triggerCandidates.add(triggerCell);
                    }
                }
            }

            if (triggerCandidates.isEmpty()) {
                continue;
            }

            triggerCandidates.sort((a, b) -> {
                boolean aDead = isDeadEndCell(a.row, a.col);
                boolean bDead = isDeadEndCell(b.row, b.col);
                if (aDead != bDead) return aDead ? -1 : 1;

                int aDeg = countBaseWalkableNeighbors(a.row, a.col);
                int bDeg = countBaseWalkableNeighbors(b.row, b.col);
                return Integer.compare(aDeg, bDeg);
            });

            CellPos triggerCell = triggerCandidates.get(0);
            String barrierId = "barrier_" + pairIndex;
            String triggerId = "trigger_" + pairIndex;

            Barrier barrier = new Barrier(barrierId, barrierCell);
            Trigger trigger = new Trigger(triggerId, triggerCell, barrierId);

            barriersById.put(barrierId, barrier);
            triggersById.put(triggerId, trigger);

            cells[barrierCell.row][barrierCell.col].barrierId = barrierId;
            cells[triggerCell.row][triggerCell.col].triggerId = triggerId;

            return true;
        }

        return false;
    }

    private boolean isAvailableForBarrier(CellPos p) {
        if (!inBounds(p.row, p.col)) return false;
        WorldCell wc = cells[p.row][p.col];
        if (wc.baseType != CellType.CORRIDOR) return false;
        if (wc.barrierId != null || wc.triggerId != null || wc.doorId != null || wc.keyId != null) return false;
        if (p.row == startRow && p.col == startCol) return false;
        if (p.row == exitRow && p.col == exitCol) return false;
        return true;
    }

    private boolean isAvailableForTrigger(CellPos p) {
        if (!inBounds(p.row, p.col)) return false;
        WorldCell wc = cells[p.row][p.col];
        if (wc.baseType != CellType.CORRIDOR) return false;
        if (wc.barrierId != null || wc.triggerId != null || wc.doorId != null || wc.keyId != null) return false;
        if (p.row == startRow && p.col == startCol) return false;
        if (p.row == exitRow && p.col == exitCol) return false;
        return true;
    }

    private boolean placeSpecialPairsOnce(int targetPairs) {
        clearSpecialPairs();

        if (targetPairs <= 0) {
            return true;
        }

        for (int pairIndex = 0; pairIndex < targetPairs; pairIndex++) {
            boolean placed = placeOneSpecialPair(pairIndex);
            if (!placed) {
                clearSpecialPairs();
                resetRuntimeState();
                return false;
            }
        }

        return true;
    }

    private boolean placeSpecialPairsWithValidation(int maxAttempts, int targetPairs) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            clearSpecialPairs();
            boolean placed = placeSpecialPairsOnce(targetPairs);
            if (!placed) {
                continue;
            }
            if (validateProgression()) {
                resetRuntimeState();
                return true;
            }
        }

        clearSpecialPairs();
        resetRuntimeState();
        return false;
    }

    private void clearSpecialPairs() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                cells[r][c].barrierId = null;
                cells[r][c].triggerId = null;
                cells[r][c].doorId = null;
                cells[r][c].keyId = null;
            }
        }

        barriersById.clear();
        triggersById.clear();
        doorsById.clear();
        keysById.clear();
    }

    public boolean runSolvabilityCheck() {
        resetRuntimeState();

        CellPos start = new CellPos(startRow, startCol);

        Map<Integer, Integer> inventory = new HashMap<>();
        boolean changed;

        do {
            changed = false;

            Set<CellPos> reachable = reachableWithLiveState(start);

            if (reachable.contains(new CellPos(exitRow, exitCol))) {
                resetRuntimeState();
                return true;
            }

            for (Trigger t : triggersById.values()) {
                if (!t.hasFired && reachable.contains(t.cell)) {
                    t.hasFired = true;

                    Barrier b = barriersById.get(t.targetBarrierId);
                    if (b != null) {
                        b.isOpen = true;
                    }

                    changed = true;
                }
            }

            reachable = reachableWithLiveState(start);

            if (reachable.contains(new CellPos(exitRow, exitCol))) {
                resetRuntimeState();
                return true;
            }

            for (Key k : keysById.values()) {
                if (!k.isCollected && reachable.contains(k.cell)) {
                    k.isCollected = true;
                    inventory.merge(k.colorIndex, 1, Integer::sum);
                    changed = true;
                }
            }

            reachable = reachableWithLiveState(start);

            if (reachable.contains(new CellPos(exitRow, exitCol))) {
                resetRuntimeState();
                return true;
            }

            for (Door d : doorsById.values()) {
                if (d.isUnlocked) {
                    continue;
                }

                int count = inventory.getOrDefault(d.colorIndex, 0);
                if (count <= 0) {
                    continue;
                }

                if (isDoorInteractableFromReachableCell(d, reachable)) {
                    d.isUnlocked = true;

                    if (count == 1) {
                        inventory.remove(d.colorIndex);
                    } else {
                        inventory.put(d.colorIndex, count - 1);
                    }

                    changed = true;
                }
            }

        } while (changed);

        Set<CellPos> finalReachable = reachableWithLiveState(start);
        boolean ok = finalReachable.contains(new CellPos(exitRow, exitCol));

        resetRuntimeState();
        return ok;
    }

    private boolean isDoorInteractableFromReachableCell(Door door, Set<CellPos> reachable) {
        int row = door.cell.row;
        int col = door.cell.col;

        for (int[] d : DIRS) {
            int nr = row + d[0];
            int nc = col + d[1];

            CellPos adj = new CellPos(nr, nc);
            if (reachable.contains(adj)) {
                return true;
            }
        }

        return false;
    }

    private Set<CellPos> reachableWithLiveState(CellPos start) {
        Set<CellPos> visited = new HashSet<>();
        ArrayDeque<CellPos> queue = new ArrayDeque<>();

        if (!inBounds(start.row, start.col) || isSolid(start.row, start.col)) {
            return visited;
        }

        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            CellPos cur = queue.removeFirst();

            for (int[] d : DIRS) {
                int nr = cur.row + d[0];
                int nc = cur.col + d[1];

                CellPos next = new CellPos(nr, nc);

                if (!inBounds(nr, nc)) continue;
                if (visited.contains(next)) continue;
                if (isSolid(nr, nc)) continue;

                visited.add(next);
                queue.addLast(next);
            }
        }

        return visited;
    }

    public Door getAdjacentLockedDoor(int row, int col) {
        for (int[] d : DIRS) {
            int nr = row + d[0];
            int nc = col + d[1];

            Door door = getDoorAt(nr, nc);
            if (door != null && !door.isUnlocked) {
                return door;
            }
        }
        return null;
    }

    private boolean[][] reachableWithState(CellPos start, Set<String> unlockedDoors) {
        boolean[][] visited = new boolean[rows][cols];
        ArrayDeque<CellPos> queue = new ArrayDeque<>();

        if (!inBounds(start.row, start.col) || !isBaseWalkable(start.row, start.col)) {
            return visited;
        }

        queue.add(start);
        visited[start.row][start.col] = true;

        while (!queue.isEmpty()) {
            CellPos cur = queue.poll();

            for (int[] d : DIRS) {
                int nr = cur.row + d[0];
                int nc = cur.col + d[1];

                if (!inBounds(nr, nc)) continue;
                if (visited[nr][nc]) continue;
                if (isBlockedForValidation(nr, nc, unlockedDoors)) continue;

                visited[nr][nc] = true;
                queue.add(new CellPos(nr, nc));
            }
        }

        return visited;
    }

    private boolean isBlockedForValidation(int row, int col, Set<String> unlockedDoors) {
        if (!inBounds(row, col)) {
            return true;
        }

        if (cells[row][col].baseType == CellType.WALL) {
            return true;
        }

        String barrierId = cells[row][col].barrierId;
        if (barrierId != null) {
            Barrier b = barriersById.get(barrierId);
            if (b != null && !b.isOpen) {
                return true;
            }
        }

        String doorId = cells[row][col].doorId;
        if (doorId != null && !unlockedDoors.contains(doorId)) {
            return true;
        }

        return false;
    }

    private boolean validateProgression() {
        List<Trigger> orderedTriggers = getTriggersInProgressionOrder();
        List<Barrier> orderedBarriers = getBarriersInProgressionOrder();

        if (orderedTriggers.size() != orderedBarriers.size()) {
            return false;
        }

        CellPos start = new CellPos(startRow, startCol);
        CellPos exit = new CellPos(exitRow, exitCol);

        resetRuntimeState();

        Set<String> unlockedDoors = new HashSet<>();
        boolean[][] reachableWithDoors = reachableWithState(start, unlockedDoors);

        if ((!orderedBarriers.isEmpty() || !doorsById.isEmpty()) &&
                reachableWithDoors[exit.row][exit.col]) {
            resetRuntimeState();
            return false;
        }

        if (!orderedTriggers.isEmpty()) {
            Trigger first = orderedTriggers.get(0);
            Set<CellPos> reachable = reachableWithCurrentBarrierState(start);
            if (!reachable.contains(first.cell)) {
                resetRuntimeState();
                return false;
            }
        }

        for (int i = 0; i < orderedTriggers.size(); i++) {
            Trigger t = orderedTriggers.get(i);
            Barrier b = barriersById.get(t.targetBarrierId);

            if (b == null) {
                resetRuntimeState();
                return false;
            }

            t.hasFired = true;
            b.isOpen = true;

            Set<CellPos> reachable = reachableWithCurrentBarrierState(start);

            boolean isLast = (i == orderedTriggers.size() - 1);

            if (!isLast) {
                Trigger next = orderedTriggers.get(i + 1);
                if (!reachable.contains(next.cell)) {
                    resetRuntimeState();
                    return false;
                }

                if (reachable.contains(exit)) {
                    resetRuntimeState();
                    return false;
                }
            }
        }

        Map<Integer, Integer> collectedKeyCounts = new HashMap<>();
        List<Door> orderedDoors = new ArrayList<>(doorsById.values());

        for (Door door : orderedDoors) {
            reachableWithDoors = reachableWithState(start, unlockedDoors);

            for (Key key : keysById.values()) {
                if (!key.isCollected && reachableWithDoors[key.cell.row][key.cell.col]) {
                    key.isCollected = true;
                    collectedKeyCounts.merge(key.colorIndex, 1, Integer::sum);
                }
            }

            reachableWithDoors = reachableWithState(start, unlockedDoors);
            if (!isDoorInteractableFromReachableState(door, reachableWithDoors)) {
                resetRuntimeState();
                return false;
            }

            int count = collectedKeyCounts.getOrDefault(door.colorIndex, 0);
            if (count <= 0) {
                resetRuntimeState();
                return false;
            }

            if (count == 1) {
                collectedKeyCounts.remove(door.colorIndex);
            } else {
                collectedKeyCounts.put(door.colorIndex, count - 1);
            }

            unlockedDoors.add(door.id);
        }

        reachableWithDoors = reachableWithState(start, unlockedDoors);
        if (!reachableWithDoors[exit.row][exit.col]) {
            resetRuntimeState();
            return false;
        }

        resetRuntimeState();
        return true;
    }

    private boolean isDoorInteractableFromReachableState(Door door, boolean[][] reachable) {
        int row = door.cell.row;
        int col = door.cell.col;

        for (int[] d : DIRS) {
            int nr = row + d[0];
            int nc = col + d[1];

            if (!inBounds(nr, nc)) {
                continue;
            }

            if (reachable[nr][nc]) {
                return true;
            }
        }

        return false;
    }

    private List<Trigger> getTriggersInProgressionOrder() {
        List<Barrier> orderedBarriers = getBarriersInProgressionOrder();
        List<Trigger> orderedTriggers = new ArrayList<>();

        for (Barrier b : orderedBarriers) {
            Trigger matched = null;
            for (Trigger t : triggersById.values()) {
                if (b.id.equals(t.targetBarrierId)) {
                    matched = t;
                    break;
                }
            }

            if (matched == null) {
                return Collections.emptyList();
            }

            orderedTriggers.add(matched);
        }

        return orderedTriggers;
    }

    private List<Barrier> getBarriersInProgressionOrder() {
        Map<CellPos, Integer> dist = reachableDistancesIgnoringBarriers(new CellPos(startRow, startCol));

        List<Barrier> ordered = new ArrayList<>(barriersById.values());
        ordered.sort(Comparator.comparingInt(b -> dist.getOrDefault(b.cell, Integer.MAX_VALUE)));

        return ordered;
    }

    private Set<CellPos> reachableWithCurrentBarrierState(CellPos start) {
        Set<CellPos> reachable = new HashSet<>();
        ArrayDeque<CellPos> q = new ArrayDeque<>();

        if (!inBounds(start.row, start.col) || isSolid(start.row, start.col)) {
            return reachable;
        }

        reachable.add(start);
        q.add(start);

        while (!q.isEmpty()) {
            CellPos cur = q.removeFirst();

            for (int[] d : DIRS) {
                int nr = cur.row + d[0];
                int nc = cur.col + d[1];
                CellPos next = new CellPos(nr, nc);

                if (!inBounds(nr, nc)) continue;
                if (isSolid(nr, nc)) continue;
                if (reachable.contains(next)) continue;

                reachable.add(next);
                q.addLast(next);
            }
        }

        return reachable;
    }

    private Map<CellPos, Integer> reachableDistancesIgnoringBarriers(CellPos start) {
        Map<CellPos, Integer> dist = new HashMap<>();
        ArrayDeque<CellPos> q = new ArrayDeque<>();

        if (!inBounds(start.row, start.col) || !isBaseWalkable(start.row, start.col)) {
            return dist;
        }

        dist.put(start, 0);
        q.add(start);

        while (!q.isEmpty()) {
            CellPos cur = q.removeFirst();
            int cd = dist.get(cur);

            for (int[] d : DIRS) {
                int nr = cur.row + d[0];
                int nc = cur.col + d[1];
                CellPos next = new CellPos(nr, nc);

                if (!inBounds(nr, nc)) continue;
                if (!isBaseWalkable(nr, nc)) continue;
                if (dist.containsKey(next)) continue;

                dist.put(next, cd + 1);
                q.addLast(next);
            }
        }

        return dist;
    }

    private static CellPos findStartCell(WorldCell[][] cells) {
        for (int r = 1; r < cells.length - 1; r++) {
            for (int c = 1; c < cells[0].length - 1; c++) {
                if (cells[r][c].baseType == CellType.CORRIDOR) {
                    return new CellPos(r, c);
                }
            }
        }
        return null;
    }

    public boolean inBounds(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }

    public boolean isBaseWalkable(int row, int col) {
        if (!inBounds(row, col))
            return false;
        CellType type = cells[row][col].baseType;
        return type == CellType.CORRIDOR || type == CellType.EXIT;
    }

    public boolean isSolid(int row, int col) {
        if (!inBounds(row, col))
            return false;

        if (cells[row][col].baseType == CellType.WALL) {
            return true;
        }

        String barrierId = cells[row][col].barrierId;
        if (barrierId != null) {
            Barrier b = barriersById.get(barrierId);
            if (b != null && !b.isOpen) {
                return true;
            }
        }

        String doorId = cells[row][col].doorId;
        if (doorId != null) {
            Door d = doorsById.get(doorId);
            if (d != null && !d.isUnlocked) {
                return true;
            }
        }

        return false;
    }

    public boolean isExit(int row, int col) {
        return inBounds(row, col) && cells[row][col].baseType == CellType.EXIT;
    }

    public Trigger getTriggerAt(int row, int col) {
        if (!inBounds(row, col))
            return null;
        String triggerId = cells[row][col].triggerId;
        if (triggerId == null)
            return null;
        return triggersById.get(triggerId);
    }

    public boolean activateTriggerAt(int row, int col) {
        Trigger t = getTriggerAt(row, col);
        if (t == null || t.hasFired) {
            return false;
        }

        t.hasFired = true;

        Barrier b = barriersById.get(t.targetBarrierId);
        if (b != null) {
            b.isOpen = true;
        }

        return true;
    }

    public Door getDoorAt(int row, int col) {
        if (!inBounds(row, col))
            return null;

        String doorId = cells[row][col].doorId;
        if (doorId == null)
            return null;

        return doorsById.get(doorId);
    }

    public Key getKeyAt(int row, int col) {
        if (!inBounds(row, col))
            return null;

        String keyId = cells[row][col].keyId;
        if (keyId == null)
            return null;

        return keysById.get(keyId);
    }

    public boolean collectKeyAt(int row, int col) {
        Key k = getKeyAt(row, col);
        if (k == null || k.isCollected) {
            return false;
        }

        k.isCollected = true;
        return true;
    }

    public boolean unlockDoor(String doorId) {
        Door d = doorsById.get(doorId);
        if (d == null || d.isUnlocked) {
            return false;
        }

        d.isUnlocked = true;
        return true;
    }

    public boolean unlockDoorAt(int row, int col) {
        Door d = getDoorAt(row, col);
        if (d == null || d.isUnlocked) {
            return false;
        }

        d.isUnlocked = true;
        return true;
    }

    public boolean hasLockedDoor(int row, int col) {
        Door d = getDoorAt(row, col);
        return d != null && !d.isUnlocked;
    }

    public boolean hasCollectedKeyBeenRemoved(int row, int col) {
        Key k = getKeyAt(row, col);
        return k != null && k.isCollected;
    }

    public int getRegionIndex(int row, int col) {
        if (!inBounds(row, col)) {
            return 0;
        }

        int region = regionIndexByCell[row][col];
        if (region >= 0) {
            return region;
        }

        int[] counts = new int[4];

        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;

                int nr = row + dr;
                int nc = col + dc;

                if (!inBounds(nr, nc)) continue;
                if (!isBaseWalkable(nr, nc)) continue;

                int neighborRegion = regionIndexByCell[nr][nc];
                if (neighborRegion >= 0 && neighborRegion < counts.length) {
                    counts[neighborRegion]++;
                }
            }
        }

        int bestRegion = 0;
        int bestCount = -1;

        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > bestCount) {
                bestCount = counts[i];
                bestRegion = i;
            }
        }

        return bestRegion;
    }

    public void buildRegionMap() {
        int sizeMetric = Math.min(rows, cols);
        int logicalRegionCount = 4 + (int) Math.round((sizeMetric - 21) * (12.0 / 180.0));
        logicalRegionCount = clamp(4, logicalRegionCount, 16);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                regionIndexByCell[r][c] = -1;
            }
        }

        CellPos start = new CellPos(startRow, startCol);
        CellPos exit = new CellPos(exitRow, exitCol);

        List<CellPos> mainPath = shortestPath(start, exit, Collections.emptySet());

        if (mainPath == null || mainPath.isEmpty()) {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    regionIndexByCell[r][c] = 0;
                }
            }
            return;
        }

        List<CellPos> seeds = new ArrayList<>();
        Set<CellPos> used = new HashSet<>();

        for (int i = 0; i < logicalRegionCount; i++) {
            double t = (logicalRegionCount == 1)
                    ? 0.0
                    : (double) i / (double) (logicalRegionCount - 1);

            int idx = (int) Math.round(t * (mainPath.size() - 1));
            idx = clamp(0, idx, mainPath.size() - 1);

            CellPos seed = mainPath.get(idx);

            if (used.contains(seed)) {
                int left = idx - 1;
                int right = idx + 1;
                CellPos replacement = null;

                while (left >= 0 || right < mainPath.size()) {
                    if (left >= 0) {
                        CellPos candidate = mainPath.get(left);
                        if (!used.contains(candidate)) {
                            replacement = candidate;
                            break;
                        }
                        left--;
                    }
                    if (right < mainPath.size()) {
                        CellPos candidate = mainPath.get(right);
                        if (!used.contains(candidate)) {
                            replacement = candidate;
                            break;
                        }
                        right++;
                    }
                }

                if (replacement != null) {
                    seed = replacement;
                }
            }

            seeds.add(seed);
            used.add(seed);
        }

        ArrayDeque<CellPos> q = new ArrayDeque<>();
        for (int i = 0; i < seeds.size(); i++) {
            CellPos seed = seeds.get(i);
            regionIndexByCell[seed.row][seed.col] = i;
            q.add(seed);
        }

        while (!q.isEmpty()) {
            CellPos cur = q.removeFirst();
            int region = regionIndexByCell[cur.row][cur.col];

            for (int[] d : DIRS) {
                int nr = cur.row + d[0];
                int nc = cur.col + d[1];

                if (!inBounds(nr, nc)) continue;
                if (!isBaseWalkable(nr, nc)) continue;
                if (regionIndexByCell[nr][nc] >= 0) continue;

                regionIndexByCell[nr][nc] = region;
                q.addLast(new CellPos(nr, nc));
            }
        }

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (isBaseWalkable(r, c) && regionIndexByCell[r][c] < 0) {
                    regionIndexByCell[r][c] = 0;
                }
            }
        }

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (isBaseWalkable(r, c)) {
                    continue;
                }

                Map<Integer, Integer> counts = new HashMap<>();

                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if (dr == 0 && dc == 0) continue;

                        int nr = r + dr;
                        int nc = c + dc;

                        if (!inBounds(nr, nc)) continue;
                        if (!isBaseWalkable(nr, nc)) continue;

                        int region = regionIndexByCell[nr][nc];
                        if (region < 0) continue;

                        counts.put(region, counts.getOrDefault(region, 0) + 1);
                    }
                }

                if (counts.isEmpty()) {
                    regionIndexByCell[r][c] = 0;
                } else {
                    int bestRegion = 0;
                    int bestCount = -1;

                    for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
                        if (e.getValue() > bestCount) {
                            bestCount = e.getValue();
                            bestRegion = e.getKey();
                        }
                    }

                    regionIndexByCell[r][c] = bestRegion;
                }
            }
        }
    }

    public boolean hasClosedBarrier(int row, int col) {
        if (!inBounds(row, col))
            return false;
        String barrierId = cells[row][col].barrierId;
        if (barrierId == null)
            return false;
        Barrier b = barriersById.get(barrierId);
        return b != null && !b.isOpen;
    }

    public boolean hasTrigger(int row, int col) {
        return inBounds(row, col) && cells[row][col].triggerId != null;
    }

    private boolean isStraightCorridor(int row, int col) {
        if (!inBounds(row, col) || !isBaseWalkable(row, col)) {
            return false;
        }

        boolean up = inBounds(row - 1, col) && isBaseWalkable(row - 1, col);
        boolean down = inBounds(row + 1, col) && isBaseWalkable(row + 1, col);
        boolean left = inBounds(row, col - 1) && isBaseWalkable(row, col - 1);
        boolean right = inBounds(row, col + 1) && isBaseWalkable(row, col + 1);

        return (up && down && !left && !right) ||
               (!up && !down && left && right);
    }

    private boolean isTurnCell(int row, int col) {
        if (!inBounds(row, col) || !isBaseWalkable(row, col)) {
            return false;
        }

        return countBaseWalkableNeighbors(row, col) == 2 && !isStraightCorridor(row, col);
    }

    private boolean isJunctionCell(int row, int col) {
        if (!inBounds(row, col) || !isBaseWalkable(row, col)) {
            return false;
        }

        return countBaseWalkableNeighbors(row, col) >= 3;
    }

    private boolean isDeadEndCell(int row, int col) {
        if (!inBounds(row, col) || !isBaseWalkable(row, col)) {
            return false;
        }

        return countBaseWalkableNeighbors(row, col) <= 1;
    }

    private boolean isNearBarrier(int row, int col) {
        for (int[] d : DIRS) {
            int nr = row + d[0];
            int nc = col + d[1];

            if (!inBounds(nr, nc)) {
                continue;
            }

            if (cells[nr][nc].barrierId != null) {
                return true;
            }
        }

        return false;
    }

    private List<CellPos> shortestPath(CellPos start, CellPos goal, Set<CellPos> blockedCells) {
        Map<CellPos, CellPos> prev = new HashMap<>();
        ArrayDeque<CellPos> q = new ArrayDeque<>();
        Set<CellPos> seen = new HashSet<>();

        q.add(start);
        seen.add(start);

        while (!q.isEmpty()) {
            CellPos cur = q.removeFirst();
            if (cur.equals(goal)) {
                break;
            }

            for (int[] d : DIRS) {
                int nr = cur.row + d[0];
                int nc = cur.col + d[1];
                CellPos next = new CellPos(nr, nc);

                if (!inBounds(nr, nc))
                    continue;
                if (!isBaseWalkable(nr, nc))
                    continue;
                if (blockedCells.contains(next))
                    continue;
                if (seen.contains(next))
                    continue;

                seen.add(next);
                prev.put(next, cur);
                q.addLast(next);
            }
        }

        if (!seen.contains(goal)) {
            return Collections.emptyList();
        }

        LinkedList<CellPos> path = new LinkedList<>();
        CellPos cur = goal;
        while (cur != null) {
            path.addFirst(cur);
            cur = prev.get(cur);
        }

        return path;
    }

    private static int clamp(int lo, int value, int hi) {
        return Math.max(lo, Math.min(value, hi));
    }

    public void resetRuntimeState() {
        for (Barrier b : barriersById.values()) {
            b.isOpen = false;
        }

        for (Trigger t : triggersById.values()) {
            t.hasFired = false;
        }

        for (Door d : doorsById.values()) {
            d.isUnlocked = false;
        }

        for (Key k : keysById.values()) {
            k.isCollected = false;
        }
    }

    private CellPos findTriggerCellInBranch(CellPos start, Set<CellPos> forbidden) {
        ArrayDeque<CellPos> queue = new ArrayDeque<>();
        Set<CellPos> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        CellPos best = null;
        int bestScore = Integer.MIN_VALUE;

        while (!queue.isEmpty()) {
            CellPos cur = queue.removeFirst();

            if (!inBounds(cur.row, cur.col)) continue;
            if (forbidden.contains(cur)) continue;

            WorldCell cell = cells[cur.row][cur.col];

            if (cell.baseType != CellType.CORRIDOR) continue;
            if (cell.barrierId != null || cell.triggerId != null ||
                cell.doorId != null || cell.keyId != null) continue;
            if (cur.row == startRow && cur.col == startCol) continue;
            if (cur.row == exitRow && cur.col == exitCol) continue;

            int neighbors = countBaseWalkableNeighbors(cur.row, cur.col);
            boolean deadEnd = (neighbors <= 1);

            int score = 0;
            if (deadEnd) score += 1000;
            score += (10 - neighbors) * 10;
            score += manhattan(start, cur);

            if (score > bestScore) {
                bestScore = score;
                best = cur;
            }

            for (int[] d : DIRS) {
                int nr = cur.row + d[0];
                int nc = cur.col + d[1];
                CellPos next = new CellPos(nr, nc);

                if (!inBounds(nr, nc)) continue;
                if (visited.contains(next)) continue;
                if (forbidden.contains(next)) continue;

                visited.add(next);
                queue.addLast(next);
            }
        }

        return best;
    }

    public int getAdjacentLandmarkType(int row, int col) {
        if (!inBounds(row, col)) {
            return 0;
        }

        int[] counts = new int[5];

        for (int[] d : DIRS) {
            int nr = row + d[0];
            int nc = col + d[1];

            if (!inBounds(nr, nc)) continue;

            int type = cells[nr][nc].landmarkType;
            if (type >= 1 && type <= 4) {
                counts[type]++;
            }
        }

        int bestType = 0;
        int bestCount = 0;

        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > bestCount) {
                bestCount = counts[i];
                bestType = i;
            }
        }

        return bestType;
    }

    private void clearLandmarks() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                cells[r][c].landmarkType = 0;
            }
        }
    }

    private boolean isLandmarkEligible(int row, int col) {
        if (!inBounds(row, col)) return false;
        if (!isBaseWalkable(row, col)) return false;
        if (row == startRow && col == startCol) return false;
        if (row == exitRow && col == exitCol) return false;
        if (cells[row][col].triggerId != null) return false;
        if (cells[row][col].barrierId != null) return false;
        if (cells[row][col].landmarkType != 0) return false;

        int distFromStart = Math.abs(row - startRow) + Math.abs(col - startCol);
        int distFromExit = Math.abs(row - exitRow) + Math.abs(col - exitCol);

        if (distFromStart < 6) return false;
        if (distFromExit < 6) return false;

        return true;
    }

    private int countBaseWalkableNeighbors(int row, int col) {
        int count = 0;
        for (int[] d : DIRS) {
            int nr = row + d[0];
            int nc = col + d[1];
            if (inBounds(nr, nc) && isBaseWalkable(nr, nc)) {
                count++;
            }
        }
        return count;
    }

    private void placeLandmarks() {
        clearLandmarks();

        int walkableCount = 0;
        List<CellPos> candidates = new ArrayList<>();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!isBaseWalkable(r, c)) {
                    continue;
                }

                walkableCount++;

                if (!isLandmarkEligible(r, c)) {
                    continue;
                }

                boolean turn = isTurnCell(r, c);
                boolean junction = isJunctionCell(r, c);
                boolean deadEnd = isDeadEndCell(r, c);
                boolean nearBarrier = isNearBarrier(r, c);

                if (turn || junction || deadEnd || nearBarrier) {
                    candidates.add(new CellPos(r, c));
                }
            }
        }

        if (candidates.isEmpty()) {
            return;
        }

        int logicalRegionCount = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                logicalRegionCount = Math.max(logicalRegionCount, regionIndexByCell[r][c] + 1);
            }
        }
        logicalRegionCount = Math.max(4, logicalRegionCount);

        int landmarkBudget = Math.max(4, Math.min(16, walkableCount / 40));
        Collections.shuffle(candidates, rng);

        int placed = 0;
        for (CellPos p : candidates) {
            if (placed >= landmarkBudget) {
                break;
            }

            if (cells[p.row][p.col].landmarkType != 0) {
                continue;
            }

            int type = (getRegionIndex(p.row, p.col) % 4) + 1;
            cells[p.row][p.col].landmarkType = type;
            placed++;
        }
    }
}
