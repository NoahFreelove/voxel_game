#version 330 core

in vec2 vTexCoord;
in vec3 vNormal;
in float vTexIndex;
in float vHighlight;

uniform sampler2D uTexture;
uniform float uAtlasTileSize;
uniform int uAtlasTilesPerRow;

out vec4 FragColor;

void main() {
    // Atlas UV calculation
    int tileX = int(vTexIndex) % uAtlasTilesPerRow;
    int tileY = int(vTexIndex) / uAtlasTilesPerRow;
    vec2 tileOffset = vec2(float(tileX) * uAtlasTileSize,
                           1.0 - (float(tileY) + 1.0) * uAtlasTileSize);
    vec2 atlasUV = tileOffset + vTexCoord * uAtlasTileSize;

    vec4 texColor = texture(uTexture, atlasUV);

    // Simple face-based shading (no normalize, no dot product)
    float shade = 0.7 + abs(vNormal.y) * 0.3;
    vec3 finalColor = texColor.rgb * shade;

    // Highlight
    if (vHighlight > 0.5) {
        finalColor = mix(finalColor, vec3(1.0), 0.25);
    }

    FragColor = vec4(finalColor, 1.0);
}
