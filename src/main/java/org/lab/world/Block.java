package org.lab.world;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Block data structure.
 * Contains position, rotation, scale, and texture index.
 * No OpenGL resources - pure data for instanced rendering.
 */
public class Block {
    private final Vector3f position;
    private final Vector3f rotation; // Euler angles in radians
    private float scale;
    private int textureIndex;

    // Cached model matrix, recomputed when dirty
    private final Matrix4f modelMatrix = new Matrix4f();
    private boolean dirty = true;

    /**
     * Creates a block at the specified position with default rotation and scale.
     */
    public Block(float x, float y, float z) {
        this.position = new Vector3f(x, y, z);
        this.rotation = new Vector3f(0, 0, 0);
        this.scale = 1.0f;
        this.textureIndex = 0;
    }

    /**
     * Creates a block at the specified position with texture index.
     */
    public Block(float x, float y, float z, int textureIndex) {
        this(x, y, z);
        this.textureIndex = textureIndex;
    }

    /**
     * Creates a block with full parameters.
     */
    public Block(Vector3f position, Vector3f rotation, float scale, int textureIndex) {
        this.position = new Vector3f(position);
        this.rotation = new Vector3f(rotation);
        this.scale = scale;
        this.textureIndex = textureIndex;
    }

    public Vector3f getPosition() {
        return position;
    }

    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
        dirty = true;
    }

    public Vector3f getRotation() {
        return rotation;
    }

    public void setRotation(float rx, float ry, float rz) {
        rotation.set(rx, ry, rz);
        dirty = true;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
        dirty = true;
    }

    public int getTextureIndex() {
        return textureIndex;
    }

    public void setTextureIndex(int textureIndex) {
        this.textureIndex = textureIndex;
    }

    /**
     * Returns the model matrix for this block.
     * Computes T * Ry * Rx * Rz * S (translation, rotation, scale).
     * Result is cached until position/rotation/scale changes.
     */
    public Matrix4f getModelMatrix() {
        if (dirty) {
            modelMatrix.identity()
                .translate(position)
                .rotateY(rotation.y)
                .rotateX(rotation.x)
                .rotateZ(rotation.z)
                .scale(scale);
            dirty = false;
        }
        return modelMatrix;
    }

    /**
     * Writes instance data to the given array starting at offset.
     * Format: position(3) + rotation(3) + scale(1) + textureIndex(1) = 8 floats
     *
     * @param data   target array
     * @param offset starting index
     * @return new offset (offset + 8)
     */
    public int writeInstanceData(float[] data, int offset) {
        data[offset++] = position.x;
        data[offset++] = position.y;
        data[offset++] = position.z;
        data[offset++] = rotation.x;
        data[offset++] = rotation.y;
        data[offset++] = rotation.z;
        data[offset++] = scale;
        data[offset++] = (float) textureIndex;
        return offset;
    }
}
