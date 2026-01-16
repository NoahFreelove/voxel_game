package org.lab.render;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Vertex Buffer Object (VBO) wrapper.
 * Stores vertex data (positions, UVs, normals, etc.) on the GPU.
 */
public class VBO implements AutoCloseable {
    private final int id;
    private final int target;

    /**
     * Creates a VBO with the specified target (GL_ARRAY_BUFFER or GL_ELEMENT_ARRAY_BUFFER).
     */
    public VBO(int target) {
        this.target = target;
        this.id = glGenBuffers();
    }

    /**
     * Creates a VBO for vertex data (GL_ARRAY_BUFFER).
     */
    public VBO() {
        this(GL_ARRAY_BUFFER);
    }

    /**
     * Binds this VBO to its target.
     */
    public void bind() {
        glBindBuffer(target, id);
    }

    /**
     * Unbinds any VBO from this target.
     */
    public void unbind() {
        glBindBuffer(target, 0);
    }

    /**
     * Uploads float data to the GPU.
     * @param data float array to upload
     * @param usage GL_STATIC_DRAW, GL_DYNAMIC_DRAW, or GL_STREAM_DRAW
     */
    public void uploadData(float[] data, int usage) {
        FloatBuffer buffer = MemoryUtil.memAllocFloat(data.length);
        buffer.put(data).flip();
        bind();
        glBufferData(target, buffer, usage);
        MemoryUtil.memFree(buffer);
    }

    /**
     * Uploads int data to the GPU (typically for index buffers).
     * @param data int array to upload
     * @param usage GL_STATIC_DRAW, GL_DYNAMIC_DRAW, or GL_STREAM_DRAW
     */
    public void uploadData(int[] data, int usage) {
        IntBuffer buffer = MemoryUtil.memAllocInt(data.length);
        buffer.put(data).flip();
        bind();
        glBufferData(target, buffer, usage);
        MemoryUtil.memFree(buffer);
    }

    /**
     * Allocates buffer storage without uploading data.
     * Useful for dynamic buffers that will be updated with updateSubData().
     * @param sizeInBytes size to allocate in bytes
     * @param usage GL_STATIC_DRAW, GL_DYNAMIC_DRAW, or GL_STREAM_DRAW
     */
    public void allocate(long sizeInBytes, int usage) {
        bind();
        glBufferData(target, sizeInBytes, usage);
    }

    /**
     * Updates a portion of the buffer data.
     * Buffer must be bound and have been previously allocated.
     * @param offsetInBytes byte offset into the buffer
     * @param data float data to upload
     */
    public void updateSubData(long offsetInBytes, float[] data) {
        FloatBuffer buffer = MemoryUtil.memAllocFloat(data.length);
        buffer.put(data).flip();
        glBufferSubData(target, offsetInBytes, buffer);
        MemoryUtil.memFree(buffer);
    }

    /**
     * Updates a portion of the buffer with a FloatBuffer.
     * @param offsetInBytes byte offset into the buffer
     * @param buffer FloatBuffer with data (must be flipped)
     */
    public void updateSubData(long offsetInBytes, FloatBuffer buffer) {
        glBufferSubData(target, offsetInBytes, buffer);
    }

    public int getId() {
        return id;
    }

    public int getTarget() {
        return target;
    }

    @Override
    public void close() {
        glDeleteBuffers(id);
    }
}
