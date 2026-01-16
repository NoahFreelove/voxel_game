package org.lab.render;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.stb.STBImage.*;

/**
 * 2D Texture Array wrapper (GL_TEXTURE_2D_ARRAY).
 *
 * A texture array is the proper way to handle multiple block textures in OpenGL.
 * Instead of binding multiple textures or using shader branching, all textures
 * are stored in a single array and selected by layer index in the shader.
 *
 * Benefits over multiple sampler2D uniforms:
 * - Single texture bind call
 * - No shader branching (better GPU performance)
 * - Scales to many textures without shader changes
 * - Layer selection via texture coordinate (simple float index)
 *
 * Usage in shader:
 *   uniform sampler2DArray uTextureArray;
 *   vec4 color = texture(uTextureArray, vec3(uv, layerIndex));
 */
public class TextureArray implements AutoCloseable {
    private final int id;
    private final int width;
    private final int height;
    private final int layerCount;

    /**
     * Creates a texture array from multiple image files.
     * All images must have the same dimensions.
     *
     * @param paths paths to texture files in order (index 0, 1, 2, ...)
     */
    public TextureArray(String... paths) {
        if (paths.length == 0) {
            throw new IllegalArgumentException("At least one texture path required");
        }

        this.layerCount = paths.length;

        // Load first image to get dimensions
        ImageData firstImage = loadImage(paths[0]);
        this.width = firstImage.width;
        this.height = firstImage.height;

        // Create texture array
        this.id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, id);

        // Allocate storage for all layers
        // glTexImage3D allocates the full array; we'll fill layers with glTexSubImage3D
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8,
                width, height, layerCount,
                0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        // Upload first layer
        glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0,
                0, 0, 0,  // x, y, z offsets
                width, height, 1,  // width, height, depth (1 layer)
                GL_RGBA, GL_UNSIGNED_BYTE, firstImage.pixels);
        stbi_image_free(firstImage.pixels);
        MemoryUtil.memFree(firstImage.fileBuffer);

        // Load and upload remaining layers
        for (int i = 1; i < paths.length; i++) {
            ImageData img = loadImage(paths[i]);

            ByteBuffer pixelsToUpload = img.pixels;
            ByteBuffer resizedBuffer = null;

            // Resize if dimensions don't match
            if (img.width != width || img.height != height) {
                System.out.println("Resizing " + paths[i] + " from " +
                        img.width + "x" + img.height + " to " + width + "x" + height);

                resizedBuffer = resizeImage(img.pixels, img.width, img.height, width, height);
                pixelsToUpload = resizedBuffer;
            }

            // Upload this layer
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0,
                    0, 0, i,  // x, y, layer index
                    width, height, 1,
                    GL_RGBA, GL_UNSIGNED_BYTE, pixelsToUpload);

            // Clean up
            stbi_image_free(img.pixels);
            MemoryUtil.memFree(img.fileBuffer);
            if (resizedBuffer != null) {
                MemoryUtil.memFree(resizedBuffer);
            }
        }

        // Set filtering - NEAREST for crisp voxel textures
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        // Wrap mode - REPEAT for tiling
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_REPEAT);

        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);

        System.out.println("Created texture array: " + width + "x" + height + " with " + layerCount + " layers");
    }

    /**
     * Loads an image from the classpath.
     */
    private ImageData loadImage(String path) {
        ByteBuffer fileBuffer = loadResourceToBuffer(path);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer widthBuf = stack.mallocInt(1);
            IntBuffer heightBuf = stack.mallocInt(1);
            IntBuffer channelsBuf = stack.mallocInt(1);

            stbi_set_flip_vertically_on_load(true);

            ByteBuffer pixels = stbi_load_from_memory(fileBuffer, widthBuf, heightBuf, channelsBuf, 4);
            if (pixels == null) {
                MemoryUtil.memFree(fileBuffer);
                throw new RuntimeException("Failed to load texture: " + path + "\n" + stbi_failure_reason());
            }

            return new ImageData(pixels, fileBuffer, widthBuf.get(0), heightBuf.get(0));
        }
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
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource: " + path, e);
        }
    }

    /**
     * Binds this texture array to the specified texture unit.
     *
     * @param unit texture unit (0 = GL_TEXTURE0, etc.)
     */
    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D_ARRAY, id);
    }

    /**
     * Binds to texture unit 0.
     */
    public void bind() {
        bind(0);
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

    public int getLayerCount() {
        return layerCount;
    }

    @Override
    public void close() {
        glDeleteTextures(id);
    }

    /**
     * Resizes an RGBA image using bilinear interpolation.
     * This is a simple implementation for educational purposes.
     *
     * @param src source pixel data (RGBA, 4 bytes per pixel)
     * @param srcW source width
     * @param srcH source height
     * @param dstW destination width
     * @param dstH destination height
     * @return newly allocated buffer with resized image
     */
    private ByteBuffer resizeImage(ByteBuffer src, int srcW, int srcH, int dstW, int dstH) {
        ByteBuffer dst = MemoryUtil.memAlloc(dstW * dstH * 4);

        float xRatio = (float) srcW / dstW;
        float yRatio = (float) srcH / dstH;

        for (int y = 0; y < dstH; y++) {
            for (int x = 0; x < dstW; x++) {
                // Map destination pixel to source coordinates
                float srcX = x * xRatio;
                float srcY = y * yRatio;

                // Get the four neighboring pixels
                int x0 = (int) srcX;
                int y0 = (int) srcY;
                int x1 = Math.min(x0 + 1, srcW - 1);
                int y1 = Math.min(y0 + 1, srcH - 1);

                // Calculate interpolation weights
                float xWeight = srcX - x0;
                float yWeight = srcY - y0;

                // Bilinear interpolation for each channel (RGBA)
                for (int c = 0; c < 4; c++) {
                    int p00 = src.get((y0 * srcW + x0) * 4 + c) & 0xFF;
                    int p10 = src.get((y0 * srcW + x1) * 4 + c) & 0xFF;
                    int p01 = src.get((y1 * srcW + x0) * 4 + c) & 0xFF;
                    int p11 = src.get((y1 * srcW + x1) * 4 + c) & 0xFF;

                    // Interpolate
                    float top = p00 + (p10 - p00) * xWeight;
                    float bottom = p01 + (p11 - p01) * xWeight;
                    int value = (int) (top + (bottom - top) * yWeight);

                    dst.put((y * dstW + x) * 4 + c, (byte) value);
                }
            }
        }

        return dst;
    }

    /**
     * Helper class to hold loaded image data.
     */
    private record ImageData(ByteBuffer pixels, ByteBuffer fileBuffer, int width, int height) {}
}
