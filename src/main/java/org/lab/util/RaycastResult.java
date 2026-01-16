package org.lab.util;

import org.joml.Vector3i;

/**
 * Stores the result of a raycast against voxel blocks.
 * Contains the hit block position and the face that was hit.
 */
public class RaycastResult {

    /**
     * Represents a face of a block with its normal direction.
     */
    public enum Face {
        NORTH(0, 0, -1),   // -Z
        SOUTH(0, 0, 1),    // +Z
        EAST(1, 0, 0),     // +X
        WEST(-1, 0, 0),    // -X
        TOP(0, 1, 0),      // +Y
        BOTTOM(0, -1, 0);  // -Y

        public final int nx, ny, nz;

        Face(int nx, int ny, int nz) {
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }
    }

    private final int x, y, z;
    private final Face face;
    private final float distance;

    public RaycastResult(int x, int y, int z, Face face, float distance) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.face = face;
        this.distance = distance;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public Face getFace() {
        return face;
    }

    public float getDistance() {
        return distance;
    }

    /**
     * Returns the block position as a Vector3i.
     */
    public Vector3i getBlockPos() {
        return new Vector3i(x, y, z);
    }

    /**
     * Returns the position adjacent to the hit block (based on the hit face).
     * This is where a new block would be placed.
     */
    public Vector3i getAdjacentPos() {
        return new Vector3i(x + face.nx, y + face.ny, z + face.nz);
    }
}
