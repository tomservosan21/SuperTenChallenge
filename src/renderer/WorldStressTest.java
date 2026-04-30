package renderer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorldStressTest {

    private enum TestMode {
        FAST,
        STANDARD,
        SOAK
    }

    private static final class StressCase {
        final int level;
        final int expectedSize;
        final int expectedPairs;

        StressCase(int level, int expectedSize, int expectedPairs) {
            this.level = level;
            this.expectedSize = expectedSize;
            this.expectedPairs = expectedPairs;
        }
    }

    private static final class DoorInfo {
        final String id;

        DoorInfo(String id, int row, int col, int colorIndex) {
            this.id = id;
        }
    }

    private static final class TriggerInfo {
        final String id;
        final String targetBarrierId;

        TriggerInfo(String id, int row, int col, String targetBarrierId) {
            this.id = id;
            this.targetBarrierId = targetBarrierId;
        }
    }

    private static final class SearchState {
        final int row;
        final int col;
        final int redKeys;
        final int blueKeys;
        final int greenKeys;
        final int yellowKeys;
        final int skeletonKeys;
        final long unlockedDoorMask;
        final long firedTriggerMask;
        final long collectedColorKeyMask;
        final long collectedSkeletonMask;

        SearchState(
                int row,
                int col,
                int redKeys,
                int blueKeys,
                int greenKeys,
                int yellowKeys,
                int skeletonKeys,
                long unlockedDoorMask,
                long firedTriggerMask,
                long collectedColorKeyMask,
                long collectedSkeletonMask
        ) {
            this.row = row;
            this.col = col;
            this.redKeys = redKeys;
            this.blueKeys = blueKeys;
            this.greenKeys = greenKeys;
            this.yellowKeys = yellowKeys;
            this.skeletonKeys = skeletonKeys;
            this.unlockedDoorMask = unlockedDoorMask;
            this.firedTriggerMask = firedTriggerMask;
            this.collectedColorKeyMask = collectedColorKeyMask;
            this.collectedSkeletonMask = collectedSkeletonMask;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SearchState)) {
                return false;
            }
            SearchState other = (SearchState) o;
            return row == other.row &&
                    col == other.col &&
                    redKeys == other.redKeys &&
                    blueKeys == other.blueKeys &&
                    greenKeys == other.greenKeys &&
                    yellowKeys == other.yellowKeys &&
                    skeletonKeys == other.skeletonKeys &&
                    unlockedDoorMask == other.unlockedDoorMask &&
                    firedTriggerMask == other.firedTriggerMask &&
                    collectedColorKeyMask == other.collectedColorKeyMask &&
                    collectedSkeletonMask == other.collectedSkeletonMask;
        }

        @Override
        public int hashCode() {
            int result = row;
            result = 31 * result + col;
            result = 31 * result + redKeys;
            result = 31 * result + blueKeys;
            result = 31 * result + greenKeys;
            result = 31 * result + yellowKeys;
            result = 31 * result + skeletonKeys;
            result = 31 * result + (int) (unlockedDoorMask ^ (unlockedDoorMask >>> 32));
            result = 31 * result + (int) (firedTriggerMask ^ (firedTriggerMask >>> 32));
            result = 31 * result + (int) (collectedColorKeyMask ^ (collectedColorKeyMask >>> 32));
            result = 31 * result + (int) (collectedSkeletonMask ^ (collectedSkeletonMask >>> 32));
            return result;
        }
    }

    private static int RUNS_PER_CONFIGURATION;
    private static StressCase[] ACTIVE_CASES;

    private static int GLOBAL_TOTAL_CONFIGS = 0;
    private static int GLOBAL_TOTAL_RUNS = 0;
    private static int GLOBAL_TOTAL_PASSES = 0;

    private static int GLOBAL_GENERATION_FAILURES = 0;
    private static int GLOBAL_INVARIANT_FAILURES = 0;
    private static int GLOBAL_BASE_SOLVER_FAILURES = 0;
    private static int GLOBAL_CRAWLER_FAILURES = 0;
    private static int GLOBAL_RESET_FAILURES = 0;

    private static final StressCase[] FAST_CASES = {
            new StressCase(1, 21, 4),
            new StressCase(5, 41, 5),
            new StressCase(8, 81, 8),
            new StressCase(10, 121, 11)
    };

    private static final StressCase[] STANDARD_CASES = {
            new StressCase(1, 21, 4),
            new StressCase(2, 21, 4),
            new StressCase(3, 31, 5),
            new StressCase(4, 31, 5),
            new StressCase(5, 41, 5),
            new StressCase(6, 51, 6),
            new StressCase(7, 61, 7),
            new StressCase(8, 81, 8),
            new StressCase(9, 101, 9),
            new StressCase(10, 121, 11)
    };

    private static final StressCase[] SOAK_CASES = {
            new StressCase(1, 21, 4),
            new StressCase(2, 21, 4),
            new StressCase(3, 31, 5),
            new StressCase(4, 31, 5),
            new StressCase(5, 41, 5),
            new StressCase(6, 51, 6),
            new StressCase(7, 61, 7),
            new StressCase(8, 81, 8),
            new StressCase(9, 101, 9),
            new StressCase(10, 121, 11)
    };

    private static final int[][] DIRS = {
            { -1, 0 },
            {  1, 0 },
            {  0,-1 },
            {  0, 1 }
    };

    public static void main(String[] args) {
        TestMode mode = promptForMode();
        configureMode(mode);

        long globalStartMs = System.currentTimeMillis();

        System.out.println("===== WORLD STRESS SWEEP START =====");
        System.out.println("Mode: " + mode);
        System.out.println("Runs per configuration: " + RUNS_PER_CONFIGURATION);
        System.out.println("Configurations: " + ACTIVE_CASES.length);
        System.out.println();

        for (StressCase stressCase : ACTIVE_CASES) {
            runConfiguration(stressCase);
            System.out.println();
        }

        int totalFailures =
                GLOBAL_GENERATION_FAILURES +
                GLOBAL_INVARIANT_FAILURES +
                GLOBAL_BASE_SOLVER_FAILURES +
                GLOBAL_CRAWLER_FAILURES +
                GLOBAL_RESET_FAILURES;

        System.out.println("===== GLOBAL SUMMARY =====");
        System.out.println("Configurations tested: " + GLOBAL_TOTAL_CONFIGS);
        System.out.println("Runs attempted: " + GLOBAL_TOTAL_RUNS);
        System.out.println("Passes: " + GLOBAL_TOTAL_PASSES);
        System.out.println("Failures: " + totalFailures);
        System.out.println("  Generation failures: " + GLOBAL_GENERATION_FAILURES);
        System.out.println("  Invariant failures: " + GLOBAL_INVARIANT_FAILURES);
        System.out.println("  Base solver failures: " + GLOBAL_BASE_SOLVER_FAILURES);
        System.out.println("  Crawler failures: " + GLOBAL_CRAWLER_FAILURES);
        System.out.println("  Reset failures: " + GLOBAL_RESET_FAILURES);

        if (totalFailures == 0) {
            System.out.println("Status: ALL TESTS PASSED");
        } else {
            System.out.println("Status: FAILURES DETECTED");
        }

        long elapsedMs = System.currentTimeMillis() - globalStartMs;
        System.out.println("===== WORLD STRESS SWEEP COMPLETE =====");
        System.out.println("Total elapsed ms: " + elapsedMs);
    }

    private static void configureMode(TestMode mode) {
        switch (mode) {
            case FAST:
                RUNS_PER_CONFIGURATION = 5;
                ACTIVE_CASES = FAST_CASES;
                break;

            case STANDARD:
                RUNS_PER_CONFIGURATION = 10;
                ACTIVE_CASES = STANDARD_CASES;
                break;

            case SOAK:
                RUNS_PER_CONFIGURATION = 25;
                ACTIVE_CASES = SOAK_CASES;
                break;

            default:
                throw new IllegalStateException("Unhandled mode: " + mode);
        }
    }

    @SuppressWarnings("resource")
    private static TestMode promptForMode() {
        java.util.Scanner scanner = new java.util.Scanner(System.in);

        System.out.println("Select stress test mode:");
        System.out.println("1 = FAST (quick sanity test)");
        System.out.println("2 = STANDARD (balanced regression test)");
        System.out.println("3 = SOAK (heavy level-aligned sweep)");
        System.out.print("Enter choice (1-3) [default = 2]: ");

        int choice = 0;
        try {
            choice = Integer.parseInt(scanner.nextLine().trim());
        } catch (Exception e) {
            // default below
        }

        switch (choice) {
            case 1: return TestMode.FAST;
            case 2: return TestMode.STANDARD;
            case 3: return TestMode.SOAK;
            default:
                System.out.println("Invalid input. Defaulting to STANDARD.");
                return TestMode.STANDARD;
        }
    }

    private static void runConfiguration(StressCase stressCase) {
        long startMs = System.currentTimeMillis();

        int generationFailures = 0;
        int invariantFailures = 0;
        int baseSolverFailures = 0;
        int crawlerFailures = 0;
        int resetFailures = 0;
        int passes = 0;

        System.out.println("----- CONFIG START -----");
        System.out.println(
                "Level: " + stressCase.level +
                " | size=" + stressCase.expectedSize +
                " | expectedPairs=" + stressCase.expectedPairs
        );

        for (int run = 1; run <= RUNS_PER_CONFIGURATION; run++) {
            World world;

            try {
                SceneGenerator.configureForLevel(stressCase.level);
                SceneGenerator.generateCurrentLevelWorld();
                world = SceneGenerator.world;
            } catch (Exception e) {
                generationFailures++;
                System.out.println(
                        "[GENERATION FAILURE] level=" + stressCase.level +
                        " run=" + run +
                        " | " + e.getMessage()
                );
                continue;
            }

            try {
                checkWorldInvariants(world, stressCase.expectedSize, stressCase.expectedPairs);
                checkOptionalPlacementInvariants(world, stressCase.level);
            } catch (Exception e) {
                invariantFailures++;
                System.out.println(
                        "[INVARIANT FAILURE] level=" + stressCase.level +
                        " run=" + run +
                        " | " + e.getMessage()
                );
                dumpWorldSummary(world, stressCase.level);
                continue;
            }

            boolean baseSolverOk;
            try {
                baseSolverOk = world.runSolvabilityCheck();
            } catch (Exception e) {
                baseSolverFailures++;
                System.out.println(
                        "[BASE SOLVER EXCEPTION] level=" + stressCase.level +
                        " run=" + run +
                        " | " + e.getMessage()
                );
                dumpWorldSummary(world, stressCase.level);
                continue;
            }

            if (!baseSolverOk) {
                baseSolverFailures++;
                System.out.println(
                        "[BASE SOLVER FAILURE] level=" + stressCase.level +
                        " run=" + run
                );
                dumpWorldSummary(world, stressCase.level);
                continue;
            }

            boolean crawlerOk;
            try {
                crawlerOk = runGameplayCrawler(world);
            } catch (Exception e) {
                crawlerFailures++;
                System.out.println(
                        "[CRAWLER EXCEPTION] level=" + stressCase.level +
                        " run=" + run +
                        " | " + e.getMessage()
                );
                dumpWorldSummary(world, stressCase.level);
                continue;
            }

            if (!crawlerOk) {
                crawlerFailures++;
                System.out.println(
                        "[CRAWLER FAILURE] level=" + stressCase.level +
                        " run=" + run
                );
                dumpWorldSummary(world, stressCase.level);
                continue;
            }

            try {
                resetAllRuntimeState();
                checkResetState(world, stressCase.level);
            } catch (Exception e) {
                resetFailures++;
                System.out.println(
                        "[RESET FAILURE] level=" + stressCase.level +
                        " run=" + run +
                        " | " + e.getMessage()
                );
                dumpWorldSummary(world, stressCase.level);
                continue;
            }

            boolean crawlerAfterResetOk;
            try {
                crawlerAfterResetOk = runGameplayCrawler(world);
            } catch (Exception e) {
                resetFailures++;
                System.out.println(
                        "[RESET CRAWLER EXCEPTION] level=" + stressCase.level +
                        " run=" + run +
                        " | " + e.getMessage()
                );
                dumpWorldSummary(world, stressCase.level);
                continue;
            }

            if (!crawlerAfterResetOk) {
                resetFailures++;
                System.out.println(
                        "[RESET CRAWLER FAILURE] level=" + stressCase.level +
                        " run=" + run
                );
                dumpWorldSummary(world, stressCase.level);
                continue;
            }

            passes++;

            if (run == RUNS_PER_CONFIGURATION || run % 5 == 0) {
                System.out.println(
                        "[Progress] level=" + stressCase.level +
                        " | completed " + run + " / " + RUNS_PER_CONFIGURATION
                );
            }
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        int totalFailures =
                generationFailures +
                invariantFailures +
                baseSolverFailures +
                crawlerFailures +
                resetFailures;

        System.out.println("----- CONFIG RESULT -----");
        System.out.println("Level: " + stressCase.level);
        System.out.println("Size: " + stressCase.expectedSize);
        System.out.println("Expected pairs: " + stressCase.expectedPairs);
        System.out.println("Runs: " + RUNS_PER_CONFIGURATION);
        System.out.println("Passes: " + passes);
        System.out.println("Failures: " + totalFailures);
        System.out.println("  Generation failures: " + generationFailures);
        System.out.println("  Invariant failures: " + invariantFailures);
        System.out.println("  Base solver failures: " + baseSolverFailures);
        System.out.println("  Crawler failures: " + crawlerFailures);
        System.out.println("  Reset failures: " + resetFailures);
        System.out.println("Elapsed ms: " + elapsedMs);

        GLOBAL_TOTAL_CONFIGS++;
        GLOBAL_TOTAL_RUNS += RUNS_PER_CONFIGURATION;
        GLOBAL_TOTAL_PASSES += passes;
        GLOBAL_GENERATION_FAILURES += generationFailures;
        GLOBAL_INVARIANT_FAILURES += invariantFailures;
        GLOBAL_BASE_SOLVER_FAILURES += baseSolverFailures;
        GLOBAL_CRAWLER_FAILURES += crawlerFailures;
        GLOBAL_RESET_FAILURES += resetFailures;
    }

    private static void checkWorldInvariants(World world, int expectedSize, int expectedPairs) {
        if (world == null) {
            throw new IllegalStateException("World is null");
        }

        if (world.rows != expectedSize || world.cols != expectedSize) {
            throw new IllegalStateException(
                    "World size mismatch. Expected " + expectedSize + "x" + expectedSize +
                    ", got " + world.rows + "x" + world.cols
            );
        }

        if (!world.inBounds(world.startRow, world.startCol)) {
            throw new IllegalStateException("Start out of bounds");
        }

        if (!world.inBounds(world.exitRow, world.exitCol)) {
            throw new IllegalStateException("Exit out of bounds");
        }

        if (!world.isBaseWalkable(world.startRow, world.startCol)) {
            throw new IllegalStateException("Start is not on a walkable cell");
        }

        if (!world.isExit(world.exitRow, world.exitCol)) {
            throw new IllegalStateException("Exit cell is not marked as exit");
        }

        if (world.doorsById.size() != expectedPairs) {
            throw new IllegalStateException(
                    "Expected " + expectedPairs + " doors, got " + world.doorsById.size()
            );
        }

        if (world.keysById.size() != expectedPairs) {
            throw new IllegalStateException(
                    "Expected " + expectedPairs + " keys, got " + world.keysById.size()
            );
        }

        int[] doorColorCounts = new int[4];
        int[] keyColorCounts = new int[4];

        for (Door door : world.doorsById.values()) {
            if (door == null) {
                throw new IllegalStateException("Null door in doorsById");
            }

            if (door.colorIndex < 0 || door.colorIndex > 3) {
                throw new IllegalStateException(
                        "Door has invalid colorIndex " + door.colorIndex + " for " + door.id
                );
            }

            if (!world.inBounds(door.cell.row, door.cell.col)) {
                throw new IllegalStateException("Door out of bounds: " + door.id);
            }

            String cellDoorId = world.cells[door.cell.row][door.cell.col].doorId;
            if (!door.id.equals(cellDoorId)) {
                throw new IllegalStateException("Door/cell mismatch for " + door.id);
            }

            doorColorCounts[door.colorIndex]++;
        }

        for (Key key : world.keysById.values()) {
            if (key == null) {
                throw new IllegalStateException("Null key in keysById");
            }

            if (key.colorIndex < 0 || key.colorIndex > 3) {
                throw new IllegalStateException(
                        "Key has invalid colorIndex " + key.colorIndex + " for " + key.id
                );
            }

            if (!world.inBounds(key.cell.row, key.cell.col)) {
                throw new IllegalStateException("Key out of bounds: " + key.id);
            }

            String cellKeyId = world.cells[key.cell.row][key.cell.col].keyId;
            if (!key.id.equals(cellKeyId)) {
                throw new IllegalStateException("Key/cell mismatch for " + key.id);
            }

            if (world.cells[key.cell.row][key.cell.col].doorId != null) {
                throw new IllegalStateException("Key shares cell with door: " + key.id);
            }

            if (world.cells[key.cell.row][key.cell.col].barrierId != null) {
                throw new IllegalStateException("Key shares cell with barrier: " + key.id);
            }

            if (world.cells[key.cell.row][key.cell.col].triggerId != null) {
                throw new IllegalStateException("Key shares cell with trigger: " + key.id);
            }

            keyColorCounts[key.colorIndex]++;
        }

        for (int color = 0; color < 4; color++) {
            if (keyColorCounts[color] < doorColorCounts[color]) {
                throw new IllegalStateException(
                        "Not enough keys for color " + color +
                        " | doors=" + doorColorCounts[color] +
                        " keys=" + keyColorCounts[color]
                );
            }
        }
    }

    private static void checkOptionalPlacementInvariants(World world, int level) {
        int visibleSkeletonCount = 0;
        int visibleBonusCount = 0;

        for (int r = 0; r < world.rows; r++) {
            for (int c = 0; c < world.cols; c++) {
                boolean skeleton = SceneGenerator.hasVisibleSkeletonKeyAt(r, c);
                boolean bonus = SceneGenerator.hasVisibleBonusNodeAt(r, c);

                if (skeleton) {
                    visibleSkeletonCount++;

                    if (!world.inBounds(r, c)) {
                        throw new IllegalStateException("Skeleton key out of bounds");
                    }
                    if (!world.isBaseWalkable(r, c)) {
                        throw new IllegalStateException("Skeleton key on non-walkable cell");
                    }
                    if (r == world.startRow && c == world.startCol) {
                        throw new IllegalStateException("Skeleton key on start cell");
                    }
                    if (world.isExit(r, c)) {
                        throw new IllegalStateException("Skeleton key on exit cell");
                    }
                    if (world.getKeyAt(r, c) != null) {
                        throw new IllegalStateException("Skeleton key overlaps colored key");
                    }
                    if (world.getDoorAt(r, c) != null) {
                        throw new IllegalStateException("Skeleton key overlaps door");
                    }
                    if (world.hasTrigger(r, c)) {
                        throw new IllegalStateException("Skeleton key overlaps trigger");
                    }
                }

                if (bonus) {
                    visibleBonusCount++;

                    if (!world.inBounds(r, c)) {
                        throw new IllegalStateException("Bonus node out of bounds");
                    }
                    if (!world.isBaseWalkable(r, c)) {
                        throw new IllegalStateException("Bonus node on non-walkable cell");
                    }
                    if (r == world.startRow && c == world.startCol) {
                        throw new IllegalStateException("Bonus node on start cell");
                    }
                    if (world.isExit(r, c)) {
                        throw new IllegalStateException("Bonus node on exit cell");
                    }
                    if (world.getKeyAt(r, c) != null) {
                        throw new IllegalStateException("Bonus node overlaps colored key");
                    }
                    if (world.getDoorAt(r, c) != null) {
                        throw new IllegalStateException("Bonus node overlaps door");
                    }
                    if (world.hasTrigger(r, c)) {
                        throw new IllegalStateException("Bonus node overlaps trigger");
                    }
                    if (skeleton) {
                        throw new IllegalStateException("Bonus node overlaps skeleton key");
                    }
                }
            }
        }

        int expectedSkeleton = expectedSkeletonCountForLevel(level);
        int expectedBonus = expectedBonusCountForLevel(level);

        if (visibleSkeletonCount != expectedSkeleton) {
            throw new IllegalStateException(
                    "Expected " + expectedSkeleton + " skeleton keys, got " + visibleSkeletonCount
            );
        }

        if (visibleBonusCount != expectedBonus) {
            throw new IllegalStateException(
                    "Expected " + expectedBonus + " bonus nodes, got " + visibleBonusCount
            );
        }
    }

    private static int expectedSkeletonCountForLevel(int level) {
        int size = expectedSizeForLevel(level);
        return size >= 101 ? 2 : 1;
    }

    private static int expectedBonusCountForLevel(int level) {
        int size = expectedSizeForLevel(level);
        if (size <= 31) {
            return 1;
        }
        if (size <= 61) {
            return 2;
        }
        return 3;
    }

    private static int expectedSizeForLevel(int level) {
        switch (level) {
            case 1: return 21;
            case 2: return 21;
            case 3: return 31;
            case 4: return 31;
            case 5: return 41;
            case 6: return 51;
            case 7: return 61;
            case 8: return 81;
            case 9: return 101;
            case 10: return 121;
            default: return 121;
        }
    }

    private static void resetAllRuntimeState() {
        if (SceneGenerator.world != null) {
            SceneGenerator.world.resetRuntimeState();
        }
        SceneGenerator.resetSkeletonKeysForLevel();
        SceneGenerator.resetBonusNodesForLevel();
    }

    private static void checkResetState(World world, int level) {
        if (world == null) {
            throw new IllegalStateException("World is null during reset check");
        }

        for (Barrier barrier : world.barriersById.values()) {
            if (barrier.isOpen) {
                throw new IllegalStateException("Barrier still open after reset: " + barrier.id);
            }
        }

        for (Trigger trigger : world.triggersById.values()) {
            if (trigger.hasFired) {
                throw new IllegalStateException("Trigger still fired after reset: " + trigger.id);
            }
        }

        for (Door door : world.doorsById.values()) {
            if (door.isUnlocked) {
                throw new IllegalStateException("Door still unlocked after reset: " + door.id);
            }
        }

        for (Key key : world.keysById.values()) {
            if (key.isCollected) {
                throw new IllegalStateException("Key still collected after reset: " + key.id);
            }
        }

        int visibleSkeletonCount = 0;
        int visibleBonusCount = 0;

        for (int r = 0; r < world.rows; r++) {
            for (int c = 0; c < world.cols; c++) {
                if (SceneGenerator.hasVisibleSkeletonKeyAt(r, c)) {
                    visibleSkeletonCount++;
                }
                if (SceneGenerator.hasVisibleBonusNodeAt(r, c)) {
                    visibleBonusCount++;
                }
            }
        }

        int expectedSkeleton = expectedSkeletonCountForLevel(level);
        int expectedBonus = expectedBonusCountForLevel(level);

        if (visibleSkeletonCount != expectedSkeleton) {
            throw new IllegalStateException(
                    "Skeleton keys not fully reset. Expected " + expectedSkeleton +
                    ", got " + visibleSkeletonCount
            );
        }

        if (visibleBonusCount != expectedBonus) {
            throw new IllegalStateException(
                    "Bonus nodes not fully reset. Expected " + expectedBonus +
                    ", got " + visibleBonusCount
            );
        }
    }

    private static void dumpWorldSummary(World world, int level) {
        if (world == null) {
            System.out.println("World summary: <null>");
            return;
        }

        int[] doorColorCounts = new int[4];
        int[] keyColorCounts = new int[4];
        int visibleSkeletonCount = 0;
        int visibleBonusCount = 0;

        for (Door d : world.doorsById.values()) {
            if (d != null && d.colorIndex >= 0 && d.colorIndex < 4) {
                doorColorCounts[d.colorIndex]++;
            }
        }

        for (Key k : world.keysById.values()) {
            if (k != null && k.colorIndex >= 0 && k.colorIndex < 4) {
                keyColorCounts[k.colorIndex]++;
            }
        }

        for (int r = 0; r < world.rows; r++) {
            for (int c = 0; c < world.cols; c++) {
                if (SceneGenerator.hasVisibleSkeletonKeyAt(r, c)) {
                    visibleSkeletonCount++;
                }
                if (SceneGenerator.hasVisibleBonusNodeAt(r, c)) {
                    visibleBonusCount++;
                }
            }
        }

        System.out.println("World summary:");
        System.out.println("  level=" + level);
        System.out.println("  rows=" + world.rows + " cols=" + world.cols);
        System.out.println("  start=(" + world.startRow + "," + world.startCol + ")");
        System.out.println("  exit=(" + world.exitRow + "," + world.exitCol + ")");
        System.out.println("  barriers=" + world.barriersById.size());
        System.out.println("  triggers=" + world.triggersById.size());
        System.out.println("  doors=" + world.doorsById.size());
        System.out.println("  keys=" + world.keysById.size());
        System.out.println("  skeletonKeys=" + visibleSkeletonCount);
        System.out.println("  bonusNodes=" + visibleBonusCount);
        System.out.println(
                "  door colors: R=" + doorColorCounts[0] +
                " B=" + doorColorCounts[1] +
                " G=" + doorColorCounts[2] +
                " Y=" + doorColorCounts[3]
        );
        System.out.println(
                "  key colors:  R=" + keyColorCounts[0] +
                " B=" + keyColorCounts[1] +
                " G=" + keyColorCounts[2] +
                " Y=" + keyColorCounts[3]
        );
    }
    
    private static boolean recordIfNonDominated(
            Map<DominanceKey, ArrayList<SearchState>> frontierBySignature,
            SearchState candidate
    ) {
        DominanceKey key = new DominanceKey(
                candidate.row,
                candidate.col,
                candidate.unlockedDoorMask,
                candidate.firedTriggerMask,
                candidate.collectedColorKeyMask,
                candidate.collectedSkeletonMask
        );

        ArrayList<SearchState> frontier = frontierBySignature.get(key);
        if (frontier == null) {
            frontier = new ArrayList<>();
            frontier.add(candidate);
            frontierBySignature.put(key, frontier);
            return true;
        }

        for (SearchState existing : frontier) {
            if (dominates(existing, candidate)) {
                return false;
            }
        }

        for (int i = frontier.size() - 1; i >= 0; i--) {
            if (dominates(candidate, frontier.get(i))) {
                frontier.remove(i);
            }
        }

        frontier.add(candidate);
        return true;
    }
    
    private static boolean dominates(SearchState a, SearchState b) {
        return a.redKeys >= b.redKeys &&
                a.blueKeys >= b.blueKeys &&
                a.greenKeys >= b.greenKeys &&
                a.yellowKeys >= b.yellowKeys &&
                a.skeletonKeys >= b.skeletonKeys;
    }

    private static boolean runGameplayCrawler(World world) {
        resetAllRuntimeState();

        if (world == null) {
            return false;
        }

        List<DoorInfo> doors = new ArrayList<>();
        for (Door d : world.doorsById.values()) {
            doors.add(new DoorInfo(d.id, d.cell.row, d.cell.col, d.colorIndex));
        }
        doors.sort(Comparator.comparing(a -> a.id));

        List<TriggerInfo> triggers = new ArrayList<>();
        for (Trigger t : world.triggersById.values()) {
            triggers.add(new TriggerInfo(t.id, t.cell.row, t.cell.col, t.targetBarrierId));
        }
        triggers.sort(Comparator.comparing(a -> a.id));

        Map<String, Integer> doorIndexById = new LinkedHashMap<>();
        for (int i = 0; i < doors.size(); i++) {
            doorIndexById.put(doors.get(i).id, i);
        }

        Map<String, Integer> triggerIndexById = new LinkedHashMap<>();
        for (int i = 0; i < triggers.size(); i++) {
            triggerIndexById.put(triggers.get(i).id, i);
        }

        Map<String, Integer> barrierBitById = new HashMap<>();
        for (int i = 0; i < triggers.size(); i++) {
            barrierBitById.put(triggers.get(i).targetBarrierId, i);
        }

        Map<String, Integer> keyIndexById = new LinkedHashMap<>();
        List<Key> orderedKeys = new ArrayList<>(world.keysById.values());
        orderedKeys.sort(Comparator.comparing(k -> k.id));
        for (int i = 0; i < orderedKeys.size(); i++) {
            keyIndexById.put(orderedKeys.get(i).id, i);
        }

        int[][] skeletonIndexGrid = new int[world.rows][world.cols];
        for (int r = 0; r < world.rows; r++) {
            Arrays.fill(skeletonIndexGrid[r], -1);
        }

        int skeletonCounter = 0;
        for (int r = 0; r < world.rows; r++) {
            for (int c = 0; c < world.cols; c++) {
                if (SceneGenerator.hasVisibleSkeletonKeyAt(r, c)) {
                    skeletonIndexGrid[r][c] = skeletonCounter++;
                }
            }
        }

        if (doors.size() > 62 || triggers.size() > 62 || orderedKeys.size() > 62 || skeletonCounter > 62) {
            throw new IllegalStateException("Bitmask capacity exceeded");
        }

        ArrayDeque<SearchState> queue = new ArrayDeque<>();
        Map<DominanceKey, ArrayList<SearchState>> frontierBySignature = new HashMap<>();

        SearchState startState = collectAtCell(
                world,
                new SearchState(
                        world.startRow,
                        world.startCol,
                        0, 0, 0, 0,
                        0,
                        0L,
                        0L,
                        0L,
                        0L
                ),
                keyIndexById,
                skeletonIndexGrid
        );

        if (recordIfNonDominated(frontierBySignature, startState)) {
            queue.add(startState);
        }

        while (!queue.isEmpty()) {
            SearchState current = queue.removeFirst();

            if (current.row == world.exitRow && current.col == world.exitCol) {
                resetAllRuntimeState();
                return true;
            }

            SearchState afterTrigger = maybeFireTrigger(world, current, triggerIndexById);
            if (recordIfNonDominated(frontierBySignature, afterTrigger)) {
                queue.addLast(afterTrigger);
            }

            for (int[] dir : DIRS) {
                int nr = current.row + dir[0];
                int nc = current.col + dir[1];

                if (!world.inBounds(nr, nc)) {
                    continue;
                }

                TransitionResult result = attemptMove(
                        world,
                        current,
                        nr,
                        nc,
                        doorIndexById,
                        barrierBitById
                );

                if (!result.allowed) {
                    continue;
                }

                SearchState next = collectAtCell(
                        world,
                        result.state,
                        keyIndexById,
                        skeletonIndexGrid
                );

                SearchState nextAfterTrigger = maybeFireTrigger(world, next, triggerIndexById);

                if (recordIfNonDominated(frontierBySignature, nextAfterTrigger)) {
                    queue.addLast(nextAfterTrigger);
                }
            }
        }

        resetAllRuntimeState();
        return false;
    }

    private static final class TransitionResult {
        final boolean allowed;
        final SearchState state;

        TransitionResult(boolean allowed, SearchState state) {
            this.allowed = allowed;
            this.state = state;
        }
    }

    private static final class DominanceKey {
        final int row;
        final int col;
        final long unlockedDoorMask;
        final long firedTriggerMask;
        final long collectedColorKeyMask;
        final long collectedSkeletonMask;

        DominanceKey(
                int row,
                int col,
                long unlockedDoorMask,
                long firedTriggerMask,
                long collectedColorKeyMask,
                long collectedSkeletonMask
        ) {
            this.row = row;
            this.col = col;
            this.unlockedDoorMask = unlockedDoorMask;
            this.firedTriggerMask = firedTriggerMask;
            this.collectedColorKeyMask = collectedColorKeyMask;
            this.collectedSkeletonMask = collectedSkeletonMask;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof DominanceKey)) {
                return false;
            }
            DominanceKey other = (DominanceKey) o;
            return row == other.row &&
                    col == other.col &&
                    unlockedDoorMask == other.unlockedDoorMask &&
                    firedTriggerMask == other.firedTriggerMask &&
                    collectedColorKeyMask == other.collectedColorKeyMask &&
                    collectedSkeletonMask == other.collectedSkeletonMask;
        }

        @Override
        public int hashCode() {
            int result = row;
            result = 31 * result + col;
            result = 31 * result + (int) (unlockedDoorMask ^ (unlockedDoorMask >>> 32));
            result = 31 * result + (int) (firedTriggerMask ^ (firedTriggerMask >>> 32));
            result = 31 * result + (int) (collectedColorKeyMask ^ (collectedColorKeyMask >>> 32));
            result = 31 * result + (int) (collectedSkeletonMask ^ (collectedSkeletonMask >>> 32));
            return result;
        }
    }
    
    private static TransitionResult attemptMove(
            World world,
            SearchState state,
            int nr,
            int nc,
            Map<String, Integer> doorIndexById,
            Map<String, Integer> barrierBitById
    ) {
        WorldCell cell = world.cells[nr][nc];

        if (cell.baseType == CellType.WALL) {
            return new TransitionResult(false, state);
        }

        if (cell.barrierId != null) {
            Integer barrierBit = barrierBitById.get(cell.barrierId);
            if (barrierBit == null) {
                return new TransitionResult(false, state);
            }
            long mask = 1L << barrierBit;
            if ((state.firedTriggerMask & mask) == 0L) {
                return new TransitionResult(false, state);
            }
        }

        if (cell.doorId != null) {
            Integer doorIndex = doorIndexById.get(cell.doorId);
            if (doorIndex == null) {
                return new TransitionResult(false, state);
            }

            long doorMask = 1L << doorIndex;
            boolean alreadyUnlocked = (state.unlockedDoorMask & doorMask) != 0L;

            if (!alreadyUnlocked) {
                Door door = world.doorsById.get(cell.doorId);
                if (door == null) {
                    return new TransitionResult(false, state);
                }

                SearchState unlockedState = tryUnlockDoor(state, doorMask, door.colorIndex);
                if (unlockedState == null) {
                    return new TransitionResult(false, state);
                }

                return new TransitionResult(
                        true,
                        new SearchState(
                                nr,
                                nc,
                                unlockedState.redKeys,
                                unlockedState.blueKeys,
                                unlockedState.greenKeys,
                                unlockedState.yellowKeys,
                                unlockedState.skeletonKeys,
                                unlockedState.unlockedDoorMask,
                                unlockedState.firedTriggerMask,
                                unlockedState.collectedColorKeyMask,
                                unlockedState.collectedSkeletonMask
                        )
                );
            }
        }

        if (state.row == nr && state.col == nc) {
            return new TransitionResult(true, state);
        }

        return new TransitionResult(
                true,
                new SearchState(
                        nr,
                        nc,
                        state.redKeys,
                        state.blueKeys,
                        state.greenKeys,
                        state.yellowKeys,
                        state.skeletonKeys,
                        state.unlockedDoorMask,
                        state.firedTriggerMask,
                        state.collectedColorKeyMask,
                        state.collectedSkeletonMask
                )
        );
    }

    private static SearchState tryUnlockDoor(SearchState state, long doorMask, int colorIndex) {
        switch (colorIndex) {
            case 0:
                if (state.redKeys > 0) {
                    return new SearchState(
                            state.row, state.col,
                            state.redKeys - 1, state.blueKeys, state.greenKeys, state.yellowKeys,
                            state.skeletonKeys,
                            state.unlockedDoorMask | doorMask,
                            state.firedTriggerMask,
                            state.collectedColorKeyMask,
                            state.collectedSkeletonMask
                    );
                }
                break;

            case 1:
                if (state.blueKeys > 0) {
                    return new SearchState(
                            state.row, state.col,
                            state.redKeys, state.blueKeys - 1, state.greenKeys, state.yellowKeys,
                            state.skeletonKeys,
                            state.unlockedDoorMask | doorMask,
                            state.firedTriggerMask,
                            state.collectedColorKeyMask,
                            state.collectedSkeletonMask
                    );
                }
                break;

            case 2:
                if (state.greenKeys > 0) {
                    return new SearchState(
                            state.row, state.col,
                            state.redKeys, state.blueKeys, state.greenKeys - 1, state.yellowKeys,
                            state.skeletonKeys,
                            state.unlockedDoorMask | doorMask,
                            state.firedTriggerMask,
                            state.collectedColorKeyMask,
                            state.collectedSkeletonMask
                    );
                }
                break;

            case 3:
                if (state.yellowKeys > 0) {
                    return new SearchState(
                            state.row, state.col,
                            state.redKeys, state.blueKeys, state.greenKeys, state.yellowKeys - 1,
                            state.skeletonKeys,
                            state.unlockedDoorMask | doorMask,
                            state.firedTriggerMask,
                            state.collectedColorKeyMask,
                            state.collectedSkeletonMask
                    );
                }
                break;

            default:
                break;
        }

        if (state.skeletonKeys > 0) {
            return new SearchState(
                    state.row, state.col,
                    state.redKeys, state.blueKeys, state.greenKeys, state.yellowKeys,
                    state.skeletonKeys - 1,
                    state.unlockedDoorMask | doorMask,
                    state.firedTriggerMask,
                    state.collectedColorKeyMask,
                    state.collectedSkeletonMask
            );
        }

        return null;
    }

    private static SearchState maybeFireTrigger(
            World world,
            SearchState state,
            Map<String, Integer> triggerIndexById
    ) {
        String triggerId = world.cells[state.row][state.col].triggerId;
        if (triggerId == null) {
            return state;
        }

        Integer triggerIndex = triggerIndexById.get(triggerId);
        if (triggerIndex == null) {
            return state;
        }

        long bit = 1L << triggerIndex;
        if ((state.firedTriggerMask & bit) != 0L) {
            return state;
        }

        return new SearchState(
                state.row,
                state.col,
                state.redKeys,
                state.blueKeys,
                state.greenKeys,
                state.yellowKeys,
                state.skeletonKeys,
                state.unlockedDoorMask,
                state.firedTriggerMask | bit,
                state.collectedColorKeyMask,
                state.collectedSkeletonMask
        );
    }

    private static SearchState collectAtCell(
            World world,
            SearchState state,
            Map<String, Integer> keyIndexById,
            int[][] skeletonIndexGrid
    ) {
        int redKeys = state.redKeys;
        int blueKeys = state.blueKeys;
        int greenKeys = state.greenKeys;
        int yellowKeys = state.yellowKeys;
        int skeletonKeys = state.skeletonKeys;

        long collectedColorKeyMask = state.collectedColorKeyMask;
        long collectedSkeletonMask = state.collectedSkeletonMask;

        String keyId = world.cells[state.row][state.col].keyId;
        if (keyId != null) {
            Integer keyIndex = keyIndexById.get(keyId);
            Key key = world.keysById.get(keyId);

            if (keyIndex != null && key != null) {
                long bit = 1L << keyIndex;
                if ((collectedColorKeyMask & bit) == 0L) {
                    switch (key.colorIndex) {
                        case 0: redKeys++; break;
                        case 1: blueKeys++; break;
                        case 2: greenKeys++; break;
                        case 3: yellowKeys++; break;
                        default: break;
                    }
                    collectedColorKeyMask |= bit;
                }
            }
        }

        int skeletonIndex = skeletonIndexGrid[state.row][state.col];
        if (skeletonIndex >= 0) {
            long bit = 1L << skeletonIndex;
            if ((collectedSkeletonMask & bit) == 0L) {
                skeletonKeys++;
                collectedSkeletonMask |= bit;
            }
        }

        if (redKeys == state.redKeys &&
                blueKeys == state.blueKeys &&
                greenKeys == state.greenKeys &&
                yellowKeys == state.yellowKeys &&
                skeletonKeys == state.skeletonKeys &&
                collectedColorKeyMask == state.collectedColorKeyMask &&
                collectedSkeletonMask == state.collectedSkeletonMask) {
            return state;
        }

        return new SearchState(
                state.row,
                state.col,
                redKeys,
                blueKeys,
                greenKeys,
                yellowKeys,
                skeletonKeys,
                state.unlockedDoorMask,
                state.firedTriggerMask,
                collectedColorKeyMask,
                collectedSkeletonMask
        );
    }
}