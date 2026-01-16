#version 330 core

in vec2 vTexCoord;
in vec3 vNormal;
in float vTexIndex;
in float vHighlight;

// Texture array: all block textures in one array, selected by layer index
// This is the proper OpenGL approach - no branching, single bind, scalable
uniform sampler2DArray uTextureArray;

out vec4 FragColor;

void main() {
    // Sample from texture array using (u, v, layer) coordinates
    // The layer index selects which texture in the array to use
    vec4 texColor = texture(uTextureArray, vec3(vTexCoord, vTexIndex));

    // Simple face-based shading (no normalize, no dot product)
    float shade = 0.7 + abs(vNormal.y) * 0.3;
    vec3 finalColor = texColor.rgb * shade;

    // Highlight
    if (vHighlight > 0.5) {
        finalColor = mix(finalColor, vec3(1.0), 0.25);
    }

    FragColor = vec4(finalColor, 1.0);
}
