package org.lab.render;

import org.lab.engine.Shader;

/**
 * Block texture atlas manager.
 * Wraps a Texture and provides UV coordinate calculations for tiles.
 *
 * Atlas layout: tiles arranged in a grid, indexed left-to-right, top-to-bottom.
 * Index 0 is top-left, index (tilesPerRow-1) is top-right, etc.
 */
public class TextureAtlas implements AutoCloseable {
    private final Texture texture;
    private final int tilesPerRow;
    private final float tileSize; // UV size of one tile (0.0 to 1.0)

    /**
     * Creates a texture atlas.
     *
     * @param path        path to atlas image (e.g., "textures/atlas.png")
     * @param tilesPerRow number of tiles per row in the atlas
     */
    public TextureAtlas(String path, int tilesPerRow) {
        this.texture = new Texture(path);
        this.tilesPerRow = tilesPerRow;
        this.tileSize = 1.0f / tilesPerRow;
    }

    /**
     * Binds the atlas texture to the specified unit.
     */
    public void bind(int unit) {
        texture.bind(unit);
    }

    /**
     * Binds the atlas texture to unit 0.
     */
    public void bind() {
        bind(0);
    }

    /**
     * Sets atlas-related uniforms on a shader.
     * Call after shader.use() and bind().
     */
    public void setUniforms(Shader shader) {
        shader.setInt("uTexture", 0);
        shader.setFloat("uAtlasTileSize", tileSize);
        shader.setInt("uAtlasTilesPerRow", tilesPerRow);
    }

    /**
     * Returns the U coordinate for a tile index.
     */
    public float getU(int tileIndex) {
        return (tileIndex % tilesPerRow) * tileSize;
    }

    /**
     * Returns the V coordinate for a tile index.
     * V is flipped because texture origin is bottom-left in OpenGL.
     */
    public float getV(int tileIndex) {
        return 1.0f - ((tileIndex / tilesPerRow) + 1) * tileSize;
    }

    public int getTilesPerRow() {
        return tilesPerRow;
    }

    public float getTileSize() {
        return tileSize;
    }

    public int getWidth() {
        return texture.getWidth();
    }

    public int getHeight() {
        return texture.getHeight();
    }

    @Override
    public void close() {
        texture.close();
    }
}
