package org.lab;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lab.engine.Player;
import org.lab.engine.Shader;
import org.lab.render.BlockRenderer;
import org.lab.render.CubeMesh;
import org.lab.render.TextureArray;
import org.lab.util.Raycaster;
import org.lab.util.RaycastResult;
import org.lab.world.Block;
import org.lab.world.World;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Main entry point and test for the voxel rendering system.
 * Creates a window, initializes OpenGL, and renders test blocks.
 */
public class Main {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final String TITLE = "Voxel Game - OpenGL Test";

    // Demo mode: orbital camera viewer (set to false for normal gameplay)
    private static final boolean DEMO_MODE = true;

    // Orbital camera settings (used when DEMO_MODE = true)
    private static final Vector3f ORBIT_CENTER = new Vector3f(0, 3, 0);
    private static final float ORBIT_RADIUS_MIN = 5f;
    private static final float ORBIT_RADIUS_MAX = 50f;
    private static final float ORBIT_ZOOM_SPEED = 15f;  // units per second
    private float orbitRadius = 20f;  // current zoom distance
    private float orbitYaw = 45f;     // horizontal angle (degrees)
    private float orbitPitch = 30f;   // vertical angle (degrees)

    private long window;
    private Shader shader;
    private BlockRenderer blockRenderer;
    private TextureArray blockTextures;  // All block textures in one array

    private World world;
    private Player player;
    private Matrix4f projection;
    private Matrix4f view;
    private Matrix4f viewProjection;

    // Camera state
    private float cameraYaw = -90f;    // degrees
    private float cameraPitch = 0f;
    private Vector3f cameraPos = new Vector3f(0, 2, 8);
    private Vector3f cameraFront = new Vector3f(0, 0, -1);
    private Vector3f cameraUp = new Vector3f(0, 1, 0);

    // Mouse state
    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;

    // Block interaction
    private RaycastResult targetedBlock = null;
    private static final float REACH_DISTANCE = 50.0f;  // Longer reach for demo mode
    private int currentBlockType = 0;  // 0=grass, 1=concrete, 2=wood

    // Edit mode: cursor visible, camera frozen, block highlighting active
    private boolean editMode = false;

    // For screen-to-world ray calculation
    private Matrix4f inverseViewProjection = new Matrix4f();

    // FPS tracking and frame limiting
    private int frameCount = 0;
    private float fpsTimer = 0;
    private static final float TARGET_FRAME_TIME = 1.0f / 30.0f;  // 30 FPS

    public static void main(String[] args) {
        new Main().run();
    }

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        // Set up error callback
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW
        if (!glfwInit()) {
            throw new RuntimeException("Failed to initialize GLFW");
        }

