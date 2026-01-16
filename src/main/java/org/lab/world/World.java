package org.lab.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple world container with block lookup.
 * Uses a HashMap for O(1) block collision queries.
 */
public class World {
    private final Map<Long, Block> blockMap = new HashMap<>();
    private final List<Block> blocks = new ArrayList<>();
    private final Map<Long, Integer> heightMap = new HashMap<>();

    /**
     * Adds a block to the world.
     */
    public void addBlock(Block block) {
        blocks.add(block);
        int x = (int) Math.floor(block.getPosition().x);
        int y = (int) Math.floor(block.getPosition().y);
        int z = (int) Math.floor(block.getPosition().z);
        blockMap.put(packCoord(x, y, z), block);

        // Update heightmap
        long xzKey = packXZ(x, z);
        Integer currentHeight = heightMap.get(xzKey);
        if (currentHeight == null || y > currentHeight) {
            heightMap.put(xzKey, y);
        }
    }

    /**
     * Removes a block from the world.
     * Returns the removed block or null if no block existed.
     */
    public Block removeBlock(int x, int y, int z) {
        Block block = blockMap.remove(packCoord(x, y, z));
        if (block != null) {
            blocks.remove(block);
            // Recalculate heightmap if top block was removed
            long xzKey = packXZ(x, z);
            Integer currentHeight = heightMap.get(xzKey);
            if (currentHeight != null && currentHeight == y) {
                // Find new highest block in this column
                int newHeight = Integer.MIN_VALUE;
                for (int checkY = y - 1; checkY >= -64; checkY--) {
                    if (hasBlock(x, checkY, z)) {
                        newHeight = checkY;
                        break;
                    }
                }
                if (newHeight == Integer.MIN_VALUE) {
                    heightMap.remove(xzKey);
                } else {
                    heightMap.put(xzKey, newHeight);
                }
            }
        }
        return block;
    }

    /**
     * Gets a block at the specified integer coordinates.
     * Returns null if no block exists.
     */
    public Block getBlock(int x, int y, int z) {
        return blockMap.get(packCoord(x, y, z));
    }

    /**
     * Checks if a block exists at the specified integer coordinates.
     */
    public boolean hasBlock(int x, int y, int z) {
        return blockMap.containsKey(packCoord(x, y, z));
    }

    /**
     * Returns all blocks for rendering.
     */
    public List<Block> getBlocks() {
        return blocks;
    }

    /**
     * Packs three integer coordinates into a single long key.
     * Supports coordinates in range [-2^20, 2^20-1] for each axis.
     */
    public static long packCoord(int x, int y, int z) {
        // Use 21 bits per coordinate (supports ~±1M blocks per axis)
        return ((long) (x & 0x1FFFFF)) |
               ((long) (y & 0x1FFFFF) << 21) |
               ((long) (z & 0x1FFFFF) << 42);
    }

    /**
     * Packs X and Z coordinates into a single long key for heightmap lookup.
     */
    public static long packXZ(int x, int z) {
        return ((long) (x & 0x1FFFFF)) | ((long) (z & 0x1FFFFF) << 21);
    }

    /**
     * Gets the height of the tallest block at the given X,Z position.
     * Returns Integer.MIN_VALUE if no blocks exist in that column.
     */
    public int getHeightAt(int x, int z) {
        Integer h = heightMap.get(packXZ(x, z));
        return h != null ? h : Integer.MIN_VALUE;
    }
}
