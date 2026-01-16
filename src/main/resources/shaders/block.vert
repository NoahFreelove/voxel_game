#version 330 core

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoord;
layout (location = 2) in vec3 aNormal;
layout (location = 3) in vec3 iPosition;
layout (location = 4) in vec3 iRotation;   // Kept for compatibility, unused
layout (location = 5) in float iScale;
layout (location = 6) in float iTexIndex;
layout (location = 7) in float iHighlight;

uniform mat4 uViewProjection;

out vec2 vTexCoord;
out vec3 vNormal;
out float vTexIndex;
out float vHighlight;

void main() {
    // Simple transform: position + scale only (no rotation)
    vec3 worldPos = aPos * iScale + iPosition;
    gl_Position = uViewProjection * vec4(worldPos, 1.0);

    vNormal = aNormal;  // No rotation transform needed
    vTexCoord = aTexCoord;
    vTexIndex = iTexIndex;
    vHighlight = iHighlight;
}
