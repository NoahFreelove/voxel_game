package org.lab.util;

import org.joml.Vector3f;
import org.lab.world.World;

/**
 * Voxel raycaster using the DDA (Digital Differential Analyzer) algorithm.
 * Efficiently finds the first block hit by a ray in voxel space.
 */
public class Raycaster {

    /**
     * Casts a ray through the voxel world and returns the first block hit.
     *
     * @param world   the world to raycast against
     * @param origin  ray origin position
     * @param dir     ray direction (should be normalized)
     * @param maxDist maximum distance to check
     * @return RaycastResult if a block was hit, null otherwise
     */
    public static RaycastResult cast(World world, Vector3f origin, Vector3f dir, float maxDist) {
        // Current voxel position
        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);

        // Direction to step in each axis
        int stepX = dir.x > 0 ? 1 : -1;
        int stepY = dir.y > 0 ? 1 : -1;
        int stepZ = dir.z > 0 ? 1 : -1;

        // Distance along ray to next voxel boundary in each axis
        float tMaxX = intbound(origin.x, dir.x);
        float tMaxY = intbound(origin.y, dir.y);
        float tMaxZ = intbound(origin.z, dir.z);

        // Distance along ray equal to one voxel in each axis
        float tDeltaX = Math.abs(1.0f / dir.x);
        float tDeltaY = Math.abs(1.0f / dir.y);
        float tDeltaZ = Math.abs(1.0f / dir.z);

        RaycastResult.Face lastFace = null;
        float dist = 0;

        // Step through voxels until we hit something or exceed max distance
        while (dist < maxDist) {
            // Check if current voxel contains a block
            if (world.hasBlock(x, y, z)) {
                return new RaycastResult(x, y, z, lastFace, dist);
            }

            // Move to next voxel boundary (choose axis with smallest t value)
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                dist = tMaxX;
                x += stepX;
                tMaxX += tDeltaX;
                lastFace = stepX > 0 ? RaycastResult.Face.WEST : RaycastResult.Face.EAST;
            } else if (tMaxY < tMaxZ) {
                dist = tMaxY;
                y += stepY;
                tMaxY += tDeltaY;
                lastFace = stepY > 0 ? RaycastResult.Face.BOTTOM : RaycastResult.Face.TOP;
            } else {
                dist = tMaxZ;
                z += stepZ;
                tMaxZ += tDeltaZ;
                lastFace = stepZ > 0 ? RaycastResult.Face.NORTH : RaycastResult.Face.SOUTH;
            }
        }

        return null;
    }

    /**
     * Calculates the distance along the ray to the next integer boundary.
     * This is used to initialize tMax values in the DDA algorithm.
     *
     * @param s starting position on one axis
     * @param ds direction component on that axis
     * @return distance to next integer boundary, or Float.POSITIVE_INFINITY if ds is 0
     */
    private static float intbound(float s, float ds) {
        if (ds == 0) {
            return Float.POSITIVE_INFINITY;
        }
        if (ds > 0) {
            // Distance to next higher integer
            return ((float) Math.ceil(s) - s) / ds;
        } else {
            // Distance to next lower integer
            return ((float) Math.floor(s) - s) / ds;
        }
    }
}
