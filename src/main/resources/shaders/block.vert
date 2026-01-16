#version 330 core

// Per-vertex attributes (from CubeMesh)
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoord;
layout (location = 2) in vec3 aNormal;

// Per-instance attributes (from BlockRenderer)
layout (location = 3) in vec3 iPosition;
layout (location = 4) in vec3 iRotation;  // Euler angles (radians)
layout (location = 5) in float iScale;
layout (location = 6) in float iTexIndex;
layout (location = 7) in float iHighlight;

// Uniforms
uniform mat4 uViewProjection;

// Outputs to fragment shader
out vec2 vTexCoord;
out vec3 vNormal;
out float vTexIndex;
out float vHighlight;

// Builds a rotation matrix from Euler angles (Y * X * Z order)
mat4 rotationMatrix(vec3 rot) {
    float cx = cos(rot.x);
    float sx = sin(rot.x);
    float cy = cos(rot.y);
    float sy = sin(rot.y);
    float cz = cos(rot.z);
    float sz = sin(rot.z);

    // Combined Y * X * Z rotation
    return mat4(
        cy*cz + sy*sx*sz,   cx*sz,  -sy*cz + cy*sx*sz,  0.0,
        -cy*sz + sy*sx*cz,  cx*cz,  sy*sz + cy*sx*cz,   0.0,
        sy*cx,              -sx,    cy*cx,              0.0,
        0.0,                0.0,    0.0,                1.0
    );
}

void main() {
    // Build model matrix from instance data: T * R * S
    mat4 rotation = rotationMatrix(iRotation);
    mat4 model = mat4(1.0);

    // Translation
    model[3] = vec4(iPosition, 1.0);

    // Apply rotation
    model = model * rotation;

    // Apply scale (uniform)
    mat4 scale = mat4(iScale);
    scale[3][3] = 1.0;
    model = model * scale;

    // Transform position
    vec4 worldPos = model * vec4(aPos, 1.0);
    gl_Position = uViewProjection * worldPos;

    // Transform normal (rotation only, no translation/scale)
    vNormal = mat3(rotation) * aNormal;

    // Pass through texture coordinates, index, and highlight
    vTexCoord = aTexCoord;
    vTexIndex = iTexIndex;
    vHighlight = iHighlight;
}
