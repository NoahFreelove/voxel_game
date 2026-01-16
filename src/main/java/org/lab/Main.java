package org.lab;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lab.engine.Player;
import org.lab.engine.Shader;
import org.lab.render.BlockRenderer;
import org.lab.render.CubeMesh;
import org.lab.render.Texture;
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

    private long window;
    private Shader shader;
    private BlockRenderer blockRenderer;
    private Texture grassTexture;

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
    private static final float REACH_DISTANCE = 5.0f;

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
        });

        // Set up mouse button callback for block interaction
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (action != GLFW_PRESS || targetedBlock == null) return;

            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                placeBlock();
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                removeBlock();
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
        glfwSwapInterval(1); // V-Sync

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

        // Load grass texture with REPEAT wrap mode for tiling
        grassTexture = new Texture("textures/grass.jpg");
        grassTexture.bind();
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

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
        System.out.println("Controls: WASD to move, Space to jump, Mouse to look, ESC to exit");
        System.out.println("Rendering " + world.getBlocks().size() + " blocks with instancing");
    }

    /**
     * Creates test blocks in the world.
     */
    private void createTestBlocks() {
        // Create a 10x10 floor of grass blocks at y=0
        for (int x = -5; x < 5; x++) {
            for (int z = -5; z < 5; z++) {
                world.addBlock(new Block(x, 0, z, 0)); // All use single grass texture
            }
        }

        // Add some blocks underneath for depth
        for (int x = -5; x < 5; x++) {
            for (int z = -5; z < 5; z++) {
                world.addBlock(new Block(x, -1, z, 0));
            }
        }

        for (int x = -5; x < 5; x++) {
            for (int z = -5; z < 5; z++) {
                world.addBlock(new Block(x, -2, z, 0));
            }
        }

        // Add a small tower to test vertical collision
        for (int y = 1; y <= 5; y++) {
            world.addBlock(new Block(3, y, 3, 0));
        }
    }

    private void loop() {
        float lastTime = (float) glfwGetTime();

        while (!glfwWindowShouldClose(window)) {
            float currentTime = (float) glfwGetTime();
            float deltaTime = currentTime - lastTime;
            lastTime = currentTime;

            // Process input
            processInput(deltaTime);

            // Update player physics
            player.update(deltaTime, world);

            // Update camera position from player eye position
            cameraPos = player.getEyePosition();

            // Perform raycast to find targeted block
            targetedBlock = Raycaster.cast(world, cameraPos, cameraFront, REACH_DISTANCE);

            // Update view matrix
            Vector3f target = new Vector3f(cameraPos).add(cameraFront);
            view.identity().lookAt(cameraPos, target, cameraUp);

            // Combine view and projection
            projection.mul(view, viewProjection);

            // Clear screen
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Render blocks
            shader.use();

            // Set uniforms - using grass texture (single texture, not atlas)
            grassTexture.bind(0);
            shader.setInt("uTexture", 0);
            shader.setFloat("uAtlasTileSize", 1.0f);  // Full texture = 1.0
            shader.setInt("uAtlasTilesPerRow", 1);     // Single texture
            shader.setVector3f("uLightDir", 0.3f, -0.8f, 0.5f); // Sun direction
            shader.setVector3f("uAmbientColor", 0.3f, 0.3f, 0.35f); // Slight blue ambient

            // Batch and render all blocks with optional highlight
            Vector3i highlightPos = targetedBlock != null ? targetedBlock.getBlockPos() : null;
            blockRenderer.render(world.getBlocks(), shader, viewProjection, highlightPos);

            // Swap buffers and poll events
            glfwSwapBuffers(window);
            glfwPollEvents();
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
        grassTexture.close();

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
