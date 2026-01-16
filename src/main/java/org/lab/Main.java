package org.lab;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lab.engine.Shader;
import org.lab.render.BlockRenderer;
import org.lab.render.CubeMesh;
import org.lab.render.TextureArray;
import org.lab.world.Block;
import org.lab.world.World;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Orbital Camera Demo - Minimal viewer for the voxel scene.
 * Arrow keys to rotate, +/- to zoom, ESC to exit.
 */
public class Main {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final String TITLE = "Voxel Game - Orbital Camera Demo";

    // Orbital camera settings
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
    private TextureArray blockTextures;

    private World world;
    private Matrix4f projection;
    private Matrix4f view;
    private Matrix4f viewProjection;

    // Camera state
    private Vector3f cameraPos = new Vector3f(0, 2, 8);
    private Vector3f cameraUp = new Vector3f(0, 1, 0);

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

        // Set up key callback - ESC to exit only
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                glfwSetWindowShouldClose(win, true);
            }
        });

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
        createHillScene();

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

        System.out.println("Orbital Camera Demo");
        System.out.println("Controls: Arrow keys to rotate, +/- to zoom, ESC to exit");
        System.out.println("Rendering " + world.getBlocks().size() + " blocks with instancing");
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
            // Poll events first
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

            // Arrow key camera control
            float rotateSpeed = 60f;  // degrees per second
            if (glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS) {
                orbitYaw -= rotateSpeed * deltaTime;
            }
            if (glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS) {
                orbitYaw += rotateSpeed * deltaTime;
            }
            if (glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS) {
                orbitPitch += rotateSpeed * deltaTime;
                if (orbitPitch > 89f) orbitPitch = 89f;
            }
            if (glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS) {
                orbitPitch -= rotateSpeed * deltaTime;
                if (orbitPitch < -89f) orbitPitch = -89f;
            }

            // Zoom with +/- keys
            if (glfwGetKey(window, GLFW_KEY_EQUAL) == GLFW_PRESS) {
                orbitRadius -= ORBIT_ZOOM_SPEED * deltaTime;
                if (orbitRadius < ORBIT_RADIUS_MIN) orbitRadius = ORBIT_RADIUS_MIN;
            }
            if (glfwGetKey(window, GLFW_KEY_MINUS) == GLFW_PRESS) {
                orbitRadius += ORBIT_ZOOM_SPEED * deltaTime;
                if (orbitRadius > ORBIT_RADIUS_MAX) orbitRadius = ORBIT_RADIUS_MAX;
            }

            // Calculate camera position on sphere around center
            float yawRad = (float) Math.toRadians(orbitYaw);
            float pitchRad = (float) Math.toRadians(orbitPitch);

            cameraPos.x = ORBIT_CENTER.x + orbitRadius * (float) Math.cos(pitchRad) * (float) Math.cos(yawRad);
            cameraPos.y = ORBIT_CENTER.y + orbitRadius * (float) Math.sin(pitchRad);
            cameraPos.z = ORBIT_CENTER.z + orbitRadius * (float) Math.cos(pitchRad) * (float) Math.sin(yawRad);

            // Camera looks at center
            view.identity().lookAt(cameraPos, ORBIT_CENTER, cameraUp);

            // Combine view and projection
            projection.mul(view, viewProjection);

            // Clear screen
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Render blocks
            shader.use();

            // Bind texture array and set sampler uniform
            blockTextures.bind(0);
            shader.setInt("uTextureArray", 0);

            // Render all blocks (no highlight)
            blockRenderer.render(world.getBlocks(), shader, viewProjection, null);

            // Swap buffers
            glfwSwapBuffers(window);
        }
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
