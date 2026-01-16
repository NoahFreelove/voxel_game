package org.lab.engine;

import org.joml.Vector3f;
import org.lab.world.World;

/**
 * Player with physics and collision.
 * Encapsulates position, velocity, and handles gravity/jumping.
 */
public class Player {
    // Physics constants
    private static final float GRAVITY = -20.0f;       // blocks/sec²
    private static final float JUMP_VELOCITY = 8.0f;   // blocks/sec
    private static final float TERMINAL_VELOCITY = -50.0f; // blocks/sec
    private static final float MOVE_SPEED = 5.0f;      // blocks/sec

    // Player dimensions (sized to fit in 1-block gaps)
    private static final float WIDTH = 0.5f;           // Player width in blocks
    private static final float HEIGHT = 0.9f;          // Player height in blocks
    private static final float EYE_HEIGHT = 0.75f;     // Eye height above feet

    // State
    private final Vector3f position;  // Feet position
    private final Vector3f velocity;
    private boolean onGround;

    public Player(float x, float y, float z) {
        this.position = new Vector3f(x, y, z);
        this.velocity = new Vector3f(0, 0, 0);
        this.onGround = false;
    }

    /**
     * Updates player physics: gravity, velocity integration, collision resolution.
     */
    public void update(float deltaTime, World world) {
        // Apply gravity
        velocity.y += GRAVITY * deltaTime;
        if (velocity.y < TERMINAL_VELOCITY) {
            velocity.y = TERMINAL_VELOCITY;
        }

        // Move and collide on each axis separately
        onGround = false;

        // Y axis first (gravity)
        moveAxis(1, velocity.y * deltaTime, world);

        // X axis
        moveAxis(0, velocity.x * deltaTime, world);

        // Z axis
        moveAxis(2, velocity.z * deltaTime, world);

        // Dampen horizontal velocity (friction when on ground)
        if (onGround) {
            velocity.x *= 0.8f;
            velocity.z *= 0.8f;
        }
    }

    /**
     * Moves the player along one axis with collision detection.
     * @param axis 0=X, 1=Y, 2=Z
     * @param delta amount to move
     * @param world world for collision queries
     */
    private void moveAxis(int axis, float delta, World world) {
        if (Math.abs(delta) < 0.0001f) return;

        // Calculate new position
        float newVal = getAxisValue(axis) + delta;
        setAxisValue(axis, newVal);

        // Check for collisions
        float halfWidth = WIDTH / 2.0f;

        // Calculate AABB bounds
        int minX = (int) Math.floor(position.x - halfWidth);
        int maxX = (int) Math.floor(position.x + halfWidth);
        int minY = (int) Math.floor(position.y);
        int maxY = (int) Math.floor(position.y + HEIGHT);
        int minZ = (int) Math.floor(position.z - halfWidth);
        int maxZ = (int) Math.floor(position.z + halfWidth);

        // Check all blocks the player might intersect
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    if (world.hasBlock(bx, by, bz)) {
                        // Block exists, resolve collision
                        resolveCollision(axis, delta, bx, by, bz);
                        return; // Exit after first collision on this axis
                    }
                }
            }
        }
    }

    /**
     * Resolves collision by pushing the player out of the block.
     */
    private void resolveCollision(int axis, float delta, int blockX, int blockY, int blockZ) {
        float halfWidth = WIDTH / 2.0f;
        float epsilon = 0.01f;  // Larger epsilon for more stable collision

        if (axis == 0) { // X
            if (delta > 0) {
                position.x = blockX - halfWidth - epsilon;
            } else {
                position.x = blockX + 1 + halfWidth + epsilon;
            }
            velocity.x = 0;
        } else if (axis == 1) { // Y
            if (delta > 0) {
                position.y = blockY - HEIGHT - epsilon;
            } else {
                position.y = blockY + 1 + epsilon;
                onGround = true;
            }
            velocity.y = 0;
        } else { // Z
            if (delta > 0) {
                position.z = blockZ - halfWidth - epsilon;
            } else {
                position.z = blockZ + 1 + halfWidth + epsilon;
            }
            velocity.z = 0;
        }
    }

    private float getAxisValue(int axis) {
        return switch (axis) {
            case 0 -> position.x;
            case 1 -> position.y;
            case 2 -> position.z;
            default -> 0;
        };
    }

    private void setAxisValue(int axis, float value) {
        switch (axis) {
            case 0 -> position.x = value;
            case 1 -> position.y = value;
            case 2 -> position.z = value;
        }
    }

    /**
     * Returns the eye position (for camera).
     */
    public Vector3f getEyePosition() {
        return new Vector3f(position.x, position.y + EYE_HEIGHT, position.z);
    }

    /**
     * Attempts to jump if on ground.
     */
    public void jump() {
        if (onGround) {
            velocity.y = JUMP_VELOCITY;
            onGround = false;
        }
    }

    /**
     * Adds horizontal movement based on look direction.
     * @param forward forward/backward input (-1 to 1)
     * @param strafe left/right input (-1 to 1)
     * @param yaw camera yaw in degrees
     */
    public void move(float forward, float strafe, float yaw, float deltaTime) {
        float yawRad = (float) Math.toRadians(yaw);

        // Calculate movement direction in world space
        float dx = 0, dz = 0;

        if (forward != 0) {
            dx += (float) Math.cos(yawRad) * forward;
            dz += (float) Math.sin(yawRad) * forward;
        }

        if (strafe != 0) {
            // Strafe is perpendicular to forward
            dx += (float) Math.cos(yawRad + Math.PI / 2) * strafe;
            dz += (float) Math.sin(yawRad + Math.PI / 2) * strafe;
        }

        // Normalize if both inputs active
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len > 0.01f) {
            dx /= len;
            dz /= len;
        }

        // Apply speed
        velocity.x = dx * MOVE_SPEED;
        velocity.z = dz * MOVE_SPEED;
    }

    public Vector3f getPosition() {
        return position;
    }

    public boolean isOnGround() {
        return onGround;
    }

    /**
     * Resets the player to a given position with zero velocity.
     */
    public void reset(float x, float y, float z) {
        position.set(x, y, z);
        velocity.set(0, 0, 0);
        onGround = false;
    }
}
