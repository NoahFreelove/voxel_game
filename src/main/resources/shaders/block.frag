#version 330 core

// Inputs from vertex shader
in vec2 vTexCoord;
in vec3 vNormal;
in float vTexIndex;
in float vHighlight;

// Uniforms
uniform sampler2D uTexture;
uniform float uAtlasTileSize;    // UV size of one tile (e.g., 0.0625 for 16x16 atlas)
uniform int uAtlasTilesPerRow;   // Tiles per row (e.g., 16)
uniform vec3 uLightDir;          // Directional light direction (normalized)
uniform vec3 uAmbientColor;      // Ambient light color/intensity

// Output
out vec4 FragColor;

void main() {
    // Calculate tile position in atlas from texture index
    int tileX = int(vTexIndex) % uAtlasTilesPerRow;
    int tileY = int(vTexIndex) / uAtlasTilesPerRow;

    // Calculate UV offset for this tile
    // Note: V is inverted because OpenGL texture origin is bottom-left
    vec2 tileOffset = vec2(
        float(tileX) * uAtlasTileSize,
        1.0 - (float(tileY) + 1.0) * uAtlasTileSize
    );

    // Scale and offset local UV to atlas UV
    vec2 atlasUV = tileOffset + vTexCoord * uAtlasTileSize;

    // Sample texture
    vec4 texColor = texture(uTexture, atlasUV);

    // Discard fully transparent pixels (for transparency support)
    if (texColor.a < 0.1) {
        discard;
    }

    // Basic directional lighting
    vec3 normal = normalize(vNormal);
    float diff = max(dot(normal, -uLightDir), 0.0);

    // Combine ambient and diffuse lighting
    vec3 lighting = uAmbientColor + vec3(diff);

    // Apply lighting to texture color
    vec3 finalColor = texColor.rgb * lighting;

    // Apply highlight effect (25% brighter when highlighted)
    if (vHighlight > 0.5) {
        finalColor = mix(finalColor, vec3(1.0), 0.25);
    }

    FragColor = vec4(finalColor, texColor.a);
}
