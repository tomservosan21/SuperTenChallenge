package renderer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class SceneGenerator {
    static public double[][] pts;
    static public int[][] multiCubeFaces;
    static public int numMultiCubeFaces;
    static public int[][][] indexes;
    static public char[][] mazeGrid;
    static public World world;

    public static int CURRENT_SIZE = 21;
    static final int MAX_WORLD_BUILD_ATTEMPTS = 100;

    static SceneGenerator sg;

    private static final int FACE_FLOOR = 6;
    private static final int FACE_CEILING = 7;
    private static final int FACE_EXIT = 8;
    private static final int FACE_PLATE = 9;
    private static final int FACE_SPECIAL_WALL = 10;

    private static final int FACE_DOOR_RED = 11;
    private static final int FACE_DOOR_BLUE = 12;
    private static final int FACE_DOOR_GREEN = 13;
    private static final int FACE_DOOR_YELLOW = 14;

    private static final int FACE_KEY_RED = 15;
    private static final int FACE_KEY_BLUE = 16;
    private static final int FACE_KEY_GREEN = 17;
    private static final int FACE_KEY_YELLOW = 18;
    private static final int FACE_KEY_SKELETON = 19;
    private static final int FACE_BONUS = 20;

    private static final ArrayList<int[]> skeletonKeyCells = new ArrayList<>();
    private static final ArrayList<Boolean> skeletonKeyCollected = new ArrayList<>();

    private static final ArrayList<int[]> bonusNodeCells = new ArrayList<>();
    private static final ArrayList<Boolean> bonusNodeCollected = new ArrayList<>();

    private static final ArrayList<int[]> dotCells = new ArrayList<>();
    private static final ArrayList<Boolean> dotCollected = new ArrayList<>();

    public SceneGenerator() {
        initializeWorldWithRetries();

        final double CELL = 4.0;
        final double FLOOR_Z = 0.0;
        final double CEIL_Z = 4.0;

        int rows = mazeGrid.length;
        int cols = mazeGrid[0].length;

        indexes = new int[rows + 1][cols + 1][2];
        pts = new double[(rows + 1) * (cols + 1) * 2][3];

        int p = 0;
        for (int i = 0; i <= rows; i++) {
            for (int j = 0; j <= cols; j++) {
                pts[p] = new double[] { i * CELL, j * CELL, FLOOR_Z };
                indexes[i][j][0] = p++;
                pts[p] = new double[] { i * CELL, j * CELL, CEIL_Z };
                indexes[i][j][1] = p++;
            }
        }

        rebuildFacesFromWorld();
    }

    public static void configureForLevel(int level) {
        switch (level) {
            case 1: CURRENT_SIZE = 21; break;
            case 2: CURRENT_SIZE = 21; break;
            case 3: CURRENT_SIZE = 31; break;
            case 4: CURRENT_SIZE = 31; break;
            case 5: CURRENT_SIZE = 41; break;
            case 6: CURRENT_SIZE = 51; break;
            case 7: CURRENT_SIZE = 61; break;
            case 8: CURRENT_SIZE = 81; break;
            case 9: CURRENT_SIZE = 101; break;
            case 10: CURRENT_SIZE = 121; break;
            default: CURRENT_SIZE = 121; break;
        }
    }

    public static void generateCurrentLevelWorld() {
        sg = new SceneGenerator();
    }

    public static int getSkeletonKeySpawnCountForCurrentLevel() {
        return CURRENT_SIZE >= 101 ? 2 : 1;
    }

    public static int getBonusNodeSpawnCountForCurrentLevel() {
        if (CURRENT_SIZE <= 31) {
            return 1;
        }
        if (CURRENT_SIZE <= 61) {
            return 2;
        }
        return 3;
    }

    public static void resetSkeletonKeysForLevel() {
        for (int i = 0; i < skeletonKeyCollected.size(); i++) {
            skeletonKeyCollected.set(i, false);
        }
    }

    public static void resetBonusNodesForLevel() {
        for (int i = 0; i < bonusNodeCollected.size(); i++) {
            bonusNodeCollected.set(i, false);
        }
    }

    public static void resetDotsForLevel() {
        for (int i = 0; i < dotCollected.size(); i++) {
            dotCollected.set(i, false);
        }
    }

    public static int getTotalDotCount() {
        return dotCells.size();
    }

    public static int getCollectedDotCount() {
        int count = 0;
        for (boolean collected : dotCollected) {
            if (collected) {
                count++;
            }
        }
        return count;
    }

    public static int getRemainingDotCount() {
        return getTotalDotCount() - getCollectedDotCount();
    }

    public static boolean areAllDotsCollected() {
        return getRemainingDotCount() <= 0;
    }

    public static boolean hasVisibleDotAt(int row, int col) {
        for (int i = 0; i < dotCells.size(); i++) {
            int[] cell = dotCells.get(i);
            if (!dotCollected.get(i) && cell[0] == row && cell[1] == col) {
                return true;
            }
        }
        return false;
    }

    public static boolean collectDotAt(int row, int col) {
        for (int i = 0; i < dotCells.size(); i++) {
            int[] cell = dotCells.get(i);
            if (!dotCollected.get(i) && cell[0] == row && cell[1] == col) {
                dotCollected.set(i, true);
                return true;
            }
        }
        return false;
    }

    public static boolean hasVisibleSkeletonKeyAt(int row, int col) {
        for (int i = 0; i < skeletonKeyCells.size(); i++) {
            int[] cell = skeletonKeyCells.get(i);
            if (!skeletonKeyCollected.get(i) && cell[0] == row && cell[1] == col) {
                return true;
            }
        }
        return false;
    }

    public static boolean collectSkeletonKeyAt(int row, int col) {
        for (int i = 0; i < skeletonKeyCells.size(); i++) {
            int[] cell = skeletonKeyCells.get(i);
            if (!skeletonKeyCollected.get(i) && cell[0] == row && cell[1] == col) {
                skeletonKeyCollected.set(i, true);
                return true;
            }
        }
        return false;
    }

    public static boolean hasVisibleBonusNodeAt(int row, int col) {
        for (int i = 0; i < bonusNodeCells.size(); i++) {
            int[] cell = bonusNodeCells.get(i);
            if (!bonusNodeCollected.get(i) && cell[0] == row && cell[1] == col) {
                return true;
            }
        }
        return false;
    }

    public static boolean collectBonusNodeAt(int row, int col) {
        for (int i = 0; i < bonusNodeCells.size(); i++) {
            int[] cell = bonusNodeCells.get(i);
            if (!bonusNodeCollected.get(i) && cell[0] == row && cell[1] == col) {
                bonusNodeCollected.set(i, true);
                return true;
            }
        }
        return false;
    }

    public static int getCollectedBonusNodeCount() {
        int count = 0;
        for (boolean collected : bonusNodeCollected) {
            if (collected) {
                count++;
            }
        }
        return count;
    }

    private static int computeRequiredDoorKeyPairsForSize(int size) {
        int pairs = 4 + (int)Math.round((size - 21) * (12.0 / 180.0));

        if (pairs < 4) {
            pairs = 4;
        }
        if (pairs > 16) {
            pairs = 16;
        }

        return pairs;
    }

    private void initializeWorldWithRetries() {
        final int requiredDoorKeyPairs = computeRequiredDoorKeyPairsForSize(CURRENT_SIZE);

        IllegalStateException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_WORLD_BUILD_ATTEMPTS; attempt++) {
            try {
                Maze maze = new Maze(CURRENT_SIZE, CURRENT_SIZE);
                char[][] candidateGrid = maze.getGrid();
                World candidateWorld = World.fromMaze(candidateGrid, requiredDoorKeyPairs);

                mazeGrid = candidateGrid;
                world = candidateWorld;

                initializeSkeletonKeys();
                initializeBonusNodes();
                validateOptionalPlacements();
                initializeDots();

                System.out.println(
                        "[WorldGen] success on attempt " + attempt +
                        " / " + MAX_WORLD_BUILD_ATTEMPTS +
                        " | size=" + CURRENT_SIZE +
                        " | pairs=" + requiredDoorKeyPairs +
                        " | skeletonKeys=" + skeletonKeyCells.size() +
                        " | bonusNodes=" + bonusNodeCells.size() +
                        " | dots=" + dotCells.size()
                );

                System.out.println(
                        "[WorldGen] object counts" +
                        " | doors=" + world.doorsById.size() +
                        " | keys=" + world.keysById.size() +
                        " | pressure plates=" + world.triggersById.size() +
                        " | barriers=" + world.barriersById.size()
                );

                if (attempt >= 25) {
                    System.out.println(
                            "[WorldGen] warning: generation needed " + attempt +
                            " attempts. Generator may be getting tight at this size/pair count."
                    );
                }

                return;
            } catch (IllegalStateException e) {
                lastFailure = e;

                if (attempt == 1 || attempt % 10 == 0) {
                    System.out.println(
                            "[WorldGen] retry " + attempt +
                            " / " + MAX_WORLD_BUILD_ATTEMPTS +
                            " failed for size=" + CURRENT_SIZE +
                            " pairs=" + requiredDoorKeyPairs +
                            " | reason=" + e.getMessage()
                    );
                }
            }
        }

        throw new IllegalStateException(
                "Could not generate a valid world after " +
                MAX_WORLD_BUILD_ATTEMPTS + " fresh maze attempts.",
                lastFailure
        );
    }

    private void initializeSkeletonKeys() {
        skeletonKeyCells.clear();
        skeletonKeyCollected.clear();

        int targetCount = getSkeletonKeySpawnCountForCurrentLevel();

        ArrayList<int[]> candidates = new ArrayList<>();

        int exitRow = -1;
        int exitCol = -1;
        for (int r = 0; r < world.rows; r++) {
            for (int c = 0; c < world.cols; c++) {
                if (world.isExit(r, c)) {
                    exitRow = r;
                    exitCol = c;
                }
            }
        }

        for (int r = 0; r < world.rows; r++) {
            for (int c = 0; c < world.cols; c++) {
                if (!world.isBaseWalkable(r, c)) continue;
                if (r == world.startRow && c == world.startCol) continue;
                if (world.isExit(r, c)) continue;
                if (world.getKeyAt(r, c) != null) continue;
                if (world.getDoorAt(r, c) != null) continue;
                if (world.hasTrigger(r, c)) continue;

                int walkableNeighbors = 0;
                if (world.inBounds(r - 1, c) && world.isBaseWalkable(r - 1, c)) walkableNeighbors++;
                if (world.inBounds(r + 1, c) && world.isBaseWalkable(r + 1, c)) walkableNeighbors++;
                if (world.inBounds(r, c - 1) && world.isBaseWalkable(r, c - 1)) walkableNeighbors++;
                if (world.inBounds(r, c + 1) && world.isBaseWalkable(r, c + 1)) walkableNeighbors++;

                int distFromStart = Math.abs(r - world.startRow) + Math.abs(c - world.startCol);
                int distFromExit = (exitRow >= 0 && exitCol >= 0)
                        ? Math.abs(r - exitRow) + Math.abs(c - exitCol)
                        : 0;

                boolean sideBranchLike = walkableNeighbors <= 2;
                boolean farEnough = distFromStart >= Math.max(6, CURRENT_SIZE / 4);

                if (sideBranchLike && farEnough) {
                    candidates.add(new int[] { r, c, distFromStart + distFromExit });
                }
            }
        }

        if (candidates.isEmpty()) {
            for (int r = 0; r < world.rows; r++) {
                for (int c = 0; c < world.cols; c++) {
                    if (!world.isBaseWalkable(r, c)) continue;
                    if (r == world.startRow && c == world.startCol) continue;
                    if (world.isExit(r, c)) continue;
                    if (world.getKeyAt(r, c) != null) continue;
                    if (world.getDoorAt(r, c) != null) continue;
                    if (world.hasTrigger(r, c)) continue;

                    int distFromStart = Math.abs(r - world.startRow) + Math.abs(c - world.startCol);
                    candidates.add(new int[] { r, c, distFromStart });
                }
            }
        }

        Collections.shuffle(candidates, new Random());
        candidates.sort((a, b) -> Integer.compare(b[2], a[2]));

        int placed = 0;
        for (int[] cell : candidates) {
            if (placed >= targetCount) {
                break;
            }

            boolean duplicate = false;
            for (int[] existing : skeletonKeyCells) {
                if (existing[0] == cell[0] && existing[1] == cell[1]) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) continue;

            skeletonKeyCells.add(new int[] { cell[0], cell[1] });
            skeletonKeyCollected.add(false);
            placed++;
        }
    }

    private void initializeBonusNodes() {
        bonusNodeCells.clear();
        bonusNodeCollected.clear();

        int targetCount = getBonusNodeSpawnCountForCurrentLevel();

        ArrayList<int[]> candidates = new ArrayList<>();

        int exitRow = -1;
        int exitCol = -1;
        for (int r = 0; r < world.rows; r++) {
            for (int c = 0; c < world.cols; c++) {
                if (world.isExit(r, c)) {
                    exitRow = r;
                    exitCol = c;
                }
            }
        }

        for (int r = 0; r < world.rows; r++) {
            for (int c = 0; c < world.cols; c++) {
                if (!world.isBaseWalkable(r, c)) continue;
                if (r == world.startRow && c == world.startCol) continue;
                if (world.isExit(r, c)) continue;
                if (world.getKeyAt(r, c) != null) continue;
                if (world.getDoorAt(r, c) != null) continue;
                if (world.hasTrigger(r, c)) continue;
                if (hasVisibleSkeletonKeyAt(r, c)) continue;

                int walkableNeighbors = 0;
                if (world.inBounds(r - 1, c) && world.isBaseWalkable(r - 1, c)) walkableNeighbors++;
                if (world.inBounds(r + 1, c) && world.isBaseWalkable(r + 1, c)) walkableNeighbors++;
                if (world.inBounds(r, c - 1) && world.isBaseWalkable(r, c - 1)) walkableNeighbors++;
                if (world.inBounds(r, c + 1) && world.isBaseWalkable(r, c + 1)) walkableNeighbors++;

                int distFromStart = Math.abs(r - world.startRow) + Math.abs(c - world.startCol);
                int distFromExit = (exitRow >= 0 && exitCol >= 0)
                        ? Math.abs(r - exitRow) + Math.abs(c - exitCol)
                        : 0;

                boolean deadEnd = walkableNeighbors == 1;
                boolean sideBranch = walkableNeighbors == 2;

                boolean farEnoughFromStart = distFromStart >= Math.max(5, CURRENT_SIZE / 5);
                boolean farEnoughFromExit = distFromExit >= Math.max(6, CURRENT_SIZE / 4);

                if (!farEnoughFromStart || !farEnoughFromExit) {
                    continue;
                }

                int score = 0;

                // Strongly prefer real dead ends
                if (deadEnd) {
                    score += 1000;
                } else if (sideBranch) {
                    score += 450;
                } else {
                    continue;
                }

                // Prefer cells that are far from both start and exit
                score += distFromStart * 3;
                score += distFromExit * 5;

                // Prefer "deep" optional spaces rather than near-corridor noise
                score += Math.abs(distFromStart - distFromExit);

                candidates.add(new int[] { r, c, score });
            }
        }

        if (candidates.isEmpty()) {
            for (int r = 0; r < world.rows; r++) {
                for (int c = 0; c < world.cols; c++) {
                    if (!world.isBaseWalkable(r, c)) continue;
                    if (r == world.startRow && c == world.startCol) continue;
                    if (world.isExit(r, c)) continue;
                    if (world.getKeyAt(r, c) != null) continue;
                    if (world.getDoorAt(r, c) != null) continue;
                    if (world.hasTrigger(r, c)) continue;
                    if (hasVisibleSkeletonKeyAt(r, c)) continue;

                    int walkableNeighbors = 0;
                    if (world.inBounds(r - 1, c) && world.isBaseWalkable(r - 1, c)) walkableNeighbors++;
                    if (world.inBounds(r + 1, c) && world.isBaseWalkable(r + 1, c)) walkableNeighbors++;
                    if (world.inBounds(r, c - 1) && world.isBaseWalkable(r, c - 1)) walkableNeighbors++;
                    if (world.inBounds(r, c + 1) && world.isBaseWalkable(r, c + 1)) walkableNeighbors++;

                    if (walkableNeighbors > 2) continue;

                    int distFromStart = Math.abs(r - world.startRow) + Math.abs(c - world.startCol);
                    int distFromExit = (exitRow >= 0 && exitCol >= 0)
                            ? Math.abs(r - exitRow) + Math.abs(c - exitCol)
                            : 0;

                    int score = distFromStart + distFromExit;
                    candidates.add(new int[] { r, c, score });
                }
            }
        }

        Collections.shuffle(candidates, new Random());
        candidates.sort((a, b) -> Integer.compare(b[2], a[2]));

        int placed = 0;
        for (int[] cell : candidates) {
            if (placed >= targetCount) {
                break;
            }

            boolean duplicate = false;
            for (int[] existing : bonusNodeCells) {
                if (existing[0] == cell[0] && existing[1] == cell[1]) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) continue;

            bonusNodeCells.add(new int[] { cell[0], cell[1] });
            bonusNodeCollected.add(false);
            placed++;
        }
    }
    

    private void initializeDots() {
        dotCells.clear();
        dotCollected.clear();

        if (world == null) {
            return;
        }

        /*
         * Dot distribution pass:
         *
         * The old system used a row/column ordinal pattern with row/column
         * drought protection. It produced the right total scale, but a player
         * following corridors could still experience long low-signal stretches,
         * especially in huge late-game regions.
         *
         * This version keeps dots deterministic and non-guiding, but distributes
         * them by maze topology instead of screen/grid scan order.
         */

        final int preferredDistanceSpacing;
        final int localCoverageRadius;
        final int clusterPercent;

        if (CURRENT_SIZE >= 101) {
            preferredDistanceSpacing = 4;
            localCoverageRadius = 3;
            clusterPercent = 34;
        } else if (CURRENT_SIZE >= 61) {
            preferredDistanceSpacing = 5;
            localCoverageRadius = 3;
            clusterPercent = 26;
        } else {
            preferredDistanceSpacing = 5;
            localCoverageRadius = 4;
            clusterPercent = 18;
        }

        boolean[][] eligible = new boolean[world.rows][world.cols];
        boolean[][] placed = new boolean[world.rows][world.cols];
        int eligibleCount = 0;

        for (int r = 0; r < world.rows; r++) {
            for (int c = 0; c < world.cols; c++) {
                if (!world.isBaseWalkable(r, c)) continue;
                if (r == world.startRow && c == world.startCol) continue;
                if (world.isExit(r, c)) continue;
                if (world.getKeyAt(r, c) != null) continue;
                if (world.getDoorAt(r, c) != null) continue;
                if (world.hasTrigger(r, c)) continue;
                if (hasVisibleSkeletonKeyAt(r, c)) continue;
                if (hasVisibleBonusNodeAt(r, c)) continue;

                eligible[r][c] = true;
                eligibleCount++;
            }
        }

        if (eligibleCount <= 0) {
            return;
        }

        long seed = CURRENT_SIZE * 1_000_003L +
                world.startRow * 10_007L +
                world.startCol * 101L +
                world.exitRow * 503L +
                world.exitCol;

        int[][] distance = computeWalkableDistanceFromStart();
        int phase = (int)Math.floorMod(seed, preferredDistanceSpacing);

        for (int r = 0; r < world.rows; r++) {
            for (int c = 0; c < world.cols; c++) {
                if (!eligible[r][c]) {
                    continue;
                }

                int d = distance[r][c];
                if (d >= 0 && Math.floorMod(d + phase, preferredDistanceSpacing) == 0) {
                    placed[r][c] = true;
                }
            }
        }

        for (int r = 0; r < world.rows; r++) {
            for (int c = 0; c < world.cols; c++) {
                if (!eligible[r][c] || placed[r][c]) {
                    continue;
                }

                if (!hasPlacedDotWithinRadius(placed, r, c, localCoverageRadius)) {
                    placed[r][c] = true;
                }
            }
        }

        Random clusterRng = new Random(seed ^ 0x5DEECE66DL);
        ArrayList<int[]> anchorCells = new ArrayList<>();

        for (int r = 0; r < world.rows; r++) {
            for (int c = 0; c < world.cols; c++) {
                if (placed[r][c]) {
                    anchorCells.add(new int[] { r, c });
                }
            }
        }

        Collections.shuffle(anchorCells, clusterRng);

        for (int[] anchor : anchorCells) {
            if (clusterRng.nextInt(100) >= clusterPercent) {
                continue;
            }

            placeOneNearbyDotIfAvailable(eligible, placed, anchor[0], anchor[1], clusterRng);
        }

        for (int r = 0; r < world.rows; r++) {
            for (int c = 0; c < world.cols; c++) {
                if (placed[r][c]) {
                    dotCells.add(new int[] { r, c });
                    dotCollected.add(false);
                }
            }
        }
    }

    private int[][] computeWalkableDistanceFromStart() {
        int[][] distance = new int[world.rows][world.cols];
        for (int r = 0; r < world.rows; r++) {
            for (int c = 0; c < world.cols; c++) {
                distance[r][c] = -1;
            }
        }

        if (!world.inBounds(world.startRow, world.startCol)) {
            return distance;
        }

        ArrayDeque<int[]> q = new ArrayDeque<>();
        distance[world.startRow][world.startCol] = 0;
        q.addLast(new int[] { world.startRow, world.startCol });

        final int[][] dirs = {
                { -1, 0 },
                {  1, 0 },
                {  0,-1 },
                {  0, 1 }
        };

        while (!q.isEmpty()) {
            int[] cur = q.removeFirst();
            int row = cur[0];
            int col = cur[1];
            int nextDistance = distance[row][col] + 1;

            for (int[] d : dirs) {
                int nr = row + d[0];
                int nc = col + d[1];

                if (!world.inBounds(nr, nc)) {
                    continue;
                }
                if (!world.isBaseWalkable(nr, nc)) {
                    continue;
                }
                if (distance[nr][nc] >= 0) {
                    continue;
                }

                distance[nr][nc] = nextDistance;
                q.addLast(new int[] { nr, nc });
            }
        }

        return distance;
    }

    private boolean hasPlacedDotWithinRadius(boolean[][] placed, int row, int col, int radius) {
        int r0 = Math.max(0, row - radius);
        int r1 = Math.min(world.rows - 1, row + radius);
        int c0 = Math.max(0, col - radius);
        int c1 = Math.min(world.cols - 1, col + radius);
        int radiusSquared = radius * radius;

        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                if (!placed[r][c]) {
                    continue;
                }

                int dr = r - row;
                int dc = c - col;
                if (dr * dr + dc * dc <= radiusSquared) {
                    return true;
                }
            }
        }

        return false;
    }

    private void placeOneNearbyDotIfAvailable(
            boolean[][] eligible,
            boolean[][] placed,
            int row,
            int col,
            Random rng
    ) {
        ArrayList<int[]> candidates = new ArrayList<>();

        final int[][] offsets = {
                { -1, 0 }, { 1, 0 }, { 0,-1 }, { 0, 1 },
                { -1,-1 }, { -1, 1 }, { 1,-1 }, { 1, 1 },
                { -2, 0 }, { 2, 0 }, { 0,-2 }, { 0, 2 }
        };

        for (int[] offset : offsets) {
            int nr = row + offset[0];
            int nc = col + offset[1];

            if (!world.inBounds(nr, nc)) {
                continue;
            }
            if (!eligible[nr][nc] || placed[nr][nc]) {
                continue;
            }

            candidates.add(new int[] { nr, nc });
        }

        if (candidates.isEmpty()) {
            return;
        }

        int[] chosen = candidates.get(rng.nextInt(candidates.size()));
        placed[chosen[0]][chosen[1]] = true;
    }

    private void validateOptionalPlacements() {
        for (int[] cell : bonusNodeCells) {
            int row = cell[0];
            int col = cell[1];

            if (!world.inBounds(row, col)) {
                throw new IllegalStateException("Bonus node out of bounds");
            }

            if (!world.isBaseWalkable(row, col)) {
                throw new IllegalStateException("Bonus node placed on non-walkable cell");
            }

            if (row == world.startRow && col == world.startCol) {
                throw new IllegalStateException("Bonus node placed on start cell");
            }

            if (world.isExit(row, col)) {
                throw new IllegalStateException("Bonus node placed on exit cell");
            }

            if (world.getKeyAt(row, col) != null) {
                throw new IllegalStateException("Bonus node overlaps a colored key");
            }

            if (world.getDoorAt(row, col) != null) {
                throw new IllegalStateException("Bonus node overlaps a door");
            }

            if (world.hasTrigger(row, col)) {
                throw new IllegalStateException("Bonus node overlaps a pressure plate");
            }

            if (hasVisibleSkeletonKeyAt(row, col)) {
                throw new IllegalStateException("Bonus node overlaps a skeleton key");
            }
        }

        for (int[] cell : skeletonKeyCells) {
            int row = cell[0];
            int col = cell[1];

            if (!world.inBounds(row, col)) {
                throw new IllegalStateException("Skeleton key out of bounds");
            }

            if (!world.isBaseWalkable(row, col)) {
                throw new IllegalStateException("Skeleton key placed on non-walkable cell");
            }

            if (row == world.startRow && col == world.startCol) {
                throw new IllegalStateException("Skeleton key placed on start cell");
            }

            if (world.isExit(row, col)) {
                throw new IllegalStateException("Skeleton key placed on exit cell");
            }

            if (world.getKeyAt(row, col) != null) {
                throw new IllegalStateException("Skeleton key overlaps a colored key");
            }

            if (world.getDoorAt(row, col) != null) {
                throw new IllegalStateException("Skeleton key overlaps a door");
            }

            if (world.hasTrigger(row, col)) {
                throw new IllegalStateException("Skeleton key overlaps a pressure plate");
            }
        }
    }

    private static int keyFaceTypeForColor(int colorIndex) {
        switch (colorIndex) {
            case 0: return FACE_KEY_RED;
            case 1: return FACE_KEY_BLUE;
            case 2: return FACE_KEY_GREEN;
            case 3: return FACE_KEY_YELLOW;
            default: return FACE_KEY_RED;
        }
    }

    private static int doorFaceTypeForColor(int colorIndex) {
        switch (colorIndex) {
            case 0: return FACE_DOOR_RED;
            case 1: return FACE_DOOR_BLUE;
            case 2: return FACE_DOOR_GREEN;
            case 3: return FACE_DOOR_YELLOW;
            default: return FACE_DOOR_RED;
        }
    }

    public static void rebuildFacesFromWorld() {
        List<int[]> faces = new ArrayList<>();

        int rows = world.rows;
        int cols = world.cols;

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                int i0 = i;
                int i1 = i + 1;
                int j0 = j;
                int j1 = j + 1;

                boolean solid = world.isSolid(i, j);
                Door door = world.getDoorAt(i, j);
                boolean hasLockedDoor = door != null && !door.isUnlocked;

                boolean hasClosedBarrier = world.hasClosedBarrier(i, j);
                boolean isExit = world.isExit(i, j);
                boolean hasTrigger = world.hasTrigger(i, j);
                Key key = world.getKeyAt(i, j);
                boolean hasVisibleKey = key != null && !key.isCollected;
                boolean hasVisibleSkeletonKey = hasVisibleSkeletonKeyAt(i, j);
                boolean hasVisibleBonusNode = hasVisibleBonusNodeAt(i, j);

                if (solid || hasLockedDoor) {
                    if (hasLockedDoor) {
                        int doorFaceType = doorFaceTypeForColor(door.colorIndex);

                        addBottomFace(faces, i0, i1, j0, j1, doorFaceType);
                        addTopFace(faces, i0, i1, j0, j1, doorFaceType);

                        if (i == 0 || !world.isSolid(i - 1, j)) {
                            addFrontFace(faces, i0, i1, j0, j1, doorFaceType);
                        }
                        if (i == rows - 1 || !world.isSolid(i + 1, j)) {
                            addBackFace(faces, i0, i1, j0, j1, doorFaceType);
                        }
                        if (j == 0 || !world.isSolid(i, j - 1)) {
                            addLeftFace(faces, i0, i1, j0, j1, doorFaceType);
                        }
                        if (j == cols - 1 || !world.isSolid(i, j + 1)) {
                            addRightFace(faces, i0, i1, j0, j1, doorFaceType);
                        }
                    } else {
                        addBottomFace(faces, i0, i1, j0, j1, hasClosedBarrier ? FACE_SPECIAL_WALL : 0);
                        addTopFace(faces, i0, i1, j0, j1, hasClosedBarrier ? FACE_SPECIAL_WALL : 1);

                        if (i == 0 || !world.isSolid(i - 1, j)) {
                            addFrontFace(faces, i0, i1, j0, j1, hasClosedBarrier ? FACE_SPECIAL_WALL : 2);
                        }
                        if (i == rows - 1 || !world.isSolid(i + 1, j)) {
                            addBackFace(faces, i0, i1, j0, j1, hasClosedBarrier ? FACE_SPECIAL_WALL : 3);
                        }
                        if (j == 0 || !world.isSolid(i, j - 1)) {
                            addLeftFace(faces, i0, i1, j0, j1, hasClosedBarrier ? FACE_SPECIAL_WALL : 4);
                        }
                        if (j == cols - 1 || !world.isSolid(i, j + 1)) {
                            addRightFace(faces, i0, i1, j0, j1, hasClosedBarrier ? FACE_SPECIAL_WALL : 5);
                        }
                    }
                } else {
                    int floorFaceType = FACE_FLOOR;

                    if (hasVisibleKey) {
                        floorFaceType = keyFaceTypeForColor(key.colorIndex);
                    } else if (hasVisibleSkeletonKey) {
                        floorFaceType = FACE_KEY_SKELETON;
                    } else if (hasVisibleBonusNode) {
                        floorFaceType = FACE_BONUS;
                    } else if (hasTrigger) {
                        floorFaceType = FACE_PLATE;
                    }

                    addOpenFloorFace(faces, i0, i1, j0, j1, floorFaceType);
                    addOpenCeilingFace(faces, i0, i1, j0, j1);

                    if (isExit) {
                        addExitFace(faces, i0, i1, j0, j1);
                    }
                }
            }
        }

        multiCubeFaces = new int[faces.size()][4];
        for (int k = 0; k < faces.size(); k++) {
            multiCubeFaces[k] = faces.get(k);
        }
        numMultiCubeFaces = faces.size();
    }

    private static void addOpenFloorFace(List<int[]> faces, int i0, int i1, int j0, int j1, int faceType) {
        faces.add(new int[] { indexes[i0][j0][0], indexes[i1][j0][0], indexes[i0][j1][0], faceType });
        faces.add(new int[] { indexes[i0][j1][0], indexes[i1][j0][0], indexes[i1][j1][0], faceType });
    }

    private static void addOpenCeilingFace(List<int[]> faces, int i0, int i1, int j0, int j1) {
        faces.add(new int[] { indexes[i0][j0][1], indexes[i0][j1][1], indexes[i1][j0][1], FACE_CEILING });
        faces.add(new int[] { indexes[i0][j1][1], indexes[i1][j1][1], indexes[i1][j0][1], FACE_CEILING });
    }

    private static void addExitFace(List<int[]> faces, int i0, int i1, int j0, int j1) {
        faces.add(new int[] { indexes[i1][j0][0], indexes[i1][j0][1], indexes[i1][j1][1], FACE_EXIT });
        faces.add(new int[] { indexes[i1][j0][0], indexes[i1][j1][1], indexes[i1][j1][0], FACE_EXIT });
    }

    private static void addBottomFace(List<int[]> faces, int i0, int i1, int j0, int j1, int faceType) {
        faces.add(new int[] { indexes[i0][j0][0], indexes[i0][j1][0], indexes[i1][j0][0], faceType });
        faces.add(new int[] { indexes[i0][j1][0], indexes[i1][j1][0], indexes[i1][j0][0], faceType });
    }

    private static void addTopFace(List<int[]> faces, int i0, int i1, int j0, int j1, int faceType) {
        faces.add(new int[] { indexes[i0][j0][1], indexes[i1][j0][1], indexes[i0][j1][1], faceType });
        faces.add(new int[] { indexes[i0][j1][1], indexes[i1][j0][1], indexes[i1][j1][1], faceType });
    }

    private static void addFrontFace(List<int[]> faces, int i0, int i1, int j0, int j1, int faceType) {
        faces.add(new int[] { indexes[i0][j0][0], indexes[i0][j0][1], indexes[i0][j1][1], faceType });
        faces.add(new int[] { indexes[i0][j0][0], indexes[i0][j1][1], indexes[i0][j1][0], faceType });
    }

    private static void addBackFace(List<int[]> faces, int i0, int i1, int j0, int j1, int faceType) {
        faces.add(new int[] { indexes[i1][j0][0], indexes[i1][j1][1], indexes[i1][j0][1], faceType });
        faces.add(new int[] { indexes[i1][j0][0], indexes[i1][j1][0], indexes[i1][j1][1], faceType });
    }

    private static void addLeftFace(List<int[]> faces, int i0, int i1, int j0, int j1, int faceType) {
        faces.add(new int[] { indexes[i0][j0][0], indexes[i1][j0][1], indexes[i0][j0][1], faceType });
        faces.add(new int[] { indexes[i1][j0][1], indexes[i0][j0][0], indexes[i1][j0][0], faceType });
    }

    private static void addRightFace(List<int[]> faces, int i0, int i1, int j0, int j1, int faceType) {
        faces.add(new int[] { indexes[i0][j1][0], indexes[i0][j1][1], indexes[i1][j1][1], faceType });
        faces.add(new int[] { indexes[i0][j1][0], indexes[i1][j1][1], indexes[i1][j1][0], faceType });
    }
}


