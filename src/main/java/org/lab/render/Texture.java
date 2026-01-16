package org.lab.render;

import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.stb.STBImage.*;

/**
 * 2D texture wrapper.
 * Uses STB for image loading with NEAREST filtering for pixel art.
 */
public class Texture implements AutoCloseable {
    private final int id;
    private final int width;
    private final int height;

    /**
     * Loads a texture from the classpath.
     *
     * @param path path to the image file (e.g., "textures/atlas.png")
     */
    public Texture(String path) {
        // Load image data from classpath into a ByteBuffer
        ByteBuffer imageData = loadResourceToBuffer(path);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer widthBuf = stack.mallocInt(1);
            IntBuffer heightBuf = stack.mallocInt(1);
            IntBuffer channelsBuf = stack.mallocInt(1);

            // STB needs the image data flipped for OpenGL
            stbi_set_flip_vertically_on_load(true);

            ByteBuffer pixels = stbi_load_from_memory(imageData, widthBuf, heightBuf, channelsBuf, 4);
            if (pixels == null) {
                throw new RuntimeException("Failed to load texture: " + path + "\n" + stbi_failure_reason());
            }

            this.width = widthBuf.get(0);
            this.height = heightBuf.get(0);

            // Create and configure texture
            this.id = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, id);

            // NEAREST filtering for crisp pixel art (Minecraft-style)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

            // Clamp to edge to prevent texture bleeding
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            // Upload to GPU
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

            // Free STB buffer
            stbi_image_free(pixels);
        }

        // Free the loaded file buffer
        org.lwjgl.system.MemoryUtil.memFree(imageData);
    }

    /**
     * Loads a resource file into a direct ByteBuffer.
     */
    private ByteBuffer loadResourceToBuffer(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Resource not found: " + path);
            }
            byte[] bytes = is.readAllBytes();
            ByteBuffer buffer = org.lwjgl.system.MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource: " + path, e);
        }
    }

    /**
     * Binds this texture to the specified texture unit.
     *
     * @param unit texture unit (0 = GL_TEXTURE0, 1 = GL_TEXTURE1, etc.)
     */
    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, id);
    }

    /**
     * Binds this texture to unit 0.
     */
    public void bind() {
        bind(0);
    }

    /**
     * Unbinds any texture from the currently active unit.
     */
    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public int getId() {
        return id;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public void close() {
        glDeleteTextures(id);
    }
}
