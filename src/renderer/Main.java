package renderer;

//================= Main.java =================

import javax.swing.*;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class Main extends JPanel implements Runnable {
	private static final long serialVersionUID = 4622231226623369662L;
	
	private enum MaterialTheme {
        EARTH,
        FIRE,
        WATER,
        STONE,
        SMOKE,
        WOOD
    }

	BufferedImage img;
	ZBufferRenderer renderer;
	static Camera cam = new Camera();
	BSPNode bsp;
	List<Triangle> scene = new ArrayList<>();
	volatile BufferedImage image;

	BufferedImage wallTexture1;
	BufferedImage wallTexture2;
	BufferedImage wallTexture3;
	BufferedImage wallTexture4;
	BufferedImage floorTexture;
	BufferedImage ceilingTexture;
	BufferedImage metalTexture;
	BufferedImage exitTexture;

	BufferedImage[][] regionWallTextures;
	BufferedImage[] regionFloorTextures;
	BufferedImage[] regionCeilingTextures;

	BufferedImage[] coloredDoorTextures;
	BufferedImage[] coloredKeyTextures;

	BufferedImage pressurePlateTexture;
	BufferedImage removableWallTexture;
	BufferedImage pressedPressurePlateTexture;

	BufferedImage[][] landmarkCueTextures;
	
	BufferedImage[][] regionFloorVariantTextures;
	BufferedImage[][][] regionWallSubTextures;
	BufferedImage[][][] regionWallGradientTextures;
	
	BufferedImage[][] regionWallAccentTextures;
	BufferedImage[] rainbowWallTextures;

	private final ArrayList<Vertex> clipInput = new ArrayList<>(8);
	private final ReusablePoint3D rp0 = new ReusablePoint3D();
	private final ReusablePoint3D rp1 = new ReusablePoint3D();
	private final ReusablePoint3D rp2 = new ReusablePoint3D();

	int currentLevel = 1;
	int maxLevels = 10;

	volatile boolean interactRequested = false;

	boolean wKey, sKey, aKey, dKey, zKey, xKey;
	boolean iKey, jKey, kKey, lKey, nKey, mKey;
	boolean spaceKey;
	boolean showHud = true;
	long lastFrameTime = System.nanoTime();
	double fps = 0.0;
	long fpsAccumStart = System.nanoTime();
	int fpsFrameCount = 0;

	boolean showInventory = true;

	boolean levelCompleteAwaitingContinue = false;
	int pendingNextLevel = -1;

	int levelKeysCollected = 0;
	int levelDoorsUnlocked = 0;
	int levelPlatesActivated = 0;
	int levelScore = 0;

	BufferedImage skeletonKeyTexture;

	int skeletonKeyCount = 0;
	boolean skeletonKeyUseConfirm = false;
	int skeletonConfirmDoorId = -1;

	long levelStartNanos = System.nanoTime();
	long levelCompleteElapsedNanos = 0L;
	long lastExitLockedMessageNanos = 0L;

	private static final int MAX_INTERACTION_LINES = 5;
	private static final long INTERACTION_MESSAGE_DURATION_NANOS = 5_000_000_000L;

	private static class InteractionMessage {
		final String text;
		final long expiresAtNanos;

		InteractionMessage(String text, long expiresAtNanos) {
			this.text = text;
			this.expiresAtNanos = expiresAtNanos;
		}
	}

	private final ArrayDeque<InteractionMessage> interactionMessages = new ArrayDeque<>();

	volatile boolean rebuildSceneRequested = false;
	volatile boolean debugNextLevelRequested = false;
	volatile boolean debugForceAllDotsCollected = false;
	volatile boolean debugWarpNearExitRequested = false;

	// ================= WARP SYSTEM =================
	private int highestRegionReached = 0;
	private int highestRegionEntryRow = -1;
	private int highestRegionEntryCol = -1;

	private static final int WARP_CELL_X = 1;
	private static final int WARP_CELL_Y = 1;

	private boolean warpRecentlyUsed = false;
	private boolean warpWasClearedByReset = false;

	private boolean playerHasAdvancedOneCellThisLevel = false;
	private int levelStartPlayerRow = -1;
	private int levelStartPlayerCol = -1;

	private final LinkedHashMap<Integer, Integer> inventoryKeyCounts = new LinkedHashMap<>();
	
	private static final int[][] SPECIAL_WALL_COLORS = {
	        {128,   0, 128},   // PURPLE
	        {255, 140,   0},   // ORANGE
	        {245, 230,  40},   // YELLOW
	        {220,  40,  32},   // RED
	        { 40, 190,  70},   // GREEN
	        { 40, 110, 235},   // BLUE
	        {255, 105, 180},   // PINK
	        {150, 150, 150},   // GRAY
	        { 40, 220, 240},   // CYAN
	        {235,  40, 235}    // MAGENTA
	};

	private ColorPair[][] regionSpecialWallPairs = new ColorPair[16][];
	
	private static class ColorPair {
	    int r1, g1, b1;
	    int r2, g2, b2;

	    ColorPair(int r1, int g1, int b1, int r2, int g2, int b2) {
	        this.r1 = r1;
	        this.g1 = g1;
	        this.b1 = b1;
	        this.r2 = r2;
	        this.g2 = g2;
	        this.b2 = b2;
	    }
	}

	private static class ReusablePoint3D extends Point3D {
		public ReusablePoint3D() {
			super(0f, 0f, 0f);
		}

		public void setFromVertex(Vertex v) {
			this.x = (float) v.pos.x;
			this.y = (float) v.pos.y;
			this.z = (float) v.pos.z;
			this.u = (float) v.u;
			this.v = (float) v.v;
		}
	}
	
	private ColorPair[] getRegionSpecialWallPairs(int region) {
	    int safeRegion = clampRegion(region);

	    if (regionSpecialWallPairs[safeRegion] != null) {
	        return regionSpecialWallPairs[safeRegion];
	    }

	    int[] order = new int[SPECIAL_WALL_COLORS.length];
	    for (int i = 0; i < order.length; i++) {
	        order[i] = i;
	    }

	    Random rng = new Random(91000L + safeRegion * 7919L);

	    for (int i = order.length - 1; i > 0; i--) {
	        int j = rng.nextInt(i + 1);

	        int tmp = order[i];
	        order[i] = order[j];
	        order[j] = tmp;
	    }

	    ColorPair[] pairs = new ColorPair[4];

	    for (int i = 0; i < 4; i++) {
	        int[] a = SPECIAL_WALL_COLORS[order[i * 2]];
	        int[] b = SPECIAL_WALL_COLORS[order[i * 2 + 1]];

	        pairs[i] = new ColorPair(
	                a[0], a[1], a[2],
	                b[0], b[1], b[2]
	        );
	    }

	    regionSpecialWallPairs[safeRegion] = pairs;
	    return pairs;
	}
	
	private boolean shouldSkipDynamicTriangle(Triangle t) {
	    if (t == null || SceneGenerator.world == null) {
	        return false;
	    }

	    // Open removable barrier:
	    // skip only the vertical wall faces.
	    // Horizontal faces are converted to floor/ceiling in getRuntimeTextureForTriangle(...).
	    if (t.faceType == 10 && isOpenBarrierCell(t.gridRow, t.gridCol)) {
	        Vector3 n = t.normal;
	        if (n != null && Math.abs(n.z) < 0.55) {
	            return true;
	        }
	    }

	    return false;
	}
	
	private BufferedImage getRuntimeTextureForTriangle(Triangle t) {
	    if (t == null || SceneGenerator.world == null) {
	        return t == null ? null : t.texture;
	    }

	    int row = t.gridRow;
	    int col = t.gridCol;

	    if (row < 0 || col < 0 ||
	            row >= SceneGenerator.world.rows ||
	            col >= SceneGenerator.world.cols) {
	        return t.texture;
	    }

	    // Pressure plate: pressed/unpressed without rebuilding.
	    if (t.faceType == 9) {
	        Trigger trigger = SceneGenerator.world.getTriggerAt(row, col);
	        if (trigger != null && trigger.hasFired) {
	            return pressedPressurePlateTexture;
	        }
	        return pressurePlateTexture;
	    }

	    // Open barrier: turn horizontal faces into normal floor/ceiling.
	    if (t.faceType == 10 && isOpenBarrierCell(row, col)) {
	        double cz = (t.v0.pos.z + t.v1.pos.z + t.v2.pos.z) / 3.0;
	        int visualRegion = resolveVisualZoneForFace(0, row, col);

	        if (cz < cam.CELL * 0.5) {
	            return regionFloorTextures[visualRegion];
	        } else {
	            return regionCeilingTextures[visualRegion];
	        }
	    }

	    // Colored key floor replacement -> restore floor after collected.
	    if (t.faceType >= 15 && t.faceType <= 18) {
	        Key key = SceneGenerator.world.getKeyAt(row, col);

	        if (key == null || key.isCollected) {
	            int visualRegion = resolveVisualZoneForFace(0, row, col);
	            return regionFloorTextures[visualRegion];
	        }

	        return t.texture;
	    }

	    // Skeleton key floor replacement -> restore floor after collected.
	    if (t.faceType == 19) {
	        if (!SceneGenerator.hasVisibleSkeletonKeyAt(row, col)) {
	            int visualRegion = resolveVisualZoneForFace(0, row, col);
	            return regionFloorTextures[visualRegion];
	        }

	        return t.texture;
	    }

	    // Bonus node floor replacement -> restore floor after collected.
	    if (t.faceType == 20) {
	        if (!SceneGenerator.hasVisibleBonusNodeAt(row, col)) {
	            int visualRegion = resolveVisualZoneForFace(0, row, col);
	            return regionFloorTextures[visualRegion];
	        }

	        return t.texture;
	    }

	    return t.texture;
	}
	
	private boolean isOpenBarrierCell(int row, int col) {
	    if (SceneGenerator.world == null) {
	        return false;
	    }

	    if (row < 0 || col < 0 ||
	            row >= SceneGenerator.world.rows ||
	            col >= SceneGenerator.world.cols) {
	        return false;
	    }

	    String barrierId = SceneGenerator.world.cells[row][col].barrierId;
	    if (barrierId == null) {
	        return false;
	    }

	    Barrier barrier = SceneGenerator.world.barriersById.get(barrierId);
	    return barrier != null && barrier.isOpen;
	}
	
	private boolean isSolidCell(int row, int col) {
	    if (SceneGenerator.world == null) {
	        return true;
	    }

	    if (!SceneGenerator.world.inBounds(row, col)) {
	        return true;
	    }

	    if (isClosedBarrierCell(row, col)) {
	        return true;
	    }

	    if (isOpenBarrierCell(row, col)) {
	        return false;
	    }

	    Door door = SceneGenerator.world.getDoorAt(row, col);
	    if (door != null && !door.isUnlocked) {
	        return true;
	    }

	    if (!SceneGenerator.world.isBaseWalkable(row, col)) {
	        return true;
	    }

	    return SceneGenerator.world.isSolid(row, col);
	}
	
	private boolean isClosedBarrierCell(int row, int col) {
	    if (SceneGenerator.world == null) {
	        return true;
	    }

	    if (!SceneGenerator.world.inBounds(row, col)) {
	        return true;
	    }

	    String barrierId = SceneGenerator.world.cells[row][col].barrierId;
	    if (barrierId == null) {
	        return false;
	    }

	    Barrier barrier = SceneGenerator.world.barriersById.get(barrierId);
	    return barrier != null && !barrier.isOpen;
	}

	private void rasterizeClippedPolygon(List<Vertex> poly, Triangle sourceTri, Vector3 viewLight, Matrix4 proj) {
		for (int i = 1; i < poly.size() - 1; i++) {
			Triangle tri = new Triangle(poly.get(0), poly.get(i), poly.get(i + 1), sourceTri.red, sourceTri.green,
					sourceTri.blue, 0, 0, 0, sourceTri.texture);
			tri.copyGridMetadataFrom(sourceTri);

			computeNormal(tri);

			Vector3 center = Main.triangleCenter(tri);
			Vector3 toCamera = new Vector3(-center.x, -center.y, -center.z).normalize();

			double facing = tri.normal.normalize().dot(toCamera);
			if (facing <= 1e-8) {
				continue;
			}

			Vector3 faceNormal = tri.normal.normalize();

			float invDepth0 = (float) (-1.0 / tri.v0.pos.z);
			float invDepth1 = (float) (-1.0 / tri.v1.pos.z);
			float invDepth2 = (float) (-1.0 / tri.v2.pos.z);

			Triangle projTri = transform(tri, proj);

			if (projTri.v0.pos.w <= 0.0 || projTri.v1.pos.w <= 0.0 || projTri.v2.pos.w <= 0.0) {
				continue;
			}

			perspectiveDivide(projTri);
			screenMap(projTri);

			rp0.setFromVertex(projTri.v0);
			rp1.setFromVertex(projTri.v1);
			rp2.setFromVertex(projTri.v2);

			rp0.invDepth = invDepth0;
			rp1.invDepth = invDepth1;
			rp2.invDepth = invDepth2;

			Lighting.computeLitColorAt(tri.v0.pos.toVec3(), faceNormal, viewLight, tri.red, tri.green, tri.blue, rp0);

			Lighting.computeLitColorAt(tri.v1.pos.toVec3(), faceNormal, viewLight, tri.red, tri.green, tri.blue, rp1);

			Lighting.computeLitColorAt(tri.v2.pos.toVec3(), faceNormal, viewLight, tri.red, tri.green, tri.blue, rp2);

			renderer.drawTriangle(rp0, rp1, rp2, tri.texture);
		}
	}

	private void requestSceneRebuild() {
		rebuildSceneRequested = true;
	}

	private void applyInitialLTurn90() {
		double ninety = Math.toRadians(90.0);

		Matrix4 rotStep = Matrix4.rotationY(-ninety);

		cam.rot = Matrix4.mul(cam.rot, rotStep);

		onRotate(rotStep);

		cam.rot = orthonormalizeRotation(cam.rot);
	}

	private void processPendingSceneRebuild() {
		if (!rebuildSceneRequested) {
			return;
		}

		SceneGenerator.rebuildFacesFromWorld();
		rebuildSceneFromWorld();
		rebuildSceneRequested = false;
	}

	private Vector3 rotateDirectionToView(Vector3 dir) {
		Matrix4 rotOnly = Matrix4.transpose(cam.rot);
		Vector4 v = rotOnly.transform(dir);
		return new Vector3(v.x, v.y, v.z).normalize();
	}

	public Main(int w, int h) {
		img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		renderer = new ZBufferRenderer(w, h);
		image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

		SceneGenerator.configureForLevel(currentLevel);
		SceneGenerator.generateCurrentLevelWorld();

		wallTexture1 = TextureFactory.makeBrickTexture(64, 64);
		wallTexture2 = TextureFactory.makeBrickTextureAlt(64, 64);
		wallTexture3 = TextureFactory.makeStoneTexture(64, 64);
		wallTexture4 = TextureFactory.makeMetalPanelTexture(64, 64);

		floorTexture = TextureFactory.makeCheckerTexture(64, 64, 8, 90, 90, 90, 130, 130, 130);
		ceilingTexture = TextureFactory.makeStoneTextureDark(64, 64);
		metalTexture = TextureFactory.makeMetalPanelTexture(64, 64);
		exitTexture = TextureFactory.makeExitTexture(64, 64);
		
		buildRegionalTextures();
		buildWallGradientTextures();
		buildWallAccentTextures();
		buildRainbowWallTextures();
		buildLandmarkCueTextures();
		buildDoorAndKeyTextures();

		pressurePlateTexture = TextureFactory.makePressurePlateTexture(64, 64);
		removableWallTexture = TextureFactory.makeRemovableWallTexture(64, 64);
		pressedPressurePlateTexture = TextureFactory.makePressedPressurePlateTexture(64, 64);

		rebuildSceneFromWorld();

		setFocusable(true);
		installKeyBindings();

		new Thread(this).start();
	}
	
	private void buildRainbowWallTextures() {
	    rainbowWallTextures = new BufferedImage[24];

	    int[][] pairs = {
	            { 80, 36, 20, 255, 210, 60 },    // brown / yellow
	            { 120, 24, 18, 255, 90, 40 },    // red / orange
	            { 22, 70, 150, 90, 230, 255 },   // blue / cyan
	            { 28, 90, 42, 170, 255, 90 },    // green / lime
	            { 90, 30, 120, 230, 120, 255 },  // purple / lavender
	            { 110, 24, 78, 255, 90, 190 },   // magenta / pink
	            { 20, 92, 92, 100, 255, 210 },   // teal / mint
	            { 120, 80, 14, 255, 245, 90 },   // ochre / lemon
	            { 92, 50, 22, 255, 170, 105 },   // bark / peach
	            { 44, 60, 125, 180, 210, 255 },  // indigo / ice
	            { 72, 34, 100, 255, 150, 255 },  // plum / violet
	            { 52, 92, 26, 230, 255, 120 },   // olive / chartreuse

	            { 105, 34, 28, 255, 185, 85 },   // clay / apricot
	            { 18, 82, 130, 120, 255, 170 },  // ocean / seafoam
	            { 68, 36, 130, 255, 210, 80 },   // violet / gold
	            { 34, 96, 72, 255, 130, 170 },   // jade / rose
	            { 112, 30, 52, 255, 230, 120 },  // crimson / cream
	            { 40, 70, 110, 220, 150, 255 },  // steel / purple
	            { 88, 42, 18, 180, 255, 80 },    // brown / lime
	            { 24, 88, 120, 255, 160, 90 },   // blue teal / orange
	            { 98, 28, 92, 255, 120, 130 },   // grape / coral
	            { 42, 82, 44, 255, 220, 70 },    // forest / gold
	            { 58, 48, 130, 120, 255, 245 },  // deep purple / aqua
	            { 116, 58, 20, 255, 120, 220 }   // burnt orange / pink
	    };

	    for (int i = 0; i < rainbowWallTextures.length; i++) {
	        int[] p = pairs[i];

	        rainbowWallTextures[i] = TextureFactory.makeProductionLandmarkWall(
	                64, 64,
	                p[0], p[1], p[2],
	                p[3], p[4], p[5],
	                30000 + i * 37
	        );
	    }
	}
	
	private void buildWallAccentTextures() {
	    regionWallAccentTextures = new BufferedImage[16][4];

	    for (int region = 0; region < 16; region++) {
	        MaterialTheme mat = getMaterialForRegion(region);
	        int familyVariant = Math.floorDiv(region, 6);
	        ColorPair pair = getWallColorPair(mat, familyVariant);

	        int[] landmarkColorA = SPECIAL_WALL_COLORS[
	                Math.floorMod(region * 3 + 1, SPECIAL_WALL_COLORS.length)
	        ];

	        int[] landmarkColorB = SPECIAL_WALL_COLORS[
	                Math.floorMod(region * 5 + 4, SPECIAL_WALL_COLORS.length)
	        ];

	        int[] landmarkColorC = SPECIAL_WALL_COLORS[
	                Math.floorMod(region * 7 + 2, SPECIAL_WALL_COLORS.length)
	        ];

	        regionWallAccentTextures[region][0] =
	                makeWallAccent(pair, landmarkColorA, 0.24, 2.35, 14000 + region * 10 + 0);

	        regionWallAccentTextures[region][1] =
	                makeWallAccent(pair, landmarkColorB, 0.28, 2.55, 14000 + region * 10 + 1);

	        regionWallAccentTextures[region][2] =
	                makeWallAccent(pair, landmarkColorA, 0.20, 2.80, 14000 + region * 10 + 2);

	        regionWallAccentTextures[region][3] =
	                makeWallAccent(pair, landmarkColorC, 0.18, 3.00, 14000 + region * 10 + 3);
	    }
	}
	
	private BufferedImage chooseCalmWallTexture(
	        BufferedImage[] set,
	        int region,
	        int patternRegion,
	        int subzone,
	        int row,
	        int col,
	        int faceType
	) {
	    if (set == null || set.length == 0) {
	        return wallTexture1;
	    }

	    int variant = Math.floorMod(
	            row * 73856093 ^
	            col * 19349663 ^
	            faceType * 83492791 ^
	            patternRegion * 31,
	            set.length
	    );

	    return getCachedRegionIdentityTexture(
	            set[variant],
	            region,
	            patternRegion,
	            subzone
	    );
	}
	
	private BufferedImage makeWallAccent(
	        ColorPair pair,
	        int[] landmarkColor,
	        double lowScale,
	        double highScale,
	        int seed
	) {
	    /*
	     * Landmark visibility pass:
	     * - Landmarks should interrupt the normal wall rhythm.
	     * - They should read clearly while moving, not only while stopped.
	     * - Base region color is preserved only lightly so landmarks still belong
	     *   to the environment without blending into it.
	     */

	    int r1 = clampColor((int)Math.round(
	            pair.r1 * lowScale * 0.12 +
	            landmarkColor[0] * 0.88
	    ));
	    int g1 = clampColor((int)Math.round(
	            pair.g1 * lowScale * 0.12 +
	            landmarkColor[1] * 0.88
	    ));
	    int b1 = clampColor((int)Math.round(
	            pair.b1 * lowScale * 0.12 +
	            landmarkColor[2] * 0.88
	    ));

	    int r2 = clampColor((int)Math.round(
	            pair.r2 * highScale * 0.06 +
	            landmarkColor[0] * 0.94
	    ));
	    int g2 = clampColor((int)Math.round(
	            pair.g2 * highScale * 0.06 +
	            landmarkColor[1] * 0.94
	    ));
	    int b2 = clampColor((int)Math.round(
	            pair.b2 * highScale * 0.06 +
	            landmarkColor[2] * 0.94
	    ));

	    // Extra punch: brighten the high end so the landmark breaks the wall pattern.
	    r2 = clampColor((int)Math.round(r2 * 1.28));
	    g2 = clampColor((int)Math.round(g2 * 1.28));
	    b2 = clampColor((int)Math.round(b2 * 1.28));

	    // Extra depth: darken the low end so the texture has stronger contrast.
	    r1 = clampColor((int)Math.round(r1 * 0.72));
	    g1 = clampColor((int)Math.round(g1 * 0.72));
	    b1 = clampColor((int)Math.round(b1 * 0.72));

	    return TextureFactory.makeProductionLandmarkWall(
	            64, 64,
	            r1, g1, b1,
	            r2, g2, b2,
	            seed
	    );
	}
	
	private void startLevel(int level) {
		currentLevel = Math.max(1, Math.min(level, maxLevels));

		SceneGenerator.configureForLevel(currentLevel);
		SceneGenerator.generateCurrentLevelWorld();

		inventoryKeyCounts.clear();
		interactionMessages.clear();

		skeletonKeyCount = 0;
		skeletonKeyUseConfirm = false;
		skeletonConfirmDoorId = -1;

		resetWarpProgress();
		resetLevelStats();

		rebuildSceneFromWorld();
		positionPlayerAtWorldStart();
		resetPlayerAdvanceTracking();

		wKey = sKey = aKey = dKey = zKey = xKey = false;
		iKey = jKey = kKey = lKey = nKey = mKey = false;

		finished = false;
		levelCompleteAwaitingContinue = false;
		pendingNextLevel = -1;

		showInteractionMessage("Level " + currentLevel);
	}

	private void positionPlayerAtWorldStart() {
		cam.angle = Math.toRadians(5);
		cam.rot = Matrix4.mul(Matrix4.rotationZ(0), Matrix4.rotationX(Math.toRadians(90)));

		cam.CELL = 4.0;
		cam.PLAYER_HEIGHT = 2.0;

		applyInitialLTurn90();

		double worldX = SceneGenerator.world.startRow * cam.CELL + cam.CELL / 2.0;
		double worldY = SceneGenerator.world.startCol * cam.CELL + cam.CELL / 2.0;

		Vector3 local = worldToCameraLocal(worldX, worldY, cam.PLAYER_HEIGHT);
		cam.pos = new Vector3(local.x, cam.PLAYER_HEIGHT, local.z);
	}

	private void debugWarpPlayerNearExit() {
		if (SceneGenerator.world == null) {
			showInteractionMessage("DEBUG: no world loaded");
			return;
		}

		CellPos exit = findExitCell();
		if (exit == null) {
			showInteractionMessage("DEBUG: exit not found");
			return;
		}

		CellPos target = findWalkableCellNearExit(exit, 5);
		if (target == null) {
			target = findWalkableCellNearExit(exit, 3);
		}
		if (target == null) {
			target = findNearestWalkableCellToExit(exit);
		}
		if (target == null) {
			showInteractionMessage("DEBUG: no safe cell near exit");
			return;
		}

		double worldX = target.row * cam.CELL + cam.CELL / 2.0;
		double worldY = target.col * cam.CELL + cam.CELL / 2.0;

		Vector3 local = worldToCameraLocal(worldX, worldY, cam.PLAYER_HEIGHT);
		cam.pos = new Vector3(local.x, cam.PLAYER_HEIGHT, local.z);

		playerHasAdvancedOneCellThisLevel = true;
		warpRecentlyUsed = false;

		int region = SceneGenerator.world.getRegionIndex(target.row, target.col);
		showInteractionMessage("DEBUG: warped near exit at (" + target.row + ", " + target.col + ") region " + region);
	}

	private CellPos findExitCell() {
		if (SceneGenerator.world == null) {
			return null;
		}

		for (int r = 0; r < SceneGenerator.world.rows; r++) {
			for (int c = 0; c < SceneGenerator.world.cols; c++) {
				if (SceneGenerator.world.isExit(r, c)) {
					return new CellPos(r, c);
				}
			}
		}

		return null;
	}

	private CellPos findWalkableCellNearExit(CellPos exit, int preferredStepsAway) {
		if (SceneGenerator.world == null || exit == null) {
			return null;
		}

		int rows = SceneGenerator.world.rows;
		int cols = SceneGenerator.world.cols;

		boolean[][] seen = new boolean[rows][cols];
		int[][] dist = new int[rows][cols];

		ArrayDeque<CellPos> q = new ArrayDeque<>();
		q.add(exit);
		seen[exit.row][exit.col] = true;

		CellPos best = null;
		int bestDist = -1;

		int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

		while (!q.isEmpty()) {
			CellPos cur = q.removeFirst();
			int curDist = dist[cur.row][cur.col];

			if (curDist > preferredStepsAway + 4) {
				continue;
			}

			if (!SceneGenerator.world.isExit(cur.row, cur.col) && isSafeDebugWarpCell(cur.row, cur.col)) {
				if (curDist == preferredStepsAway) {
					return cur;
				}

				if (best == null || Math.abs(curDist - preferredStepsAway) < Math.abs(bestDist - preferredStepsAway)) {
					best = cur;
					bestDist = curDist;
				}
			}

			for (int[] d : dirs) {
				int nr = cur.row + d[0];
				int nc = cur.col + d[1];

				if (!SceneGenerator.world.inBounds(nr, nc) || seen[nr][nc]) {
					continue;
				}

				if (!SceneGenerator.world.isExit(nr, nc) && !isSafeDebugWarpCell(nr, nc)) {
					continue;
				}

				seen[nr][nc] = true;
				dist[nr][nc] = curDist + 1;
				q.addLast(new CellPos(nr, nc));
			}
		}

		return best;
	}

	private CellPos findNearestWalkableCellToExit(CellPos exit) {
		if (SceneGenerator.world == null || exit == null) {
			return null;
		}

		CellPos best = null;
		int bestDistance = Integer.MAX_VALUE;

		for (int r = 0; r < SceneGenerator.world.rows; r++) {
			for (int c = 0; c < SceneGenerator.world.cols; c++) {
				if (!isSafeDebugWarpCell(r, c)) {
					continue;
				}

				int dist = Math.abs(r - exit.row) + Math.abs(c - exit.col);
				if (dist < bestDistance) {
					bestDistance = dist;
					best = new CellPos(r, c);
				}
			}
		}

		return best;
	}

	private boolean isSafeDebugWarpCell(int row, int col) {
		return SceneGenerator.world != null && SceneGenerator.world.inBounds(row, col)
				&& SceneGenerator.world.isBaseWalkable(row, col) && !SceneGenerator.world.isSolid(row, col)
				&& !SceneGenerator.world.isExit(row, col);
	}

	private void buildLandmarkCueTextures() {
	    landmarkCueTextures = new BufferedImage[4][3];

	    // MUCH higher contrast + brightness

	    // Landmark 0 - bright gold
	    landmarkCueTextures[0][0] = TextureFactory.makeProductionLandmarkWall(64, 64, 180, 90, 10, 255, 240, 80, 600);
	    landmarkCueTextures[0][1] = TextureFactory.makeProductionLandmarkWall(64, 64, 140, 60, 10, 255, 200, 60, 601);
	    landmarkCueTextures[0][2] = TextureFactory.makeProductionLandmarkWall(64, 64, 100, 40, 5, 255, 255, 120, 602);

	    // Landmark 1 - glowing cyan
	    landmarkCueTextures[1][0] = TextureFactory.makeProductionLandmarkWall(64, 64, 10, 80, 180, 120, 255, 255, 610);
	    landmarkCueTextures[1][1] = TextureFactory.makeProductionLandmarkWall(64, 64, 10, 60, 140, 90, 220, 255, 611);
	    landmarkCueTextures[1][2] = TextureFactory.makeProductionLandmarkWall(64, 64, 10, 100, 160, 150, 255, 255, 612);

	    // Landmark 2 - bright white/violet
	    landmarkCueTextures[2][0] = TextureFactory.makeProductionLandmarkWall(64, 64, 120, 100, 200, 255, 255, 255, 620);
	    landmarkCueTextures[2][1] = TextureFactory.makeProductionLandmarkWall(64, 64, 100, 90, 180, 240, 220, 255, 621);
	    landmarkCueTextures[2][2] = TextureFactory.makeProductionLandmarkWall(64, 64, 140, 120, 220, 255, 255, 255, 622);

	    // Landmark 3 - neon green
	    landmarkCueTextures[3][0] = TextureFactory.makeProductionLandmarkWall(64, 64, 20, 140, 40, 120, 255, 120, 630);
	    landmarkCueTextures[3][1] = TextureFactory.makeProductionLandmarkWall(64, 64, 10, 110, 50, 100, 240, 150, 631);
	    landmarkCueTextures[3][2] = TextureFactory.makeProductionLandmarkWall(64, 64, 40, 170, 20, 170, 255, 100, 632);
	}

	private void installKeyBindings() {
		InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = getActionMap();

		bindHoldKey(im, am, "W", () -> wKey = true, () -> wKey = false);
		bindHoldKey(im, am, "S", () -> sKey = true, () -> sKey = false);
		bindHoldKey(im, am, "A", () -> aKey = true, () -> aKey = false);
		bindHoldKey(im, am, "D", () -> dKey = true, () -> dKey = false);
		bindHoldKey(im, am, "Z", () -> zKey = true, () -> zKey = false);
		bindHoldKey(im, am, "X", () -> xKey = true, () -> xKey = false);

		bindHoldKey(im, am, "I", () -> iKey = true, () -> iKey = false);
		bindHoldKey(im, am, "J", () -> jKey = true, () -> jKey = false);
		bindHoldKey(im, am, "K", () -> kKey = true, () -> kKey = false);
		bindHoldKey(im, am, "L", () -> lKey = true, () -> lKey = false);
		bindHoldKey(im, am, "N", () -> nKey = true, () -> nKey = false);
		bindHoldKey(im, am, "M", () -> mKey = true, () -> mKey = false);

		bindHoldKey(im, am, "SPACE", () -> spaceKey = true, () -> spaceKey = false);

		bindTapKey(im, am, "H", () -> showHud = !showHud);
		bindTapKey(im, am, "V", () -> showInventory = !showInventory);

		// IMPORTANT: defer gameplay interaction to the render/game thread
		bindTapKey(im, am, "E", () -> interactRequested = true);

		// ---------------------------------------------------------
		// DEBUG REGION PREVIEW MODE
		// F6 = toggle preview mode
		// F7 = previous region
		// F8 = next region
		// ---------------------------------------------------------
		//bindTapKey(im, am, "F6", () -> {
		//	showInteractionMessage("Dots left: " + getEffectiveRemainingDotCount());
		//});
		bindTapKey(im, am, "F9", () -> {
			debugForceAllDotsCollected = !debugForceAllDotsCollected;
			showInteractionMessage(debugForceAllDotsCollected ? "DEBUG: all dots forced collected"
					: "DEBUG: normal dot count restored");
		});
		bindTapKey(im, am, "F11", () -> debugWarpNearExitRequested = true);
		//bindTapKey(im, am, "F7", () -> cycleDebugPreviewRegion(-1));
		//bindTapKey(im, am, "F8", () -> cycleDebugPreviewRegion(1));
		bindTapKey(im, am, "F10", () -> debugNextLevelRequested = true);
		
		bindTapKey(im, am, "F6", () -> toggleDebugRegionPreview());
		bindTapKey(im, am, "F7", () -> cycleDebugPreviewRegion(-1));
		bindTapKey(im, am, "F8", () -> cycleDebugPreviewRegion(1));
	}

	private void bindHoldKey(InputMap im, ActionMap am, String key, Runnable onPress, Runnable onRelease) {
		String pressedName = key + "_PRESSED";
		String releasedName = key + "_RELEASED";

		im.put(KeyStroke.getKeyStroke("pressed " + key), pressedName);
		im.put(KeyStroke.getKeyStroke("released " + key), releasedName);

		am.put(pressedName, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				onPress.run();
			}
		});

		am.put(releasedName, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				onRelease.run();
			}
		});
	}

	private void bindTapKey(InputMap im, ActionMap am, String key, Runnable action) {
		String actionName = key + "_TAP";

		im.put(KeyStroke.getKeyStroke(key), actionName);

		am.put(actionName, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				action.run();
			}
		});
	}
	
	private ColorPair getWallColorPair(MaterialTheme mat, int familyVariant) {
	    switch (mat) {
	        case EARTH:
	            if (familyVariant == 0) return new ColorPair(42, 118, 54, 120, 210, 92);      // vivid green
	            if (familyVariant == 1) return new ColorPair(92, 74, 32, 188, 150, 58);       // olive/gold
	            return new ColorPair(48, 92, 72, 116, 188, 140);                              // moss teal

	        case FIRE:
	            if (familyVariant == 0) return new ColorPair(150, 32, 20, 255, 112, 28);      // red/orange
	            if (familyVariant == 1) return new ColorPair(118, 24, 62, 238, 72, 128);      // magenta ember
	            return new ColorPair(142, 68, 18, 255, 170, 42);                              // amber

	        case WATER:
	            if (familyVariant == 0) return new ColorPair(18, 92, 170, 60, 220, 255);      // blue/cyan
	            if (familyVariant == 1) return new ColorPair(18, 54, 132, 70, 140, 255);      // deep blue
	            return new ColorPair(14, 126, 132, 68, 238, 210);                             // teal

	        case STONE:
	            if (familyVariant == 0) return new ColorPair(92, 82, 176, 178, 132, 238);     // violet stone
	            if (familyVariant == 1) return new ColorPair(70, 112, 168, 144, 190, 238);    // blue stone
	            return new ColorPair(116, 84, 132, 214, 144, 214);                            // rose stone

	        case SMOKE:
	            if (familyVariant == 0) return new ColorPair(84, 56, 132, 182, 106, 220);     // purple smoke
	            if (familyVariant == 1) return new ColorPair(52, 80, 142, 126, 154, 230);     // blue smoke
	            return new ColorPair(108, 54, 112, 220, 104, 184);                            // plum

	        case WOOD:
	            if (familyVariant == 0) return new ColorPair(132, 72, 24, 238, 158, 54);      // warm wood
	            if (familyVariant == 1) return new ColorPair(92, 112, 30, 194, 184, 58);      // yellow-green wood
	            return new ColorPair(144, 54, 28, 238, 112, 58);                              // redwood

	        default:
	            return new ColorPair(80, 80, 160, 170, 130, 230);
	    }
	}
	
	private BufferedImage createMaterialWallToneVariant(
	        MaterialTheme mat,
	        int region,
	        int variant,
	        int tone
	) {
	    int seed = 12000 + region * 1000 + tone * 100 + variant;

	    ColorPair pair = getWallTileColorPair(mat, region, variant, tone);

	    double toneDarkScale;
	    double toneBrightScale;

	    switch (tone) {
	        case 0:
	            toneDarkScale = 0.74;
	            toneBrightScale = 0.88;
	            break;
	        case 1:
	            toneDarkScale = 0.86;
	            toneBrightScale = 0.98;
	            break;
	        case 2:
	            toneDarkScale = 1.00;
	            toneBrightScale = 1.00;
	            break;
	        case 3:
	            toneDarkScale = 1.08;
	            toneBrightScale = 1.12;
	            break;
	        case 4:
	            toneDarkScale = 1.18;
	            toneBrightScale = 1.24;
	            break;
	        default:
	            toneDarkScale = 1.0;
	            toneBrightScale = 1.0;
	            break;
	    }

	    int r1 = clampColor((int) Math.round(pair.r1 * toneDarkScale));
	    int g1 = clampColor((int) Math.round(pair.g1 * toneDarkScale));
	    int b1 = clampColor((int) Math.round(pair.b1 * toneDarkScale));

	    int r2 = clampColor((int) Math.round(pair.r2 * toneBrightScale));
	    int g2 = clampColor((int) Math.round(pair.g2 * toneBrightScale));
	    int b2 = clampColor((int) Math.round(pair.b2 * toneBrightScale));

	    return TextureFactory.makeProductionLandmarkWall(
	            64, 64,
	            r1, g1, b1,
	            r2, g2, b2,
	            seed
	    );
	}
	
	private void buildWallGradientTextures() {
	    regionWallGradientTextures = new BufferedImage[16][5][8];

	    for (int region = 0; region < 16; region++) {
	        MaterialTheme mat = getMaterialForRegion(region);

	        for (int tone = 0; tone < 5; tone++) {
	            for (int variant = 0; variant < 8; variant++) {
	                regionWallGradientTextures[region][tone][variant] =
	                        createMaterialWallToneVariant(mat, region, variant, tone);
	            }
	        }
	    }
	}
	
	private ColorPair getWallTileColorPair(
	        MaterialTheme mat,
	        int region,
	        int variant,
	        int tone
	) {
	    ColorPair[] pairs = getRegionSpecialWallPairs(region);

	    int subzone = computeSubzoneVariant(region + variant * 3, region + tone * 5, region);

	    int dominantIndex = Math.floorMod(region * 3 + subzone, pairs.length);
	    ColorPair dominant = pairs[dominantIndex];

	    int secondaryIndex = Math.floorMod(dominantIndex + 1 + Math.floorMod(subzone + variant, pairs.length), pairs.length);
	    ColorPair secondary = pairs[secondaryIndex];

	    double secondaryAmount;

	    switch (Math.floorMod(subzone, 6)) {
	        case 0:
	            secondaryAmount = 0.12; // mostly parent identity
	            break;
	        case 1:
	            secondaryAmount = 0.28; // noticeable blend
	            break;
	        case 2:
	            secondaryAmount = 0.42; // stronger shift
	            break;
	        case 3:
	            secondaryAmount = 0.18; // subtle return
	            break;
	        case 4:
	            secondaryAmount = 0.36; // strong but cohesive
	            break;
	        case 5:
	            secondaryAmount = 0.24; // medium shift
	            break;
	        default:
	            secondaryAmount = 0.25;
	            break;
	    }

	    double dominantAmount = 1.0 - secondaryAmount;

	    return new ColorPair(
	            clampColor((int) Math.round(dominant.r1 * dominantAmount + secondary.r1 * secondaryAmount)),
	            clampColor((int) Math.round(dominant.g1 * dominantAmount + secondary.g1 * secondaryAmount)),
	            clampColor((int) Math.round(dominant.b1 * dominantAmount + secondary.b1 * secondaryAmount)),

	            clampColor((int) Math.round(dominant.r2 * dominantAmount + secondary.r2 * secondaryAmount)),
	            clampColor((int) Math.round(dominant.g2 * dominantAmount + secondary.g2 * secondaryAmount)),
	            clampColor((int) Math.round(dominant.b2 * dominantAmount + secondary.b2 * secondaryAmount))
	    );
	}
	
	private BufferedImage[] getWallGradientSet(int logicalRegion, int row, int col) {
	    int region = clampRegion(logicalRegion);

	    if (regionWallGradientTextures == null || regionWallGradientTextures[region] == null) {
	        return regionWallTextures[region];
	    }

	    int segmentLength = 9;

	    int sr = Math.floorDiv(row, segmentLength);
	    int sc = Math.floorDiv(col, segmentLength);

	    int segment = Math.floorMod(sr * 2 + sc * 3 + region * 5, 5);

	    return regionWallGradientTextures[region][segment];
	}
	
	private BufferedImage chooseTextureForFace(int faceType, int row, int col) {
	    int visualRegion = resolveVisualZoneForFace(faceType, row, col);
	    int subzone = computeSubzoneVariant(row, col, visualRegion);
	    int logicalRegion = resolveRegionForFace(faceType, row, col);

	    switch (faceType) {
	        case 0:
	        case 6:
	            return regionFloorTextures[visualRegion];

	        case 1:
	        case 7:
	            return regionCeilingTextures[visualRegion];

	        case 8:
	            return exitTexture;

	        case 9:
	            if (SceneGenerator.world != null) {
	                Trigger t = SceneGenerator.world.getTriggerAt(row, col);
	                if (t != null && t.hasFired) {
	                    return pressedPressurePlateTexture;
	                }
	            }
	            return pressurePlateTexture;

	        case 10:
	            return removableWallTexture;

	        case 11:
	            return coloredDoorTextures[0];
	        case 12:
	            return coloredDoorTextures[1];
	        case 13:
	            return coloredDoorTextures[2];
	        case 14:
	            return coloredDoorTextures[3];

	        case 15:
	            return coloredKeyTextures[0];
	        case 16:
	            return coloredKeyTextures[1];
	        case 17:
	            return coloredKeyTextures[2];
	        case 18:
	            return coloredKeyTextures[3];

	        case 19:
	            return skeletonKeyTexture;

	        case 20:
	            return landmarkCueTextures[0][2];

	        case 2:
	        case 3:
	        case 4:
	        case 5: {
	            if (shouldUseDecorativeLandmarkWall(row, col, faceType, logicalRegion)) {
	                return chooseDecorativeLandmarkWallTexture(row, col, faceType, visualRegion, logicalRegion);
	            }

	            BufferedImage[] set = getWallGradientSet(logicalRegion, row, col);

	            int region = visualRegion;
	            int patternRegion = clampRegion(logicalRegion);

	            return chooseCalmWallTexture(
	                    set,
	                    region,
	                    patternRegion,
	                    subzone,
	                    row,
	                    col,
	                    faceType
	            );
	        }

	        default:
	            return metalTexture;
	    }
	}
	
	private int decorativeLandmarkHash(
	        int row,
	        int col,
	        int faceType,
	        int logicalRegion
	) {
	    int h = 17;

	    h = h * 31 + row * 73856093;
	    h = h * 31 + col * 19349663;
	    h = h * 31 + faceType * 83492791;
	    h = h * 31 + logicalRegion * 265443;

	    h ^= (h >>> 16);
	    h *= 0x7feb352d;
	    h ^= (h >>> 15);
	    h *= 0x846ca68b;
	    h ^= (h >>> 16);

	    return h & 0x7fffffff;
	}
	
	private BufferedImage chooseDecorativeLandmarkWallTexture(
	        int row,
	        int col,
	        int faceType,
	        int visualRegion,
	        int logicalRegion
	) {
	    int region = clampRegion(visualRegion);

	    if (regionWallAccentTextures == null ||
	            regionWallAccentTextures[region] == null ||
	            regionWallAccentTextures[region].length == 0) {
	        BufferedImage[] fallbackSet = getWallGradientSet(logicalRegion, row, col);
	        return chooseCalmWallTexture(
	                fallbackSet,
	                visualRegion,
	                clampRegion(logicalRegion),
	                computeSubzoneVariant(row, col, visualRegion),
	                row,
	                col,
	                faceType
	        );
	    }

	    int hash = decorativeLandmarkHash(row, col, faceType, logicalRegion);
	    int variant = Math.floorMod(hash / 31, regionWallAccentTextures[region].length);

	    return regionWallAccentTextures[region][variant];
	}
	
	private boolean shouldUseDecorativeLandmarkWall(
	        int row,
	        int col,
	        int faceType,
	        int logicalRegion
	) {
	    if (faceType < 2 || faceType > 5) {
	        return false;
	    }

	    int hash = decorativeLandmarkHash(row, col, faceType, logicalRegion);

	    /*
	     * Landmark readability pass:
	     *
	     * Late levels need landmarks to be noticed while moving.
	     * These values intentionally increase landmark frequency without adding
	     * new systems or changing maze generation.
	     */
	    int rarity;
	    if (currentLevel >= 9) {
	        rarity = 6;
	    } else if (currentLevel >= 7) {
	        rarity = 8;
	    } else {
	        rarity = 10;
	    }

	    if (Math.floorMod(hash, rarity) != 0) {
	        return false;
	    }

	    /*
	     * Light anti-clutter filter.
	     * Keeps landmarks from appearing on every nearby wall face.
	     */
	    return Math.floorMod(row * 11 + col * 17 + faceType * 5 + logicalRegion * 3, 6) != 0;
	}
	
	boolean finished = false;

	private int resolveRegionForFace(int faceType, int row, int col) {
		if (isDebugRegionPreviewEnabled()) {
			return getDebugPreviewRegionIndex();
		}

		if (SceneGenerator.world == null) {
			return 0;
		}

		row = Math.max(0, Math.min(row, SceneGenerator.world.rows - 1));
		col = Math.max(0, Math.min(col, SceneGenerator.world.cols - 1));

		if (faceType == 0 || faceType == 1 || faceType == 6 || faceType == 7 || faceType == 8 || faceType == 9
				|| (faceType >= 15 && faceType <= 18)) {
			return clampRegion(SceneGenerator.world.getRegionIndex(row, col));
		}

		int[] counts = new int[16];

		for (int dr = -1; dr <= 1; dr++) {
			for (int dc = -1; dc <= 1; dc++) {
				int nr = row + dr;
				int nc = col + dc;

				if (!SceneGenerator.world.inBounds(nr, nc)) {
					continue;
				}
				if (!SceneGenerator.world.isBaseWalkable(nr, nc)) {
					continue;
				}

				int region = SceneGenerator.world.getRegionIndex(nr, nc);
				if (region >= 0 && region < counts.length) {
					counts[region]++;
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

	private int clampRegion(int region) {
		return Math.floorMod(region, 16);
	}

	private int resolveVisualZoneForFace(int faceType, int row, int col) {
		if (isDebugRegionPreviewEnabled()) {
			return clampRegion(getDebugPreviewRegionIndex());
		}

		int logicalRegion = resolveRegionForFace(faceType, row, col);

		if (SceneGenerator.world == null) {
			return clampRegion(logicalRegion);
		}

		row = Math.max(0, Math.min(row, SceneGenerator.world.rows - 1));
		col = Math.max(0, Math.min(col, SceneGenerator.world.cols - 1));

		return resolveVisualZone(logicalRegion, row, col);
	}

	private int resolveVisualZone(int logicalRegion, int row, int col) {
	    int baseRegion = clampRegion(logicalRegion);

	    // IMPORTANT:
	    // return base region ALWAYS (pattern identity preserved)
	    // subzone will only affect tinting elsewhere
	    return baseRegion;
	}
	
	private int computeSubzoneVariant(int row, int col, int logicalRegion) {
	    int zoneSize;

	    /*
	     * Subregions:
	     * Frequent enough to create local identity, but still large enough
	     * to feel like a place instead of noise.
	     */
	    if (currentLevel >= 9) {
	        zoneSize = 7;
	    } else if (currentLevel >= 7) {
	        zoneSize = 8;
	    } else {
	        zoneSize = 10;
	    }

	    int zr = Math.floorDiv(row, zoneSize);
	    int zc = Math.floorDiv(col, zoneSize);

	    return Math.floorMod(zr * 3 + zc * 5 + logicalRegion * 7, 6);
	}

	private BufferedImage getCachedRegionIdentityTexture(
	        BufferedImage src,
	        int visualRegion,
	        int logicalRegion,
	        int subzoneVariant
	) {
	    /*
	     * UNDO wall remapping experiment.
	     *
	     * Return the original generated wall texture exactly as-is.
	     * This restores the prior two-color texture behavior and removes the
	     * failed single-color wall effect.
	     */
	    return src;
	}

	private boolean areDotsEffectivelyCollected() {
		return debugForceAllDotsCollected || SceneGenerator.areAllDotsCollected();
	}

	private int getEffectiveCollectedDotCount() {
		if (debugForceAllDotsCollected) {
			return SceneGenerator.getTotalDotCount();
		}
		return SceneGenerator.getCollectedDotCount();
	}

	private void drawHud(Graphics2D g2) {
	    if (!showHud)
	        return;

	    int row = getPlayerRow();
	    int col = getPlayerCol();

	    int intFps = (int) fps;

	    List<String> lines = new ArrayList<>();

	    // --- Core Info ---
	    lines.add(String.format("Level: %d", currentLevel));

	    if (SceneGenerator.world == null) {
	        lines.add("Region: ?");
	    } else {
	        int currentRegion = SceneGenerator.world.getRegionIndex(row, col);
	        lines.add(String.format("Region: %d", currentRegion));
	    }

	    lines.add("FPS: " + intFps);

	    // --- Controls ---
	    lines.add("Move: I K N M");
	    lines.add("Turn: J L");
	    lines.add("Use: E");
	    lines.add("Reset: SPACE");
	    lines.add("H: Toggle HUD");
	    lines.add("V: Toggle Inventory");

	    // --- Debug (kept but unobtrusive) ---
	    if (debugForceAllDotsCollected) {
	        lines.add("DEBUG: ALL DOTS FORCED");
	    }

	    if (isDebugRegionPreviewEnabled()) {
	        lines.add(String.format("PREVIEW REGION: %d", getDebugPreviewRegionIndex()));
	        lines.add("F6 Toggle Preview");
	        lines.add("F7 Prev Region");
	        lines.add("F8 Next Region");
	    }

	    g2.setFont(new Font("Monospaced", Font.BOLD, 16));

	    FontMetrics fm = g2.getFontMetrics();
	    int lineHeight = fm.getHeight();
	    int x = 12;
	    int y = 20;

	    int maxWidth = 0;
	    for (String line : lines) {
	        maxWidth = Math.max(maxWidth, fm.stringWidth(line));
	    }

	    int boxWidth = maxWidth + 20;
	    int boxHeight = lineHeight * lines.size() + 14;

	    g2.setColor(new Color(0, 0, 0, 170));
	    g2.fillRoundRect(8, 8, boxWidth, boxHeight, 12, 12);

	    g2.setColor(Color.WHITE);

	    for (String line : lines) {
	        g2.drawString(line, x, y);
	        y += lineHeight;
	    }
	}

	private void drawExitOverlay(Graphics2D g2) {
		if (!finished)
			return;

		g2.setFont(new Font("Monospaced", Font.BOLD, 34));

		String msg = "ALL " + maxLevels + " LEVELS CLEARED";

		FontMetrics fmBig = g2.getFontMetrics();
		int textWidth = fmBig.stringWidth(msg);
		int textHeight = fmBig.getHeight();

		int centerX = (getWidth() - textWidth) / 2;
		int centerY = getHeight() / 2;

		g2.setColor(new Color(0, 0, 0, 200));
		g2.fillRoundRect(centerX - 20, centerY - textHeight, textWidth + 40, textHeight + 20, 20, 20);

		g2.setColor(Color.GREEN);
		g2.drawString(msg, centerX, centerY);
	}

	private char getCurrentCellType() {
		int row = getPlayerRow();
		int col = getPlayerCol();

		if (SceneGenerator.world == null || !SceneGenerator.world.inBounds(row, col)) {
			return '?';
		}

		if (SceneGenerator.world.isExit(row, col)) {
			return 'E';
		}

		return SceneGenerator.world.isSolid(row, col) ? 'X' : 'O';
	}

	private int getPlayerRow() {
		Vector3 worldPos = cameraLocalToWorld(cam.pos.x, cam.pos.y, cam.pos.z);
		return (int) Math.floor(worldPos.x / cam.CELL);
	}

	private int getPlayerCol() {
		Vector3 worldPos = cameraLocalToWorld(cam.pos.x, cam.pos.y, cam.pos.z);
		return (int) Math.floor(worldPos.y / cam.CELL);
	}

	private void pruneExpiredInteractionMessages() {
		long now = System.nanoTime();
		while (!interactionMessages.isEmpty()) {
			InteractionMessage first = interactionMessages.peekFirst();
			if (first != null && first.expiresAtNanos <= now) {
				interactionMessages.removeFirst();
			} else {
				break;
			}
		}
	}

	private void showInteractionMessage(String msg) {
		pruneExpiredInteractionMessages();

		long expiresAt = System.nanoTime() + INTERACTION_MESSAGE_DURATION_NANOS;
		interactionMessages.addLast(new InteractionMessage(msg, expiresAt));

		while (interactionMessages.size() > 64) {
			interactionMessages.removeFirst();
		}
	}

	private String colorName(int colorIndex) {
		switch (colorIndex) {
		case 0:
			return "Red";
		case 1:
			return "Blue";
		case 2:
			return "Green";
		case 3:
			return "Yellow";
		default:
			return "Unknown";
		}
	}

	private void tryCollectKeyAtPlayerCell() {
	    if (SceneGenerator.world == null || levelCompleteAwaitingContinue || finished) {
	        return;
	    }

	    int row = getPlayerRow();
	    int col = getPlayerCol();

	    if (SceneGenerator.hasVisibleSkeletonKeyAt(row, col)) {
	        if (skeletonKeyCount >= getSkeletonKeyCapacityForCurrentLevel()) {
	            showInteractionMessage("Skeleton key capacity reached");
	            return;
	        }

	        boolean changed = SceneGenerator.collectSkeletonKeyAt(row, col);
	        if (changed) {
	            skeletonKeyCount++;
	            showInteractionMessage("Skeleton key acquired");
	        }
	        return;
	    }

	    Key key = SceneGenerator.world.getKeyAt(row, col);
	    if (key == null || key.isCollected) {
	        return;
	    }

	    boolean changed = SceneGenerator.world.collectKeyAt(row, col);
	    if (!changed) {
	        return;
	    }

	    inventoryKeyCounts.merge(key.colorIndex, 1, Integer::sum);

	    levelKeysCollected++;
	    levelScore++;

	    int count = inventoryKeyCounts.getOrDefault(key.colorIndex, 0);
	    showInteractionMessage("Picked up " + colorName(key.colorIndex) + " key (" + count + ")");
	}

	private void tryCollectDotAtPlayerCell() {
		if (SceneGenerator.world == null || levelCompleteAwaitingContinue || finished) {
			return;
		}

		int row = getPlayerRow();
		int col = getPlayerCol();

		if (SceneGenerator.collectDotAt(row, col)) {
			levelScore++;
		}
	}

	private boolean isPlayerOnOrAdjacentLockedExit() {
		if (SceneGenerator.world == null || areDotsEffectivelyCollected()) {
			return false;
		}
		int row = getPlayerRow();
		int col = getPlayerCol();

		if (SceneGenerator.world.inBounds(row, col) && SceneGenerator.world.isExit(row, col)) {
			return true;
		}
		int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
		for (int[] d : dirs) {
			int nr = row + d[0];
			int nc = col + d[1];
			if (SceneGenerator.world.inBounds(nr, nc) && SceneGenerator.world.isExit(nr, nc)) {
				return true;
			}
		}
		return false;
	}

	private void tryInteract() {
		if (finished) {
			return;
		}

		if (levelCompleteAwaitingContinue) {
			levelCompleteAwaitingContinue = false;

			if (currentLevel < maxLevels) {
				startLevel(pendingNextLevel);
			} else {
				finished = true;
				showInteractionMessage("All levels cleared");
			}

			return;
		}

		if (SceneGenerator.world == null) {
			return;
		}

		int row = getPlayerRow();
		int col = getPlayerCol();

		Door door = SceneGenerator.world.getDoorAt(row, col);
		if (door == null || door.isUnlocked) {
			door = SceneGenerator.world.getAdjacentLockedDoor(row, col);
		}

		if (door == null) {
			if (isPlayerOnOrAdjacentLockedExit()) {
				showInteractionMessage("Exit locked: collect all dots (" + getEffectiveCollectedDotCount() + " / "
						+ SceneGenerator.getTotalDotCount() + ")");
				return;
			}
			skeletonKeyUseConfirm = false;
			skeletonConfirmDoorId = -1;
			showInteractionMessage("No locked door nearby");
			return;
		}

		int count = inventoryKeyCounts.getOrDefault(door.colorIndex, 0);
		if (count > 0) {
			skeletonKeyUseConfirm = false;
			skeletonConfirmDoorId = -1;

			boolean changed = SceneGenerator.world.unlockDoor(door.id);
			if (!changed) {
				return;
			}

			if (count == 1) {
				inventoryKeyCounts.remove(door.colorIndex);
			} else {
				inventoryKeyCounts.put(door.colorIndex, count - 1);
			}

			levelDoorsUnlocked++;
			levelScore++;

			requestSceneRebuild();
			showInteractionMessage(colorName(door.colorIndex) + " door unlocked");
			return;
		}

		if (skeletonKeyCount > 0) {
			int doorConfirmId = door.id.hashCode();

			if (skeletonKeyUseConfirm && skeletonConfirmDoorId == doorConfirmId) {
				boolean changed = SceneGenerator.world.unlockDoor(door.id);
				skeletonKeyUseConfirm = false;
				skeletonConfirmDoorId = -1;

				if (!changed) {
					return;
				}

				skeletonKeyCount--;
				requestSceneRebuild();
				showInteractionMessage("Skeleton key used");
				return;
			} else {
				skeletonKeyUseConfirm = true;
				skeletonConfirmDoorId = doorConfirmId;
				showInteractionMessage("Press E again to use skeleton key");
				return;
			}
		}

		skeletonKeyUseConfirm = false;
		skeletonConfirmDoorId = -1;
		showInteractionMessage("Need " + colorName(door.colorIndex) + " key");
	}

	private void resetToInitialState() {
		if (SceneGenerator.world != null) {
			SceneGenerator.world.resetRuntimeState();
			SceneGenerator.resetSkeletonKeysForLevel();
			SceneGenerator.resetBonusNodesForLevel();
			SceneGenerator.resetDotsForLevel();
			SceneGenerator.rebuildFacesFromWorld();
			rebuildSceneFromWorld();
		}

		inventoryKeyCounts.clear();
		interactionMessages.clear();

		skeletonKeyCount = 0;
		skeletonKeyUseConfirm = false;
		skeletonConfirmDoorId = -1;

		resetWarpProgress();
		resetLevelStats();

		positionPlayerAtWorldStart();
		resetPlayerAdvanceTracking();

		wKey = sKey = aKey = dKey = zKey = xKey = false;
		iKey = jKey = kKey = lKey = nKey = mKey = false;

		finished = false;
		levelCompleteAwaitingContinue = false;
		pendingNextLevel = -1;
	}

	private void resetWarpProgress() {
		highestRegionReached = 0;
		highestRegionEntryRow = -1;
		highestRegionEntryCol = -1;
		warpRecentlyUsed = false;
		warpWasClearedByReset = false;
	}

	private void resetPlayerAdvanceTracking() {
		playerHasAdvancedOneCellThisLevel = false;
		levelStartPlayerRow = getPlayerRow();
		levelStartPlayerCol = getPlayerCol();
	}

	private void updatePlayerAdvanceTracking() {
		if (playerHasAdvancedOneCellThisLevel) {
			return;
		}

		int row = getPlayerRow();
		int col = getPlayerCol();

		if (levelStartPlayerRow < 0 || levelStartPlayerCol < 0) {
			levelStartPlayerRow = row;
			levelStartPlayerCol = col;
			return;
		}

		if (row != levelStartPlayerRow || col != levelStartPlayerCol) {
			playerHasAdvancedOneCellThisLevel = true;
		}
	}

	private void updateHighestRegionReached() {
		if (SceneGenerator.world == null) {
			return;
		}

		int row = getPlayerRow();
		int col = getPlayerCol();

		if (!SceneGenerator.world.inBounds(row, col)) {
			return;
		}

		int region = SceneGenerator.world.getRegionIndex(row, col);

		if (region > highestRegionReached) {
			highestRegionReached = region;
			highestRegionEntryRow = row;
			highestRegionEntryCol = col;

			warpRecentlyUsed = false;
			warpWasClearedByReset = false;

			showInteractionMessage("Region " + region + " reached. Warp saved.");
		}
	}

	private void checkWarpTile() {
		if (SceneGenerator.world == null || levelCompleteAwaitingContinue || finished) {
			return;
		}

		int row = getPlayerRow();
		int col = getPlayerCol();

		if (row != WARP_CELL_X || col != WARP_CELL_Y) {
			warpRecentlyUsed = false;
			return;
		}

		if (!playerHasAdvancedOneCellThisLevel && !warpWasClearedByReset) {
			return;
		}

		if (warpRecentlyUsed) {
			return;
		}

		if (highestRegionReached <= 0 || highestRegionEntryRow < 0 || highestRegionEntryCol < 0) {
			if (warpWasClearedByReset) {
				showInteractionMessage("Warp reset. Reach a region again if you need the shortcut.");
			} else {
				showInteractionMessage("Warp inactive. Reach another region first.");
			}

			warpRecentlyUsed = true;
			return;
		}

		double worldX = highestRegionEntryRow * cam.CELL + cam.CELL / 2.0;
		double worldY = highestRegionEntryCol * cam.CELL + cam.CELL / 2.0;

		Vector3 local = worldToCameraLocal(worldX, worldY, cam.PLAYER_HEIGHT);

		cam.pos.x = local.x;
		cam.pos.z = local.z;

		warpRecentlyUsed = true;
		warpWasClearedByReset = false;

		showInteractionMessage("Warped to Region " + highestRegionReached + " - shortcut used.");
	}

	private void resetLevelStats() {
		levelKeysCollected = 0;
		levelDoorsUnlocked = 0;
		levelPlatesActivated = 0;
		levelScore = 0;
		levelStartNanos = System.nanoTime();
		levelCompleteElapsedNanos = 0L;
		lastExitLockedMessageNanos = 0L;
	}

	private int getSkeletonKeyCapacityForCurrentLevel() {
		return SceneGenerator.getSkeletonKeySpawnCountForCurrentLevel();
	}

	private int getSkeletonKeyPreservedBonus() {
		if (skeletonKeyCount <= 0) {
			return 0;
		}

		// Reduced from 250 ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¾Ãƒâ€šÃ‚Â¢ 100 to avoid
		// over-incentivizing hoarding
		return skeletonKeyCount * 100;
	}

	private String formatElapsedTime(long nanos) {
		long totalSeconds = nanos / 1_000_000_000L;
		long minutes = totalSeconds / 60;
		long seconds = totalSeconds % 60;
		return String.format("%02d:%02d", minutes, seconds);
	}

	private void beginLevelCompleteState() {
		levelCompleteAwaitingContinue = true;
		levelCompleteElapsedNanos = System.nanoTime() - levelStartNanos;
		pendingNextLevel = currentLevel + 1;

		wKey = sKey = aKey = dKey = zKey = xKey = false;
		iKey = jKey = kKey = lKey = nKey = mKey = false;
	}

	private void drawLevelSummaryOverlay(Graphics2D g2) {
		if (!levelCompleteAwaitingContinue) {
			return;
		}

		int mazeSize = (SceneGenerator.world == null) ? 0 : SceneGenerator.world.rows;
		int pairs = getPairsForCurrentLevel();
		int regions = getRegionsForCurrentLevel();
		int clearScore = computeClearScore();
		int skeletonBonus = getSkeletonKeyPreservedBonus();
		int bonusNodeScore = SceneGenerator.getCollectedBonusNodeCount() * 75;

		String title = "Level " + currentLevel + " Complete";
		String timeLine = "Time: " + formatElapsedTime(levelCompleteElapsedNanos);
		String sizeLine = "Maze Size: " + mazeSize + " x " + mazeSize;
		String pairsLine = "Pairs: " + pairs;
		String regionsLine = "Regions: " + regions;
		String scoreLine = "Clear Score: " + clearScore;
		String continueLine = (currentLevel < maxLevels) ? "Press E to continue" : "Press E to finish";

		java.util.List<String> lines = new ArrayList<>();
		lines.add(title);
		lines.add("");
		lines.add(timeLine);
		lines.add(sizeLine);
		lines.add(pairsLine);
		lines.add(regionsLine);
		lines.add("");

		if (bonusNodeScore > 0) {
			lines.add("Bonus Found: +" + bonusNodeScore);
		}
		if (skeletonBonus > 0) {
			lines.add("Skeleton Key Preserved: +" + skeletonBonus);
		}
		if (bonusNodeScore > 0 || skeletonBonus > 0) {
			lines.add("");
		}

		lines.add(scoreLine);
		lines.add("");
		lines.add(continueLine);

		g2.setFont(new Font("Monospaced", Font.BOLD, 28));
		FontMetrics fm = g2.getFontMetrics();

		int maxWidth = 0;
		for (String line : lines) {
			maxWidth = Math.max(maxWidth, fm.stringWidth(line));
		}

		int lineHeight = fm.getHeight();
		int boxWidth = maxWidth + 40;
		int boxHeight = lineHeight * lines.size() + 30;

		int x = (getWidth() - boxWidth) / 2;
		int y = (getHeight() - boxHeight) / 2;

		g2.setColor(new Color(0, 0, 0, 220));
		g2.fillRoundRect(x, y, boxWidth, boxHeight, 20, 20);

		g2.setColor(Color.WHITE);

		int drawY = y + 25 + fm.getAscent();
		for (String line : lines) {
			int lineWidth = fm.stringWidth(line);
			int drawX = x + (boxWidth - lineWidth) / 2;
			g2.drawString(line, drawX, drawY);
			drawY += lineHeight;
		}
	}

	private Vector3 worldDirToViewDir(Vector3 dir) {
		Matrix4 rotOnly = Matrix4.transpose(cam.rot);
		Vector4 v = rotOnly.transform(dir);
		return new Vector3(v.x, v.y, v.z).normalize();
	}

	public static Vector3 triangleCenter(Triangle t) {
		double cx = (t.v0.pos.x + t.v1.pos.x + t.v2.pos.x) / 3.0;
		double cy = (t.v0.pos.y + t.v1.pos.y + t.v2.pos.y) / 3.0;
		double cz = (t.v0.pos.z + t.v1.pos.z + t.v2.pos.z) / 3.0;

		return new Vector3(cx, cy, cz);
	}

	static private void computeNormal(Triangle t) {
		t.normal = NormalCalculator.computeNormal(t);
	}

	public void run() {
		Matrix4 proj = Matrix4.perspective(Math.toRadians(70), 1024.0 / 768.0, 0.1, 100);

		resetToInitialState();

		while (true) {
			updateCam();
			processPendingSceneRebuild();

			renderer.clear();

			Vector3 viewLight = worldDirToViewDir(Lighting.lightDir);
			Matrix4 view = cam.getView();

			int pRow = getPlayerRow();
			int pCol = getPlayerCol();

			for (Triangle t : scene) {
			    if (shouldSkipDynamicTriangle(t)) {
			        continue;
			    }

			    int tRow = (int) (t.v0.pos.x / cam.CELL);
			    int tCol = (int) (t.v0.pos.y / cam.CELL);

			    if (tRow < pRow - 31)
			        continue;
			    if (tRow > pRow + 31)
			        continue;
			    if (tCol < pCol - 31)
			        continue;
			    if (tCol > pCol + 31)
			        continue;

			    BufferedImage originalTexture = t.texture;
			    t.texture = getRuntimeTextureForTriangle(t);

			    renderWorldTriangle(t, view, proj, viewLight);

			    t.texture = originalTexture;
			}
			
			renderVisibleDots(view, proj, viewLight, pRow, pCol);
			renderWarpCube(view, proj, viewLight);

			image = renderer.copyImage();

			repaint();

			long now = System.nanoTime();

			fpsFrameCount++;
			long elapsed = now - fpsAccumStart;
			if (elapsed >= 1_000_000_000L) {
				fps = fpsFrameCount * (1_000_000_000.0 / elapsed);
				fpsFrameCount = 0;
				fpsAccumStart = now;
			}

			lastFrameTime = now;

			try {
				Thread.sleep(1);
			} catch (Exception e) {
			}
		}
	}

	private void renderWorldTriangle(Triangle t, Matrix4 view, Matrix4 proj, Vector3 viewLight) {
		Triangle viewTri = transform(t, view);
		computeNormal(viewTri);
		Lighting.computeLighting(viewTri, viewLight);

		clipInput.clear();
		clipInput.add(viewTri.v0);
		clipInput.add(viewTri.v1);
		clipInput.add(viewTri.v2);

		List<Vertex> poly = Clipper.clipNearView(clipInput);
		if (poly.size() < 3) {
			return;
		}

		rasterizeClippedPolygon(poly, t, viewLight, proj);
	}

	private void renderWarpCube(Matrix4 view, Matrix4 proj, Vector3 viewLight) {
		if (SceneGenerator.world == null) {
			return;
		}
		if (!SceneGenerator.world.inBounds(WARP_CELL_X, WARP_CELL_Y)) {
			return;
		}
		if (!SceneGenerator.world.isBaseWalkable(WARP_CELL_X, WARP_CELL_Y)) {
			return;
		}

		double cell = cam.CELL;

		double centerX = WARP_CELL_X * cell + cell / 2.0;
		double centerY = WARP_CELL_Y * cell + cell / 2.0;
		double centerZ = 1.45;

		double pulse = Math.sin(System.nanoTime() * 0.000000003) * 0.08;
		double half = 0.52 + pulse;

		double x0 = centerX - half;
		double x1 = centerX + half;
		double y0 = centerY - half;
		double y1 = centerY + half;
		double z0 = centerZ - half;
		double z1 = centerZ + half;

		int red = 255;
		int green = 60;
		int blue = 255;

		addDotFace(x0, y0, z0, x1, y0, z0, x0, y1, z0, x1, y1, z0, red, green, blue, view, proj, viewLight);
		addDotFace(x0, y0, z1, x0, y1, z1, x1, y0, z1, x1, y1, z1, red, green, blue, view, proj, viewLight);

		addDotFace(x0, y0, z0, x0, y0, z1, x1, y0, z0, x1, y0, z1, red, green, blue, view, proj, viewLight);
		addDotFace(x0, y1, z0, x1, y1, z0, x0, y1, z1, x1, y1, z1, red, green, blue, view, proj, viewLight);

		addDotFace(x0, y0, z0, x0, y1, z0, x0, y0, z1, x0, y1, z1, red, green, blue, view, proj, viewLight);
		addDotFace(x1, y0, z0, x1, y0, z1, x1, y1, z0, x1, y1, z1, red, green, blue, view, proj, viewLight);
	}

	private void renderVisibleDots(Matrix4 view, Matrix4 proj, Vector3 viewLight, int playerRow, int playerCol) {
		if (SceneGenerator.world == null) {
			return;
		}

		final int dotDrawRadius = 22;

		int minRow = Math.max(0, playerRow - dotDrawRadius);
		int maxRow = Math.min(SceneGenerator.world.rows - 1, playerRow + dotDrawRadius);
		int minCol = Math.max(0, playerCol - dotDrawRadius);
		int maxCol = Math.min(SceneGenerator.world.cols - 1, playerCol + dotDrawRadius);

		for (int row = minRow; row <= maxRow; row++) {
			for (int col = minCol; col <= maxCol; col++) {
				if (!SceneGenerator.hasVisibleDotAt(row, col)) {
					continue;
				}

				renderDotCube(row, col, view, proj, viewLight);
			}
		}
	}

	private void renderDotCube(int row, int col, Matrix4 view, Matrix4 proj, Vector3 viewLight) {
		double cell = cam.CELL;

		double centerX = row * cell + cell / 2.0;
		double centerY = col * cell + cell / 2.0;
		double centerZ = 1.15;

		double half = 0.28;

		double x0 = centerX - half;
		double x1 = centerX + half;
		double y0 = centerY - half;
		double y1 = centerY + half;
		double z0 = centerZ - half;
		double z1 = centerZ + half;

		int red = 235;
		int green = 220;
		int blue = 80;

		addDotFace(x0, y0, z0, x1, y0, z0, x0, y1, z0, x1, y1, z0, red, green, blue, view, proj, viewLight);
		addDotFace(x0, y0, z1, x0, y1, z1, x1, y0, z1, x1, y1, z1, red, green, blue, view, proj, viewLight);

		addDotFace(x0, y0, z0, x0, y0, z1, x1, y0, z0, x1, y0, z1, red, green, blue, view, proj, viewLight);
		addDotFace(x0, y1, z0, x1, y1, z0, x0, y1, z1, x1, y1, z1, red, green, blue, view, proj, viewLight);

		addDotFace(x0, y0, z0, x0, y1, z0, x0, y0, z1, x0, y1, z1, red, green, blue, view, proj, viewLight);
		addDotFace(x1, y0, z0, x1, y0, z1, x1, y1, z0, x1, y1, z1, red, green, blue, view, proj, viewLight);
	}

	private void addDotFace(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy,
			double cz, double dx, double dy, double dz, int red, int green, int blue, Matrix4 view, Matrix4 proj,
			Vector3 viewLight) {
		Triangle first = makeDotTriangle(ax, ay, az, bx, by, bz, cx, cy, cz, red, green, blue);
		Triangle second = makeDotTriangle(cx, cy, cz, bx, by, bz, dx, dy, dz, red, green, blue);

		renderWorldTriangle(first, view, proj, viewLight);
		renderWorldTriangle(second, view, proj, viewLight);
	}

	private Triangle makeDotTriangle(double ax, double ay, double az, double bx, double by, double bz, double cx,
			double cy, double cz, int red, int green, int blue) {
		Vector3 p0 = new Vector3(ax, ay, az);
		Vector3 p1 = new Vector3(bx, by, bz);
		Vector3 p2 = new Vector3(cx, cy, cz);

		Vector3 normal = p1.sub(p0).cross(p2.sub(p0)).normalize();

		return new Triangle(new Vertex(new Vector4(ax, ay, az, 1), normal, 0.0, 0.0),
				new Vertex(new Vector4(bx, by, bz, 1), normal, 1.0, 0.0),
				new Vertex(new Vector4(cx, cy, cz, 1), normal, 0.0, 1.0), red, green, blue, 0, 0, 0, null);
	}

	private Triangle transform(Triangle t, Matrix4 m) {
	    Vertex nv0 = new Vertex(m.transform(t.v0.pos.toVec3()), t.v0.normal, t.v0.u, t.v0.v);
	    Vertex nv1 = new Vertex(m.transform(t.v1.pos.toVec3()), t.v1.normal, t.v1.u, t.v1.v);
	    Vertex nv2 = new Vertex(m.transform(t.v2.pos.toVec3()), t.v2.normal, t.v2.u, t.v2.v);

	    Triangle result = new Triangle(
	            nv0, nv1, nv2,
	            t.red, t.green, t.blue,
	            t.lightingRed, t.lightingGreen, t.lightingBlue,
	            t.texture
	    );

	    result.copyGridMetadataFrom(t);

	    return result;
	}

	private void perspectiveDivide(Triangle t) {
		for (Vertex v : new Vertex[] { t.v0, t.v1, t.v2 }) {
			v.clipZ = v.pos.z;
			v.clipW = v.pos.w;

			v.pos.x /= v.pos.w;
			v.pos.y /= v.pos.w;
			v.pos.z /= v.pos.w;
			v.pos.w = 1.0;
		}
	}

	private void screenMap(Triangle t) {
		for (Vertex v : new Vertex[] { t.v0, t.v1, t.v2 }) {
			double x = v.pos.x;
			double y = v.pos.y;
			double z = v.pos.z;

			x = (x + 1) * 0.5 * img.getWidth();
			y = (1 - y) * 0.5 * img.getHeight();

			v.pos = new Vector4(x, y, z, 1.0);
		}
	}

	public void onRotate(Matrix4 matToUse) {
	}

	double SPEED = 0.6;

	double PLAYER_RADIUS = 0.72;
	double SKIN = 0.001;

	private Matrix4 orthonormalizeRotation(Matrix4 m) {
		Vector3 x = new Vector3(m.m[0][0], m.m[1][0], m.m[2][0]);
		Vector3 y = new Vector3(m.m[0][1], m.m[1][1], m.m[2][1]);

		x = x.normalize();
		y = y.sub(x.mul(x.dot(y))).normalize();

		Vector3 z = x.cross(y);

		Matrix4 r = Matrix4.identity();

		r.m[0][0] = x.x;
		r.m[1][0] = x.y;
		r.m[2][0] = x.z;
		r.m[0][1] = y.x;
		r.m[1][1] = y.y;
		r.m[2][1] = y.z;
		r.m[0][2] = z.x;
		r.m[1][2] = z.y;
		r.m[2][2] = z.z;

		return r;
	}

	private boolean isSolidAtWorld(double worldX, double worldY) {
	    if (SceneGenerator.world == null) {
	        return true;
	    }

	    double cell = cam.CELL;

	    int row = (int) Math.floor(worldX / cell);
	    int col = (int) Math.floor(worldY / cell);

	    return isSolidCell(row, col);
	}

	private boolean isLockedExitBoundaryCollision(double worldX, double worldY) {
		if (SceneGenerator.world == null || areDotsEffectivelyCollected()) {
			return false;
		}

		double cell = cam.CELL;
		double r = PLAYER_RADIUS;

		int rows = SceneGenerator.world.rows;
		int cols = SceneGenerator.world.cols;

		// Find the boundary exit and make ONLY the outside edge act like a wall.
		// The exit cell remains reachable; the player just cannot push through
		// far enough for the renderer to clip past the doorway.
		for (int c = 0; c < cols; c++) {
			if (SceneGenerator.world.isExit(0, c)) {
				double minY = c * cell;
				double maxY = (c + 1) * cell;
				boolean alignedWithExit = worldY + r > minY && worldY - r < maxY;
				if (alignedWithExit && worldX - r < 0.0) {
					return true;
				}
			}

			if (SceneGenerator.world.isExit(rows - 1, c)) {
				double minY = c * cell;
				double maxY = (c + 1) * cell;
				double boundaryX = rows * cell;
				boolean alignedWithExit = worldY + r > minY && worldY - r < maxY;
				if (alignedWithExit && worldX + r > boundaryX) {
					return true;
				}
			}
		}

		for (int rr = 0; rr < rows; rr++) {
			if (SceneGenerator.world.isExit(rr, 0)) {
				double minX = rr * cell;
				double maxX = (rr + 1) * cell;
				boolean alignedWithExit = worldX + r > minX && worldX - r < maxX;
				if (alignedWithExit && worldY - r < 0.0) {
					return true;
				}
			}

			if (SceneGenerator.world.isExit(rr, cols - 1)) {
				double minX = rr * cell;
				double maxX = (rr + 1) * cell;
				double boundaryY = cols * cell;
				boolean alignedWithExit = worldX + r > minX && worldX - r < maxX;
				if (alignedWithExit && worldY + r > boundaryY) {
					return true;
				}
			}
		}

		return false;
	}

	private boolean collidesAt(double camX, double camZ) {
		Vector3 worldPos = cameraLocalToWorld(camX, cam.pos.y, camZ);

		double x = worldPos.x;
		double y = worldPos.y;

		double r = PLAYER_RADIUS;
		double d = r * 0.70710678118;

		// Locked exit: only the outside edge behind the exit acts like a wall.
		// This keeps the exit reachable but prevents crossing far enough to clip
		// through the doorway into blank space.
		if (isLockedExitBoundaryCollision(x, y)) {
			return true;
		}

		// Normal collision remains unchanged for real walls, locked doors, and
		// barriers.
		return isSolidAtWorld(x, y) ||

				isSolidAtWorld(x + r, y) || isSolidAtWorld(x - r, y) || isSolidAtWorld(x, y + r)
				|| isSolidAtWorld(x, y - r) ||

				isSolidAtWorld(x + d, y + d) || isSolidAtWorld(x + d, y - d) || isSolidAtWorld(x - d, y + d)
				|| isSolidAtWorld(x - d, y - d);
	}

	private Vector3 cameraLocalToWorld(double camX, double camY, double camZ) {
		return cam.rot.transform(new Vector3(camX, camY, camZ)).toVec3();
	}

	private Vector3 worldToCameraLocal(double worldX, double worldY, double worldZ) {
		Matrix4 invRot = Matrix4.transpose(cam.rot);
		return invRot.transform(new Vector3(worldX, worldY, worldZ)).toVec3();
	}

	private void tryMove(double dx, double dz) {
		Vector3 currWorld = cameraLocalToWorld(cam.pos.x, cam.pos.y, cam.pos.z);

		double targetCamX = cam.pos.x + dx;
		double targetCamZ = cam.pos.z + dz;

		Vector3 targetWorld = cameraLocalToWorld(targetCamX, cam.pos.y, targetCamZ);

		double currWX = currWorld.x;
		double currWY = currWorld.y;

		double nextWX = targetWorld.x;
		double nextWY = targetWorld.y;

		Vector3 fullLocal = worldToCameraLocal(nextWX, nextWY, currWorld.z);
		if (!collidesAt(fullLocal.x, fullLocal.z)) {
			cam.pos.x = fullLocal.x;
			cam.pos.z = fullLocal.z;
			return;
		}

		boolean moved = false;

		Vector3 slideXLocal = worldToCameraLocal(nextWX, currWY, currWorld.z);
		if (!collidesAt(slideXLocal.x, slideXLocal.z)) {
			cam.pos.x = slideXLocal.x;
			cam.pos.z = slideXLocal.z;
			moved = true;
		}

		Vector3 slideYLocal = worldToCameraLocal(currWX, nextWY, currWorld.z);
		if (!collidesAt(slideYLocal.x, slideYLocal.z)) {
			cam.pos.x = slideYLocal.x;
			cam.pos.z = slideYLocal.z;
			moved = true;
		}

		if (!moved) {
			int steps = 4;
			double stepDX = dx / steps;
			double stepDZ = dz / steps;

			for (int i = 0; i < steps; i++) {
				double subCamX = cam.pos.x + stepDX;
				double subCamZ = cam.pos.z + stepDZ;

				if (!collidesAt(subCamX, subCamZ)) {
					cam.pos.x = subCamX;
					cam.pos.z = subCamZ;
					continue;
				}

				Vector3 subWorld = cameraLocalToWorld(subCamX, cam.pos.y, subCamZ);
				Vector3 nowWorld = cameraLocalToWorld(cam.pos.x, cam.pos.y, cam.pos.z);

				Vector3 subXLocal = worldToCameraLocal(subWorld.x, nowWorld.y, nowWorld.z);
				if (!collidesAt(subXLocal.x, subXLocal.z)) {
					cam.pos.x = subXLocal.x;
					cam.pos.z = subXLocal.z;
					continue;
				}

				Vector3 subYLocal = worldToCameraLocal(nowWorld.x, subWorld.y, nowWorld.z);
				if (!collidesAt(subYLocal.x, subYLocal.z)) {
					cam.pos.x = subYLocal.x;
					cam.pos.z = subYLocal.z;
					continue;
				}

				break;
			}
		}
	}

	private void updateCam() {
		if (spaceKey) {
			boolean hadWarpProgress = highestRegionReached > 0;

			resetToInitialState();

			warpWasClearedByReset = hadWarpProgress;

			if (hadWarpProgress) {
				showInteractionMessage("Level reset. Warp progress cleared.");
			} else {
				showInteractionMessage("Level reset.");
			}

			spaceKey = false;
			return;
		}

		if (debugNextLevelRequested) {
			debugNextLevelRequested = false;
			startLevel(Math.min(currentLevel + 1, maxLevels));
			return;
		}

		if (debugWarpNearExitRequested) {
			debugWarpNearExitRequested = false;
			debugWarpPlayerNearExit();
			return;
		}

		if (interactRequested) {
			interactRequested = false;
			tryInteract();
			if (levelCompleteAwaitingContinue || finished) {
				return;
			}
		}

		if (finished) {
			return;
		}

		Vector3 forward = new Vector3(cam.rot.m[0][1], cam.rot.m[1][1], cam.rot.m[2][1]).neg();
		forward.y = 0;

		Vector3 right = new Vector3(-forward.z, 0, forward.x);
		Vector3 backward = new Vector3(-forward.x, -forward.y, -forward.z);

		if (lKey) {
			Matrix4 rotStep = Matrix4.rotationY(-cam.angle);

			cam.rot = Matrix4.mul(cam.rot, rotStep);

			Matrix4 invStep = Matrix4.transpose(rotStep);
			cam.pos = invStep.transform(cam.pos).toVec3();

			cam.rot = orthonormalizeRotation(cam.rot);
		}

		if (jKey) {
			Matrix4 rotStep = Matrix4.rotationY(cam.angle);

			cam.rot = Matrix4.mul(cam.rot, rotStep);

			Matrix4 invStep = Matrix4.transpose(rotStep);
			cam.pos = invStep.transform(cam.pos).toVec3();

			cam.rot = orthonormalizeRotation(cam.rot);
		}

		// On the level-complete screen, allow looking left/right with J/L,
		// but keep all movement and gameplay updates paused until E continues.
		if (levelCompleteAwaitingContinue) {
			return;
		}

		double moveX = 0.0;
		double moveZ = 0.0;

		if (iKey) {
			moveX += forward.x * SPEED;
			moveZ += forward.z * SPEED;
		}
		if (kKey) {
			moveX += backward.x * SPEED;
			moveZ += backward.z * SPEED;
		}
		if (nKey) {
			moveX -= right.x * SPEED;
			moveZ -= right.z * SPEED;
		}
		if (mKey) {
			moveX += right.x * SPEED;
			moveZ += right.z * SPEED;
		}

		if (moveX != 0.0 || moveZ != 0.0) {
			double len = Math.sqrt(moveX * moveX + moveZ * moveZ);
			if (len > SPEED) {
				moveX = moveX / len * SPEED;
				moveZ = moveZ / len * SPEED;
			}

			tryMove(moveX, moveZ);
		}

		boolean changed = SceneGenerator.world.activateTriggerAt(getPlayerRow(), getPlayerCol());
		if (changed) {
		    levelPlatesActivated++;
		    levelScore++;
		    requestSceneRebuild();
		    showInteractionMessage("Pressure plate activated");
		}

		if (SceneGenerator.collectBonusNodeAt(getPlayerRow(), getPlayerCol())) {
		    showInteractionMessage("Bonus found (+75)");
		}

		tryCollectKeyAtPlayerCell();
		tryCollectDotAtPlayerCell();

		updatePlayerAdvanceTracking();
		updateHighestRegionReached();
		checkWarpTile();

		if (getCurrentCellType() == 'E' && areDotsEffectivelyCollected()) {
			beginLevelCompleteState();
		}
	}
	
	private int getPairsForCurrentLevel() {
		return SceneGenerator.world == null ? 0 : SceneGenerator.world.doorsById.size();
	}

	private int getRegionsForCurrentLevel() {
		if (SceneGenerator.world == null) {
			return 0;
		}

		HashSet<Integer> regions = new HashSet<>();
		for (int r = 0; r < SceneGenerator.world.rows; r++) {
			for (int c = 0; c < SceneGenerator.world.cols; c++) {
				if (SceneGenerator.world.isBaseWalkable(r, c)) {
					regions.add(SceneGenerator.world.getRegionIndex(r, c));
				}
			}
		}
		return regions.size();
	}

	private long getExpectedTimeSecondsForCurrentLevel() {
		int size = SceneGenerator.world == null ? 0 : SceneGenerator.world.rows;
		if (size <= 0) {
			return 1;
		}
		return Math.max(1L, Math.round((size * size) / 20.0));
	}

	private int computeDifficultyScore() {
		if (SceneGenerator.world == null) {
			return 0;
		}

		int size = SceneGenerator.world.rows;
		int pairs = getPairsForCurrentLevel();
		int regions = getRegionsForCurrentLevel();

		double sizeFactor = 1.0;
		double pairFactor = 120.0;
		double regionFactor = 60.0;

		double difficultyScore = (size * sizeFactor) + (pairs * pairFactor) + (regions * regionFactor);

		return (int) Math.round(difficultyScore);
	}

	private int computeClearScore() {
		int difficultyScore = computeDifficultyScore();
		if (difficultyScore <= 0) {
			return 0;
		}

		long actualSeconds = Math.max(1L, levelCompleteElapsedNanos / 1_000_000_000L);
		long expectedSeconds = getExpectedTimeSecondsForCurrentLevel();

		double ratio = expectedSeconds / (double) actualSeconds;
		double timeModifier = Math.max(0.5, Math.min(1.5, ratio));

		int baseScore = (int) Math.round(difficultyScore * timeModifier);
		int skeletonBonus = getSkeletonKeyPreservedBonus();
		int bonusNodeScore = SceneGenerator.getCollectedBonusNodeCount() * 75;

		return baseScore + skeletonBonus + bonusNodeScore;
	}

	private void drawInventory(Graphics2D g2) {
		if (!showInventory) {
			return;
		}

		java.util.List<String> items = new ArrayList<>();

		if (SceneGenerator.world != null) {
			items.add("Dots: " + getEffectiveCollectedDotCount() + " / " + SceneGenerator.getTotalDotCount());
		}

		for (Map.Entry<Integer, Integer> entry : inventoryKeyCounts.entrySet()) {
			items.add(colorName(entry.getKey()) + " Key x" + entry.getValue());
		}

		if (skeletonKeyCount > 0) {
			items.add("Skeleton Key x" + skeletonKeyCount);
		}

		int x = getWidth() - 240;
		int y = 12;
		int width = 220;

		g2.setFont(new Font("Monospaced", Font.BOLD, 16));
		FontMetrics fm = g2.getFontMetrics();
		int lineHeight = fm.getHeight();

		int itemCount = Math.max(1, items.size());
		int height = 14 + lineHeight * (itemCount + 1);

		g2.setColor(new Color(0, 0, 0, 170));
		g2.fillRoundRect(x, y, width, height, 12, 12);

		g2.setColor(Color.WHITE);
		g2.drawString("Inventory", x + 12, y + 20);

		int drawY = y + 20 + lineHeight;

		if (items.isEmpty()) {
			g2.drawString("(empty)", x + 12, drawY);
			return;
		}

		for (String label : items) {
			g2.drawString(label, x + 12, drawY);
			drawY += lineHeight;
		}
	}

	private void drawInteractionMessage(Graphics2D g2) {
		pruneExpiredInteractionMessages();

		if (interactionMessages.isEmpty()) {
			return;
		}

		ArrayList<InteractionMessage> visible = new ArrayList<>(interactionMessages);
		int start = Math.max(0, visible.size() - MAX_INTERACTION_LINES);

		g2.setFont(new Font("Monospaced", Font.BOLD, 18));
		FontMetrics fm = g2.getFontMetrics();

		int maxWidth = 0;
		for (int i = start; i < visible.size(); i++) {
			maxWidth = Math.max(maxWidth, fm.stringWidth(visible.get(i).text));
		}

		int lineCount = visible.size() - start;
		int lineHeight = fm.getHeight();
		int boxWidth = maxWidth + 24;
		int boxHeight = 12 + lineCount * lineHeight + 12;

		int x = (getWidth() - boxWidth) / 2;
		int y = getHeight() - 80 - (lineCount - 1) * lineHeight;

		g2.setColor(new Color(0, 0, 0, 180));
		g2.fillRoundRect(x, y, boxWidth, boxHeight, 12, 12);

		g2.setColor(Color.WHITE);

		int drawY = y + 6 + fm.getAscent();
		for (int i = start; i < visible.size(); i++) {
			g2.drawString(visible.get(i).text, x + 12, drawY);
			drawY += lineHeight;
		}
	}

	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		BufferedImage frame = image;
		if (frame != null) {
			g.drawImage(frame, 0, 0, null);
		}

		Graphics2D g2 = (Graphics2D) g.create();
		drawHud(g2);
		drawInventory(g2);
		drawInteractionMessage(g2);
		drawLevelSummaryOverlay(g2);
		drawExitOverlay(g2);
		g2.dispose();
	}

	public static void main(String[] args) {
		JFrame f = new JFrame("Super Ten Challenge!");
		Main p = new Main(1024, 768);

		f.add(p);
		f.setSize(1024, 768);
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.setLocationRelativeTo(null);
		f.setVisible(true);

		SwingUtilities.invokeLater(p::requestFocusInWindow);
	}

	private void buildDoorAndKeyTextures() {
		coloredDoorTextures = new BufferedImage[4];
		coloredKeyTextures = new BufferedImage[4];

		coloredDoorTextures[0] = TextureFactory.makeLandmarkPanelTexture(64, 64, 160, 40, 40, 220, 80, 80);

		coloredDoorTextures[1] = TextureFactory.makeLandmarkPanelTexture(64, 64, 40, 70, 160, 90, 145, 230);

		coloredDoorTextures[2] = TextureFactory.makeLandmarkPanelTexture(64, 64, 45, 130, 60, 95, 205, 110);

		coloredDoorTextures[3] = TextureFactory.makeLandmarkPanelTexture(64, 64, 170, 145, 35, 235, 215, 85);

		coloredKeyTextures[0] = TextureFactory.makeDieFiveKeyTexture(64, 64, 10, 10, 10, 30, 30, 30, 120, 0, 0, 255, 40,
				40);

		coloredKeyTextures[1] = TextureFactory.makeDieFiveKeyTexture(64, 64, 10, 10, 10, 30, 30, 30, 0, 40, 120, 60,
				180, 255);

		coloredKeyTextures[2] = TextureFactory.makeDieFiveKeyTexture(64, 64, 10, 10, 10, 30, 30, 30, 0, 120, 40, 80,
				255, 120);

		coloredKeyTextures[3] = TextureFactory.makeDieFiveKeyTexture(64, 64, 10, 10, 10, 30, 30, 30, 120, 100, 0, 255,
				240, 80);

		skeletonKeyTexture = TextureFactory.makeDieFiveKeyTexture(64, 64, 18, 18, 18, 42, 42, 42, 120, 120, 120, 210,
				210, 210);
	}
	
	private MaterialTheme getMaterialForRegion(int region) {
	    switch (Math.floorMod(region, 6)) {
	        case 0: return MaterialTheme.EARTH;
	        case 1: return MaterialTheme.FIRE;
	        case 2: return MaterialTheme.WATER;
	        case 3: return MaterialTheme.STONE;
	        case 4: return MaterialTheme.SMOKE;
	        case 5: return MaterialTheme.WOOD;
	        default: return MaterialTheme.STONE;
	    }
	}
	
	private BufferedImage createMaterialWall(MaterialTheme mat, int region, int variant) {
	    int seed = 3000 + region * 100 + variant;
	    int familyVariant = Math.floorDiv(region, 6);

	    switch (mat) {
	        case EARTH:
	            switch (familyVariant) {
	                case 0:
	                    return TextureFactory.makeProductionLandmarkWall(64, 64, 52, 110, 48, 102, 168, 72, seed);
	                case 1:
	                    return TextureFactory.makeProductionLandmarkWall(64, 64, 42, 116, 70, 92, 198, 120, seed);
	                default:
	                    return TextureFactory.makeProductionLandmarkWall(64, 64, 64, 92, 42, 148, 124, 56, seed);
	            }

	        case FIRE:
	            switch (familyVariant) {
	                case 0:
	                    return TextureFactory.makeProductionLandmarkWall(64, 64, 150, 28, 18, 255, 116, 28, seed);
	                case 1:
	                    return TextureFactory.makeProductionLandmarkWall(64, 64, 112, 18, 40, 230, 64, 92, seed);
	                default:
	                    return TextureFactory.makeProductionLandmarkWall(64, 64, 98, 42, 12, 224, 146, 34, seed);
	            }

	        case WATER:
	            switch (familyVariant) {
	                case 0:
	                    return TextureFactory.makeProductionLandmarkWall(64, 64, 18, 96, 150, 54, 210, 230, seed);
	                case 1:
	                    return TextureFactory.makeProductionLandmarkWall(64, 64, 18, 62, 132, 50, 146, 228, seed);
	                default:
	                    return TextureFactory.makeProductionLandmarkWall(64, 64, 18, 128, 118, 66, 220, 196, seed);
	            }

	        case STONE:
	            switch (familyVariant) {
	                case 0:
	                    return TextureFactory.makeProductionLandmarkWall(64, 64, 72, 82, 180, 156, 110, 220, seed);
	                case 1:
	                    return TextureFactory.makeProductionLandmarkWall(64, 64, 56, 96, 170, 118, 156, 230, seed);
	                default:
	                    return TextureFactory.makeProductionLandmarkWall(64, 64, 112, 64, 156, 190, 108, 220, seed);
	            }

	        case SMOKE:
	            switch (familyVariant) {
	                case 0:
	                    return TextureFactory.makeProductionLandmarkWall(64, 64, 92, 48, 132, 178, 92, 210, seed);
	                case 1:
	                    return TextureFactory.makeProductionLandmarkWall(64, 64, 54, 76, 146, 126, 136, 220, seed);
	                default:
	                    return TextureFactory.makeProductionLandmarkWall(64, 64, 116, 46, 118, 210, 92, 184, seed);
	            }

	        case WOOD:
	            switch (familyVariant) {
	                case 0:
	                    return TextureFactory.makeProductionLandmarkWall(64, 64, 132, 76, 24, 226, 156, 54, seed);
	                case 1:
	                    return TextureFactory.makeProductionLandmarkWall(64, 64, 96, 104, 28, 188, 168, 54, seed);
	                default:
	                    return TextureFactory.makeProductionLandmarkWall(64, 64, 142, 54, 30, 226, 112, 52, seed);
	            }

	        default:
	            return TextureFactory.makeProductionLandmarkWall(64, 64, 80, 80, 160, 160, 120, 220, seed);
	    }
	}
	
	private BufferedImage createMaterialFloor(MaterialTheme mat, int region) {
	    int seed = 4000 + region;
	    int familyVariant = Math.floorDiv(region, 6);

	    switch (mat) {
	        case EARTH:
	            if (familyVariant == 0)
	                return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 38, 86, 36, 120, 190, 72, seed);
	            if (familyVariant == 1)
	                return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 92, 64, 18, 230, 220, 72, seed);
	            return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 42, 82, 66, 116, 178, 132, seed);

	        case FIRE:
	            if (familyVariant == 0)
	                return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 112, 28, 18, 238, 92, 24, seed);
	            if (familyVariant == 1)
	                return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 104, 24, 64, 220, 72, 130, seed);
	            return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 122, 58, 18, 246, 152, 42, seed);

	        case WATER:
	            if (familyVariant == 0)
	                return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 18, 76, 150, 58, 200, 235, seed);
	            if (familyVariant == 1)
	                return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 18, 48, 118, 64, 124, 230, seed);
	            return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 14, 110, 118, 64, 216, 190, seed);

	        case STONE:
	            if (familyVariant == 0)
	                return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 78, 70, 150, 166, 124, 220, seed);
	            if (familyVariant == 1)
	                return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 62, 100, 150, 136, 178, 224, seed);
	            return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 104, 72, 126, 204, 134, 204, seed);

	        case SMOKE:
	            if (familyVariant == 0)
	                return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 28, 82, 24, 142, 235, 60, seed);
	            if (familyVariant == 1)
	                return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 48, 72, 132, 120, 144, 220, seed);
	            return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 98, 46, 104, 210, 96, 178, seed);

	        case WOOD:
	            if (familyVariant == 0)
	                return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 110, 58, 22, 220, 138, 48, seed);
	            if (familyVariant == 1)
	                return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 82, 96, 28, 182, 170, 54, seed);
	            return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 126, 48, 26, 226, 106, 54, seed);

	        default:
	            return TextureFactory.makeRegionCheckerFloor(64, 64, 8, 50, 110, 140, 120, 200, 220, seed);
	    }
	}
	
	private BufferedImage createMaterialCeiling(MaterialTheme mat, int region) {
	    int seed = 5000 + region;

	    switch (mat) {
	        case FIRE:
	            return TextureFactory.makeProductionCeiling(64, 64, 50, seed);

	        case SMOKE:
	            return TextureFactory.makeProductionCeiling(64, 64, 60, seed);

	        default:
	            return TextureFactory.makeProductionCeiling(64, 64, 75, seed);
	    }
	}

	
	private void buildRegionalTextures() {
	    regionWallTextures = new BufferedImage[16][8];
	    regionFloorTextures = new BufferedImage[16];
	    regionCeilingTextures = new BufferedImage[16];

	    for (int region = 0; region < 16; region++) {
	        MaterialTheme mat = getMaterialForRegion(region);

	        for (int v = 0; v < 8; v++) {
	            regionWallTextures[region][v] = createMaterialWall(mat, region, v);
	        }

	        regionFloorTextures[region] = createMaterialFloor(mat, region);

	        regionCeilingTextures[region] = createMaterialCeiling(mat, region);
	    }
	}

	private boolean isDebugRegionPreviewEnabled() {
		Object value = getClientProperty("debugRegionPreviewEnabled");
		return value instanceof Boolean && ((Boolean) value);
	}

	private void setDebugRegionPreviewEnabled(boolean enabled) {
		putClientProperty("debugRegionPreviewEnabled", enabled);
	}

	private int getDebugPreviewRegionIndex() {
		Object value = getClientProperty("debugPreviewRegionIndex");
		if (value instanceof Integer) {
			int region = (Integer) value;
			return Math.floorMod(region, 16);
		}
		return 0;
	}

	private void setDebugPreviewRegionIndex(int region) {
		putClientProperty("debugPreviewRegionIndex", Math.floorMod(region, 16));
	}

	private void cycleDebugPreviewRegion(int delta) {
		int next = Math.floorMod(getDebugPreviewRegionIndex() + delta, 16);
		setDebugPreviewRegionIndex(next);
		showInteractionMessage("Preview Region: " + next);
		requestSceneRebuild();
	}

	private void toggleDebugRegionPreview() {
		boolean enabled = !isDebugRegionPreviewEnabled();
		setDebugRegionPreviewEnabled(enabled);

		if (enabled) {
			showInteractionMessage("Region preview ON (" + getDebugPreviewRegionIndex() + ")");
		} else {
			showInteractionMessage("Region preview OFF");
		}

		requestSceneRebuild();
	}

	private void applyRegionMoodToTriangle(Triangle tri, int logicalRegion) {
		int visualRegion = Math.floorMod(logicalRegion, 4);

		double brightnessScale;
		double tintR;
		double tintG;
		double tintB;

		switch (visualRegion) {
		case 0:
			brightnessScale = 1.10;
			tintR = 1.10;
			tintG = 1.03;
			tintB = 0.96;
			break;

		case 1:
			brightnessScale = 0.98;
			tintR = 0.92;
			tintG = 0.99;
			tintB = 1.12;
			break;

		case 2:
			brightnessScale = 0.92;
			tintR = 0.95;
			tintG = 0.97;
			tintB = 1.00;
			break;

		case 3:
			brightnessScale = 1.06;
			tintR = 1.12;
			tintG = 1.04;
			tintB = 0.94;
			break;

		default:
			brightnessScale = 1.0;
			tintR = 1.0;
			tintG = 1.0;
			tintB = 1.0;
			break;
		}

		tri.red = clampColor((int) Math.round(tri.red * brightnessScale * tintR));
		tri.green = clampColor((int) Math.round(tri.green * brightnessScale * tintG));
		tri.blue = clampColor((int) Math.round(tri.blue * brightnessScale * tintB));
	}

	private int clampColor(int v) {
		if (v < 0)
			return 0;
		if (v > 255)
			return 255;
		return v;
	}

	private Vertex makeProjectedVertex(int ptIndex, int faceType, Vector3 normal) {
		double x = SceneGenerator.pts[ptIndex][0];
		double y = SceneGenerator.pts[ptIndex][1];
		double z = SceneGenerator.pts[ptIndex][2];

		double u;
		double v;

		if (faceType == 0 || faceType == 1 || faceType == 6 || faceType == 7 || faceType == 9
				|| (faceType >= 15 && faceType <= 18)) {
			u = x / cam.CELL;
			v = y / cam.CELL;
		} else {
			double ax = Math.abs(normal.x);
			double ay = Math.abs(normal.y);
			double az = Math.abs(normal.z);

			if (ax >= ay && ax >= az) {
				u = y / cam.CELL;
				v = z / cam.CELL;
			} else if (ay >= ax && ay >= az) {
				u = x / cam.CELL;
				v = z / cam.CELL;
			} else {
				u = x / cam.CELL;
				v = y / cam.CELL;
			}
		}

		return new Vertex(new Vector4(x, y, z, 1), normal, u, v);
	}

	private Vector3 computeWorldFaceNormal(int[] f) {
		Vector3 p0 = new Vector3(SceneGenerator.pts[f[0]][0], SceneGenerator.pts[f[0]][1], SceneGenerator.pts[f[0]][2]);
		Vector3 p1 = new Vector3(SceneGenerator.pts[f[1]][0], SceneGenerator.pts[f[1]][1], SceneGenerator.pts[f[1]][2]);
		Vector3 p2 = new Vector3(SceneGenerator.pts[f[2]][0], SceneGenerator.pts[f[2]][1], SceneGenerator.pts[f[2]][2]);

		return p1.sub(p0).cross(p2.sub(p0)).normalize();
	}

	private void rebuildSceneFromWorld() {
		scene.clear();

		for (int i = 0; i < SceneGenerator.numMultiCubeFaces; i++) {
			int[] f = SceneGenerator.multiCubeFaces[i];
			
			double cx = (SceneGenerator.pts[f[0]][0] + SceneGenerator.pts[f[1]][0] + SceneGenerator.pts[f[2]][0]) / 3.0;

			double cy = (SceneGenerator.pts[f[0]][1] + SceneGenerator.pts[f[1]][1] + SceneGenerator.pts[f[2]][1]) / 3.0;

			int row = (int) Math.floor(cx / cam.CELL);
			int gridCol = (int) Math.floor(cy / cam.CELL);

			row = Math.max(0, Math.min(row, SceneGenerator.world.rows - 1));
			gridCol = Math.max(0, Math.min(gridCol, SceneGenerator.world.cols - 1));

			BufferedImage tex = chooseTextureForFace(f[3], row, gridCol);
			int logicalRegion = resolveVisualZoneForFace(f[3], row, gridCol);

			Color color = Color.WHITE;

			Vector3 worldNormal = computeWorldFaceNormal(f);

			Vertex v0 = makeProjectedVertex(f[0], f[3], worldNormal);
			Vertex v1 = makeProjectedVertex(f[1], f[3], worldNormal);
			Vertex v2 = makeProjectedVertex(f[2], f[3], worldNormal);

			Triangle tri = new Triangle(v0, v1, v2, color.getRed(), color.getGreen(), color.getBlue(), 0, 0, 0, tex);
			tri.setGridMetadata(f[3], row, gridCol);

			applyRegionMoodToTriangle(tri, logicalRegion);

			Vector3 viewLight = rotateDirectionToView(Lighting.lightDir);
			computeNormal(tri);
			Lighting.computeLighting(tri, viewLight);

			if (f[3] == 8) {
				tri.lightingRed = 255;
				tri.lightingGreen = 255;
				tri.lightingBlue = 255;
			}

			scene.add(tri);
		}

		bsp = new BSPNode(scene);
	}
}
