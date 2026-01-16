package org.lab.render;

import org.joml.Matrix4f;
import org.joml.Vector3i;
import org.lab.engine.Shader;
import org.lab.world.Block;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Instanced block renderer.
 * Batches blocks into a single draw call using instanced rendering.
 *
 * Instance data format (9 floats = 36 bytes per instance):
 * - position (vec3)
 * - rotation (vec3) - Euler angles
 * - scale (float)
 * - textureIndex (float)
 * - highlight (float) - 1.0 if highlighted, 0.0 otherwise
 */
public class BlockRenderer implements AutoCloseable {
    // Instance attribute stride: 9 floats * 4 bytes = 36 bytes
    private static final int INSTANCE_STRIDE = 9 * Float.BYTES;
    private static final int FLOATS_PER_INSTANCE = 9;

    private final VBO instanceVbo;
    private final FloatBuffer instanceBuffer;
    private final int maxInstances;

    private int instanceCount;
    private boolean batching;

    /**
     * Creates a block renderer with specified maximum instance capacity.
     *
     * @param maxInstances maximum number of blocks that can be rendered in one batch
     */
    public BlockRenderer(int maxInstances) {
        this.maxInstances = maxInstances;

        // Allocate CPU-side buffer for instance data
        instanceBuffer = MemoryUtil.memAllocFloat(maxInstances * FLOATS_PER_INSTANCE);

        // Create and configure instance VBO
        instanceVbo = new VBO(GL_ARRAY_BUFFER);
        instanceVbo.allocate((long) maxInstances * INSTANCE_STRIDE, GL_DYNAMIC_DRAW);

        // Link instance attributes to the cube mesh VAO
        CubeMesh.bind();
        instanceVbo.bind();

        VAO vao = CubeMesh.getVAO();

        // Location 3: iPosition (vec3)
        vao.linkInstancedAttribute(3, 3, GL_FLOAT, false, INSTANCE_STRIDE, 0);
        // Location 4: iRotation (vec3)
        vao.linkInstancedAttribute(4, 3, GL_FLOAT, false, INSTANCE_STRIDE, 3 * Float.BYTES);
        // Location 5: iScale (float)
        vao.linkInstancedAttribute(5, 1, GL_FLOAT, false, INSTANCE_STRIDE, 6 * Float.BYTES);
        // Location 6: iTexIndex (float)
        vao.linkInstancedAttribute(6, 1, GL_FLOAT, false, INSTANCE_STRIDE, 7 * Float.BYTES);
        // Location 7: iHighlight (float)
        vao.linkInstancedAttribute(7, 1, GL_FLOAT, false, INSTANCE_STRIDE, 8 * Float.BYTES);

        instanceVbo.unbind();
        CubeMesh.unbind();
    }

    /**
     * Begins a new batch. Call before adding blocks.
     */
    public void begin() {
        instanceCount = 0;
        instanceBuffer.clear();
        batching = true;
    }

    /**
     * Adds a block to the current batch.
     *
     * @param block     the block to add
     * @param highlight 1.0f if highlighted, 0.0f otherwise
     */
    public void addBlock(Block block, float highlight) {
        if (!batching) {
            throw new IllegalStateException("Must call begin() before addBlock()");
        }
        if (instanceCount >= maxInstances) {
            throw new IllegalStateException("Exceeded maximum instance count: " + maxInstances);
        }

        // Write instance data directly to buffer
        instanceBuffer.put(block.getPosition().x);
        instanceBuffer.put(block.getPosition().y);
        instanceBuffer.put(block.getPosition().z);
        instanceBuffer.put(block.getRotation().x);
        instanceBuffer.put(block.getRotation().y);
        instanceBuffer.put(block.getRotation().z);
        instanceBuffer.put(block.getScale());
        instanceBuffer.put((float) block.getTextureIndex());
        instanceBuffer.put(highlight);

        instanceCount++;
    }

    /**
     * Ends the batch and uploads data to GPU.
     */
    public void end() {
        if (!batching) {
            throw new IllegalStateException("Must call begin() before end()");
        }
        batching = false;

        if (instanceCount == 0) return;

        // Upload instance data to GPU
        instanceBuffer.flip();
        instanceVbo.bind();
        instanceVbo.updateSubData(0, instanceBuffer);
        instanceVbo.unbind();
    }

    /**
     * Renders all batched blocks with instancing.
     *
     * @param shader         shader to use (must be bound and have uniforms set)
     * @param viewProjection combined view-projection matrix
     */
    public void render(Shader shader, Matrix4f viewProjection) {
        if (instanceCount == 0) return;

        shader.use();
        shader.setMatrix4f("uViewProjection", viewProjection);

        CubeMesh.bind();
        glDrawElementsInstanced(GL_TRIANGLES, CubeMesh.getIndexCount(), GL_UNSIGNED_INT, 0, instanceCount);
        CubeMesh.unbind();
    }

    /**
     * Convenience method: batches and renders blocks in one call.
     *
     * @param blocks         iterable of blocks to render
     * @param shader         shader to use
     * @param viewProjection combined view-projection matrix
     * @param highlightPos   position of highlighted block, or null for no highlight
     */
    public void render(Iterable<Block> blocks, Shader shader, Matrix4f viewProjection, Vector3i highlightPos) {
        begin();
        for (Block block : blocks) {
            float highlight = 0.0f;
            if (highlightPos != null) {
                int bx = (int) Math.floor(block.getPosition().x);
                int by = (int) Math.floor(block.getPosition().y);
                int bz = (int) Math.floor(block.getPosition().z);
                if (bx == highlightPos.x && by == highlightPos.y && bz == highlightPos.z) {
                    highlight = 1.0f;
                }
            }
            addBlock(block, highlight);
        }
        end();
        render(shader, viewProjection);
    }

    public int getInstanceCount() {
        return instanceCount;
    }

    public int getMaxInstances() {
        return maxInstances;
    }

    @Override
    public void close() {
        instanceVbo.close();
        MemoryUtil.memFree(instanceBuffer);
    }
}
