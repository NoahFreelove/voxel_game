package org.lab.render;

import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Element Buffer Object (EBO) wrapper.
 * Stores index data for indexed drawing on the GPU.
 */
public class EBO implements AutoCloseable {
    private final int id;
    private int count;

    public EBO() {
        this.id = glGenBuffers();
    }

    /**
     * Binds this EBO.
     */
    public void bind() {
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, id);
    }

    /**
     * Unbinds any EBO.
     */
    public void unbind() {
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /**
     * Uploads index data to the GPU.
     * @param indices int array of indices
     * @param usage GL_STATIC_DRAW, GL_DYNAMIC_DRAW, or GL_STREAM_DRAW
     */
    public void uploadData(int[] indices, int usage) {
        this.count = indices.length;
        IntBuffer buffer = MemoryUtil.memAllocInt(indices.length);
        buffer.put(indices).flip();
        bind();
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, buffer, usage);
        MemoryUtil.memFree(buffer);
    }

    /**
     * Returns the number of indices in this buffer.
     */
    public int getCount() {
        return count;
    }

    public int getId() {
        return id;
    }

    @Override
    public void close() {
        glDeleteBuffers(id);
    }
}