        // Configure GLFW for OpenGL 3.3 core profile
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE); // Required on macOS
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 0);  // No multisampling/AA

        // Create window
        window = glfwCreateWindow(WIDTH, HEIGHT, TITLE, NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        // Set up key callback
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                glfwSetWindowShouldClose(win, true);
            }

            // Block type selection (1=grass, 2=concrete, 3=wood)
            if (DEMO_MODE && action == GLFW_PRESS) {
                if (key == GLFW_KEY_1) {
                    currentBlockType = 0;
                    System.out.println("Selected: Grass");
                } else if (key == GLFW_KEY_2) {
                    currentBlockType = 1;
                    System.out.println("Selected: Concrete");
                } else if (key == GLFW_KEY_3) {
                    currentBlockType = 2;
                    System.out.println("Selected: Wood Plank");
                }
            }

            // Toggle edit mode with Tab (demo mode only)
            if (DEMO_MODE && key == GLFW_KEY_TAB && action == GLFW_PRESS) {
                editMode = !editMode;
                glfwSetInputMode(win, GLFW_CURSOR,
                    editMode ? GLFW_CURSOR_NORMAL : GLFW_CURSOR_DISABLED);
                firstMouse = true;  // Reset to prevent camera jump when exiting edit mode
                System.out.println("Edit mode: " + (editMode ? "ON" : "OFF"));
            }
        });

        // Set up mouse callback for camera rotation
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (firstMouse) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                firstMouse = false;
            }

            double xoffset = xpos - lastMouseX;
            double yoffset = lastMouseY - ypos; // Reversed: y goes bottom to top
            lastMouseX = xpos;
            lastMouseY = ypos;

            float sensitivity = 0.1f;

            if (DEMO_MODE) {
                // Only rotate camera when NOT in edit mode
                if (!editMode) {
                    // Orbital camera: mouse drag rotates around center
                    orbitYaw += (float) xoffset * sensitivity;
                    orbitPitch -= (float) yoffset * sensitivity;  // Inverted for natural feel

                    // Clamp pitch to prevent flipping
                    if (orbitPitch > 89f) orbitPitch = 89f;
                    if (orbitPitch < -89f) orbitPitch = -89f;
                }
            } else {
                // First-person camera
                cameraYaw += (float) xoffset * sensitivity;
                cameraPitch += (float) yoffset * sensitivity;

                // Clamp pitch
                if (cameraPitch > 89f) cameraPitch = 89f;
                if (cameraPitch < -89f) cameraPitch = -89f;

                // Update camera front vector
                float yawRad = (float) Math.toRadians(cameraYaw);
                float pitchRad = (float) Math.toRadians(cameraPitch);
                cameraFront.x = (float) (Math.cos(yawRad) * Math.cos(pitchRad));
                cameraFront.y = (float) Math.sin(pitchRad);
                cameraFront.z = (float) (Math.sin(yawRad) * Math.cos(pitchRad));
                cameraFront.normalize();
            }
        });

        // Set up mouse button callback for block interaction
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (action != GLFW_PRESS) return;

            if (DEMO_MODE) {
                // Get mouse position and convert to world ray
                double[] mouseX = new double[1];
                double[] mouseY = new double[1];
                glfwGetCursorPos(win, mouseX, mouseY);

                Vector3f rayDir = screenToWorldRay((float) mouseX[0], (float) mouseY[0]);
                RaycastResult hit = Raycaster.cast(world, cameraPos, rayDir, REACH_DISTANCE);

                if (button == GLFW_MOUSE_BUTTON_LEFT && hit != null) {
                    // Place block on the face we hit
                    Vector3i pos = hit.getAdjacentPos();
                    if (pos != null && !world.hasBlock(pos.x, pos.y, pos.z)) {
                        world.addBlock(new Block(pos.x, pos.y, pos.z, currentBlockType));
                    }
                } else if (button == GLFW_MOUSE_BUTTON_RIGHT && hit != null) {
                    // Remove the block we hit
                    Vector3i pos = hit.getBlockPos();
                    world.removeBlock(pos.x, pos.y, pos.z);
                }
            } else {
                // Normal gameplay mode
                if (targetedBlock == null) return;

                if (button == GLFW_MOUSE_BUTTON_LEFT) {
                    placeBlock();
                } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                    removeBlock();
                }
            }
        });

        // Capture mouse
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        // Center window
        var vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vidmode != null) {
            glfwSetWindowPos(window,
                (vidmode.width() - WIDTH) / 2,
                (vidmode.height() - HEIGHT) / 2);
        }

        // Make OpenGL context current
        glfwMakeContextCurrent(window);
        glfwSwapInterval(0); // Disable V-Sync, we'll limit manually

        // Initialize OpenGL
        GL.createCapabilities();

        // Show window
        glfwShowWindow(window);

        // Configure OpenGL state
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glClearColor(0.4f, 0.6f, 0.9f, 1.0f); // Sky blue

        // Initialize rendering components
        CubeMesh.initialize();
        shader = new Shader("shaders/block.vert", "shaders/block.frag");
        blockRenderer = new BlockRenderer(10000); // Support up to 10k blocks

        // Load all block textures into a texture array
        // Order matters: index 0 = grass, 1 = concrete, 2 = wood plank
        blockTextures = new TextureArray(
            "textures/grass.jpg",
            "textures/concrete.jpg",
            "textures/wood_plank.jpg"
        );

        // Create world and add blocks
        world = new World();
        createTestBlocks();

        // Create player spawning on top of the grass floor
        player = new Player(0, 1, 0);
        cameraPos = player.getEyePosition();

        // Set up projection matrix (perspective)
        projection = new Matrix4f().perspective(
            (float) Math.toRadians(70.0), // FOV
            (float) WIDTH / HEIGHT,        // Aspect ratio
            0.1f,                          // Near plane
            1000.0f                        // Far plane
        );

        // Initialize view and combined matrices
        view = new Matrix4f();
        viewProjection = new Matrix4f();

        System.out.println("Initialization complete!");
        if (DEMO_MODE) {
            System.out.println("DEMO MODE: Orbital camera viewer");
            System.out.println("Controls: Mouse drag to orbit, Up/Down arrows to zoom");
            System.out.println("          Left-click to place block, Right-click to remove");
            System.out.println("          Tab to toggle edit mode (show cursor + highlight)");
            System.out.println("          1=Grass, 2=Concrete, 3=Wood Plank, ESC to exit");
        } else {
            System.out.println("Controls: WASD to move, Space to jump, Mouse to look, ESC to exit");
        }
        System.out.println("Rendering " + world.getBlocks().size() + " blocks with instancing");
    }

    /**
     * Creates test blocks in the world.
     * In demo mode, creates a hill scene. Otherwise creates simple test blocks.
     */
    private void createTestBlocks() {
        if (DEMO_MODE) {
            createHillScene();
        } else {
            // Simple test blocks for gameplay mode
            for (int x = -5; x < 5; x++) {
                for (int z = -5; z < 5; z++) {
                    world.addBlock(new Block(x, 0, z, 0));
                }
            }
            // Tower for testing
            for (int y = 1; y <= 5; y++) {
                world.addBlock(new Block(3, y, 3, 0));
            }
        }
    }

    /**
     * Creates a static scene for the orbital camera demo.
     * Features a flat grass terrain with a gentle hill, a house, and a walkway.
     *
     * Block types: 0=grass, 1=concrete, 2=wood plank
     */
    private void createHillScene() {
        int halfSize = 12; // Creates 25x25 area

        // Create base grass terrain with a gentle hill in one corner
        for (int x = -halfSize; x <= halfSize; x++) {
            for (int z = -halfSize; z <= halfSize; z++) {
                // Small hill offset to corner (around x=6, z=6)
                float hillCenterX = 6;
                float hillCenterZ = 6;
                float dx = x - hillCenterX;
                float dz = z - hillCenterZ;
                float dist = (float) Math.sqrt(dx * dx + dz * dz);

                // Gentle hill: max height 3, radius 5
                int height = 0;
                if (dist < 5) {
                    float falloff = (float) Math.cos((dist / 5) * Math.PI / 2);
                    height = (int) (3 * falloff);
                }

                // Add grass blocks from y=0 up to height
                // Skip the very tip of the hill (center point at max height)
                int maxY = (dist < 1 && height == 3) ? height - 1 : height;
                for (int y = 0; y <= maxY; y++) {
                    world.addBlock(new Block(x, y, z, 0)); // grass
                }
            }
        }

        // Build a house at (-5, 1, -5) - 5x4x5 structure
        buildHouse(-5, 1, -5);

        // Build a walkway from house to hill
        buildWalkway();
    }

    /**
     * Builds a simple house with concrete walls and wood plank roof.
     * House is 5 wide (x), 4 tall (y), 5 deep (z).
     */
    private void buildHouse(int startX, int startY, int startZ) {
        int width = 5;
        int height = 4;
        int depth = 5;

        // Walls (concrete = 1)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    // Only build walls (edges) and floor, leave interior hollow
                    boolean isWall = (x == 0 || x == width - 1 || z == 0 || z == depth - 1);
                    boolean isFloor = (y == 0);

                    if (isFloor) {
                        // Wood plank floor
                        world.addBlock(new Block(startX + x, startY + y, startZ + z, 2));
                    } else if (isWall) {
                        // Leave a door opening on front wall (z == 0, middle x)
                        boolean isDoor = (z == 0 && x == width / 2 && y < 2);
                        // Leave window openings on side walls
                        boolean isWindow = ((x == 0 || x == width - 1) && z == depth / 2 && y == 2);

                        if (!isDoor && !isWindow) {
                            world.addBlock(new Block(startX + x, startY + y, startZ + z, 1)); // concrete
                        }
                    }
                }
            }
        }

        // Flat roof (wood plank = 2)
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                world.addBlock(new Block(startX + x, startY + height, startZ + z, 2));
            }
        }
    }

    /**
     * Builds a concrete walkway from the house entrance toward the hill.
     */
    private void buildWalkway() {
        // Walkway from house door at (-3, 1, -5) going toward positive Z and X
        // Start at z=-4 (just outside door) and curve toward the hill

        // Straight section from house
        for (int z = -4; z <= 2; z++) {
            world.addBlock(new Block(-3, 1, z, 1)); // concrete
            world.addBlock(new Block(-2, 1, z, 1)); // 2-wide path
        }

        // Curve toward the hill (diagonal section)
        for (int i = 0; i < 4; i++) {
            world.addBlock(new Block(-2 + i, 1, 2 + i, 1));
            world.addBlock(new Block(-1 + i, 1, 2 + i, 1));
        }
    }

    private void loop() {
        float lastTime = (float) glfwGetTime();

        while (!glfwWindowShouldClose(window)) {
            // Poll events first to ensure fresh input for raycast
            glfwPollEvents();

            float currentTime = (float) glfwGetTime();
            float deltaTime = currentTime - lastTime;

            // 30 FPS limit - skip frame if too fast
            if (deltaTime < TARGET_FRAME_TIME) {
                continue;
            }
            lastTime = currentTime;

            // FPS counter
            frameCount++;
            fpsTimer += deltaTime;
            if (fpsTimer >= 1.0f) {
                glfwSetWindowTitle(window, TITLE + " - FPS: " + frameCount);
                frameCount = 0;
                fpsTimer = 0;
            }

            if (DEMO_MODE) {
                // Handle zoom with up/down arrow keys
                if (glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS) {
                    orbitRadius -= ORBIT_ZOOM_SPEED * deltaTime;
                    if (orbitRadius < ORBIT_RADIUS_MIN) orbitRadius = ORBIT_RADIUS_MIN;
                }
                if (glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS) {
                    orbitRadius += ORBIT_ZOOM_SPEED * deltaTime;
                    if (orbitRadius > ORBIT_RADIUS_MAX) orbitRadius = ORBIT_RADIUS_MAX;
                }

                // Orbital camera mode: calculate camera position on sphere around center
                float yawRad = (float) Math.toRadians(orbitYaw);
                float pitchRad = (float) Math.toRadians(orbitPitch);

                cameraPos.x = ORBIT_CENTER.x + orbitRadius * (float) Math.cos(pitchRad) * (float) Math.cos(yawRad);
                cameraPos.y = ORBIT_CENTER.y + orbitRadius * (float) Math.sin(pitchRad);
                cameraPos.z = ORBIT_CENTER.z + orbitRadius * (float) Math.cos(pitchRad) * (float) Math.sin(yawRad);

                // Camera looks at center
                view.identity().lookAt(cameraPos, ORBIT_CENTER, cameraUp);

                // Perform raycast for block highlighting when in edit mode
                if (editMode) {
                    double[] mouseX = new double[1];
                    double[] mouseY = new double[1];
                    glfwGetCursorPos(window, mouseX, mouseY);
                    Vector3f rayDir = screenToWorldRay((float) mouseX[0], (float) mouseY[0]);
                    targetedBlock = Raycaster.cast(world, cameraPos, rayDir, REACH_DISTANCE);
                } else {
                    targetedBlock = null;
                }
            } else {
                // Normal gameplay mode
                // Process input
                processInput(deltaTime);

                // Update player physics
                player.update(deltaTime, world);

                // Reset player if fallen 20 blocks
                if (player.getPosition().y < -20) {
                    player.reset(0, 1, 0);
                }

                // Update camera position from player eye position
                cameraPos = player.getEyePosition();

                // Perform raycast to find targeted block
                targetedBlock = Raycaster.cast(world, cameraPos, cameraFront, REACH_DISTANCE);

                // Update view matrix
                Vector3f target = new Vector3f(cameraPos).add(cameraFront);
                view.identity().lookAt(cameraPos, target, cameraUp);
            }

            // Combine view and projection
            projection.mul(view, viewProjection);

            // Compute inverse for screen-to-world ray conversion
            viewProjection.invert(inverseViewProjection);

            // Clear screen
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Render blocks
            shader.use();

            // Bind texture array and set sampler uniform
            blockTextures.bind(0);
            shader.setInt("uTextureArray", 0);

            // Batch and render all blocks with optional highlight
            Vector3i highlightPos = targetedBlock != null ? targetedBlock.getBlockPos() : null;
            blockRenderer.render(world.getBlocks(), shader, viewProjection, highlightPos);

            // Swap buffers
            glfwSwapBuffers(window);
        }
    }

    private void processInput(float deltaTime) {
        // Calculate movement input
        float forward = 0;
        float strafe = 0;

        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            forward += 1;
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            forward -= 1;
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            strafe -= 1;
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            strafe += 1;
        }

        // Apply horizontal movement
        player.move(forward, strafe, cameraYaw, deltaTime);

        // Jump
        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
            player.jump();
        }
    }

    /**
     * Places a block on the face of the targeted block.
     */
    private void placeBlock() {
        if (targetedBlock == null || targetedBlock.getFace() == null) return;

        Vector3i pos = targetedBlock.getAdjacentPos();

        // Don't place if it would intersect the player
        if (wouldIntersectPlayer(pos.x, pos.y, pos.z)) return;

        // Don't place if there's already a block there
        if (world.hasBlock(pos.x, pos.y, pos.z)) return;

        world.addBlock(new Block(pos.x, pos.y, pos.z, 0));
    }

    /**
     * Removes the targeted block.
     */
    private void removeBlock() {
        if (targetedBlock == null) return;

        Vector3i pos = targetedBlock.getBlockPos();
        world.removeBlock(pos.x, pos.y, pos.z);
        targetedBlock = null;
    }

    /**
     * Converts screen coordinates to a world-space ray direction.
     * Uses the inverse view-projection matrix to unproject screen points.
     *
     * @param screenX mouse X position (0 = left edge)
     * @param screenY mouse Y position (0 = top edge)
     * @return normalized ray direction in world space
     */
    private Vector3f screenToWorldRay(float screenX, float screenY) {
        // Get actual window size (cursor coords use window size, not framebuffer size)
        int[] winWidth = new int[1];
        int[] winHeight = new int[1];
        glfwGetWindowSize(window, winWidth, winHeight);

        // Apply cursor offset correction (compensates for system cursor hotspot)
        screenY -= 20.0f;

        // Convert screen coords to normalized device coordinates (-1 to 1)
        float ndcX = (2.0f * screenX) / winWidth[0] - 1.0f;
        float ndcY = 1.0f - (2.0f * screenY) / winHeight[0];  // Flip Y (screen Y is top-down)

        // Create points on near and far planes in clip space
        Vector3f nearPoint = new Vector3f(ndcX, ndcY, -1.0f);
        Vector3f farPoint = new Vector3f(ndcX, ndcY, 1.0f);

        // Unproject to world space using inverse view-projection
        Vector3f nearWorld = inverseViewProjection.transformProject(nearPoint);
        Vector3f farWorld = inverseViewProjection.transformProject(farPoint);

        // Calculate and normalize ray direction
        Vector3f rayDir = new Vector3f(farWorld).sub(nearWorld).normalize();
        return rayDir;
    }

    /**
     * Checks if placing a block at the given position would intersect with the player.
     */
    private boolean wouldIntersectPlayer(int bx, int by, int bz) {
        Vector3f p = player.getPosition();
        float hw = 0.25f;  // Half-width of player collision box (WIDTH/2)
        float h = 0.9f;    // Height of player collision box

        // AABB intersection test
        return bx + 1 > p.x - hw && bx < p.x + hw &&
               by + 1 > p.y && by < p.y + h &&
               bz + 1 > p.z - hw && bz < p.z + hw;
    }

    private void cleanup() {
        System.out.println("Cleaning up...");

        // Clean up rendering resources
        blockRenderer.close();
        shader.close();
        CubeMesh.cleanup();
        blockTextures.close();

        // Clean up GLFW
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();

        var callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }

        System.out.println("Cleanup complete!");
    }
}
