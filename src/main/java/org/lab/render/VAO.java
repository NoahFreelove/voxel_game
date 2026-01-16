package org.lab.render;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Vertex Array Object (VAO) wrapper.
 * Stores vertex attribute configuration and links VBOs.
 */
public class VAO implements AutoCloseable {
    private final int id;

    public VAO() {
        this.id = glGenVertexArrays();
    }

    /**
     * Binds this VAO.
     */
    public void bind() {
        glBindVertexArray(id);
    }

    /**
     * Unbinds any VAO.
     */
    public void unbind() {
        glBindVertexArray(0);
    }

    /**
     * Configures a vertex attribute pointer for per-vertex data.
     * The VBO must be bound before calling this method.
     *
     * @param location   shader attribute location
     * @param size       number of components (1, 2, 3, or 4)
     * @param type       data type (GL_FLOAT, GL_INT, etc.)
     * @param normalized whether to normalize fixed-point data
     * @param stride     byte stride between consecutive attributes (0 for tightly packed)
     * @param offset     byte offset of the first component
     */
    public void linkAttribute(int location, int size, int type, boolean normalized, int stride, long offset) {
        glVertexAttribPointer(location, size, type, normalized, stride, offset);
        glEnableVertexAttribArray(location);
    }

    /**
     * Configures a vertex attribute pointer for per-instance data.
     * The VBO must be bound before calling this method.
     * Uses glVertexAttribDivisor(location, 1) to advance once per instance.
     *
     * @param location   shader attribute location
     * @param size       number of components (1, 2, 3, or 4)
     * @param type       data type (GL_FLOAT, GL_INT, etc.)
     * @param normalized whether to normalize fixed-point data
     * @param stride     byte stride between consecutive attributes
     * @param offset     byte offset of the first component
     */
    public void linkInstancedAttribute(int location, int size, int type, boolean normalized, int stride, long offset) {
        glVertexAttribPointer(location, size, type, normalized, stride, offset);
        glEnableVertexAttribArray(location);
        // Divisor of 1 means this attribute advances once per instance
        glVertexAttribDivisor(location, 1);
    }

    /**
     * Disables a vertex attribute.
     */
    public void disableAttribute(int location) {
        glDisableVertexAttribArray(location);
    }

    public int getId() {
        return id;
    }

    @Override
    public void close() {
        glDeleteVertexArrays(id);
    }
}
