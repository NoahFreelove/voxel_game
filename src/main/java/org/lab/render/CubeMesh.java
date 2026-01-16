package org.lab.render;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Static shared cube geometry for instanced rendering.
 * All blocks share this single VAO/VBO/EBO.
 *
 * Vertex format: position(3) + texCoord(2) + normal(3) = 8 floats per vertex
 * 24 vertices (4 per face for correct UVs), 36 indices
 */
public final class CubeMesh {
    private static VAO vao;
    private static VBO vbo;
    private static EBO ebo;
    private static boolean initialized = false;

    // Stride in bytes: 8 floats * 4 bytes = 32 bytes
    private static final int STRIDE = 8 * Float.BYTES;

    private CubeMesh() {} // Prevent instantiation

    /**
     * Initializes the shared cube mesh. Must be called once after OpenGL context is created.
     */
    public static void initialize() {
        if (initialized) return;

        // Cube vertices: position(3) + texCoord(2) + normal(3)
        // Each face has 4 vertices with correct UVs and normals
        float[] vertices = {
            // Front face (Z+) - normal (0, 0, 1)
            -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,  0.0f,  0.0f,  1.0f,
             0.5f, -0.5f,  0.5f,  1.0f, 0.0f,  0.0f,  0.0f,  1.0f,
             0.5f,  0.5f,  0.5f,  1.0f, 1.0f,  0.0f,  0.0f,  1.0f,
            -0.5f,  0.5f,  0.5f,  0.0f, 1.0f,  0.0f,  0.0f,  1.0f,

            // Back face (Z-) - normal (0, 0, -1)
             0.5f, -0.5f, -0.5f,  0.0f, 0.0f,  0.0f,  0.0f, -1.0f,
            -0.5f, -0.5f, -0.5f,  1.0f, 0.0f,  0.0f,  0.0f, -1.0f,
            -0.5f,  0.5f, -0.5f,  1.0f, 1.0f,  0.0f,  0.0f, -1.0f,
             0.5f,  0.5f, -0.5f,  0.0f, 1.0f,  0.0f,  0.0f, -1.0f,

            // Top face (Y+) - normal (0, 1, 0)
            -0.5f,  0.5f,  0.5f,  0.0f, 0.0f,  0.0f,  1.0f,  0.0f,
             0.5f,  0.5f,  0.5f,  1.0f, 0.0f,  0.0f,  1.0f,  0.0f,
             0.5f,  0.5f, -0.5f,  1.0f, 1.0f,  0.0f,  1.0f,  0.0f,
            -0.5f,  0.5f, -0.5f,  0.0f, 1.0f,  0.0f,  1.0f,  0.0f,

            // Bottom face (Y-) - normal (0, -1, 0)
            -0.5f, -0.5f, -0.5f,  0.0f, 0.0f,  0.0f, -1.0f,  0.0f,
             0.5f, -0.5f, -0.5f,  1.0f, 0.0f,  0.0f, -1.0f,  0.0f,
             0.5f, -0.5f,  0.5f,  1.0f, 1.0f,  0.0f, -1.0f,  0.0f,
            -0.5f, -0.5f,  0.5f,  0.0f, 1.0f,  0.0f, -1.0f,  0.0f,

            // Right face (X+) - normal (1, 0, 0)
             0.5f, -0.5f,  0.5f,  0.0f, 0.0f,  1.0f,  0.0f,  0.0f,
             0.5f, -0.5f, -0.5f,  1.0f, 0.0f,  1.0f,  0.0f,  0.0f,
             0.5f,  0.5f, -0.5f,  1.0f, 1.0f,  1.0f,  0.0f,  0.0f,
             0.5f,  0.5f,  0.5f,  0.0f, 1.0f,  1.0f,  0.0f,  0.0f,

            // Left face (X-) - normal (-1, 0, 0)
            -0.5f, -0.5f, -0.5f,  0.0f, 0.0f, -1.0f,  0.0f,  0.0f,
            -0.5f, -0.5f,  0.5f,  1.0f, 0.0f, -1.0f,  0.0f,  0.0f,
            -0.5f,  0.5f,  0.5f,  1.0f, 1.0f, -1.0f,  0.0f,  0.0f,
            -0.5f,  0.5f, -0.5f,  0.0f, 1.0f, -1.0f,  0.0f,  0.0f,
        };

        // Indices for 6 faces, 2 triangles each = 36 indices
        int[] indices = {
            // Front
            0, 1, 2,   2, 3, 0,
            // Back
            4, 5, 6,   6, 7, 4,
            // Top
            8, 9, 10,  10, 11, 8,
            // Bottom
            12, 13, 14, 14, 15, 12,
            // Right
            16, 17, 18, 18, 19, 16,
            // Left
            20, 21, 22, 22, 23, 20,
        };

        // Create and set up VAO
        vao = new VAO();
        vao.bind();

        // Upload vertex data
        vbo = new VBO(GL_ARRAY_BUFFER);
        vbo.uploadData(vertices, GL_STATIC_DRAW);

        // Upload index data
        ebo = new EBO();
        ebo.uploadData(indices, GL_STATIC_DRAW);

        // Configure vertex attributes (per-vertex data)
        // Location 0: position (vec3)
        vao.linkAttribute(0, 3, GL_FLOAT, false, STRIDE, 0);
        // Location 1: texCoord (vec2)
        vao.linkAttribute(1, 2, GL_FLOAT, false, STRIDE, 3 * Float.BYTES);
        // Location 2: normal (vec3)
        vao.linkAttribute(2, 3, GL_FLOAT, false, STRIDE, 5 * Float.BYTES);

        vao.unbind();
        vbo.unbind();

        initialized = true;
    }

    /**
     * Binds the cube VAO for rendering.
     */
    public static void bind() {
        if (!initialized) {
            throw new IllegalStateException("CubeMesh not initialized. Call initialize() first.");
        }
        vao.bind();
    }

    /**
     * Unbinds the cube VAO.
     */
    public static void unbind() {
        vao.unbind();
    }

    /**
     * Returns the number of indices (36 for a cube).
     */
    public static int getIndexCount() {
        return 36;
    }

    /**
     * Returns the VAO for external use (e.g., linking instance attributes).
     */
    public static VAO getVAO() {
        return vao;
    }

    /**
     * Cleans up GPU resources. Call on shutdown.
     */
    public static void cleanup() {
        if (initialized) {
            ebo.close();
            vbo.close();
            vao.close();
            initialized = false;
        }
    }
}
