# Voxel Game Project

## Project Overview

Educational voxel game for teaching OpenGL and 3D graphics concepts. Minecraft alpha-inspired tech demo featuring:
- Simple procedural terrain generation
- Block break/place mechanics (grass blocks only)
- First-person camera controls

**Target audience**: Students learning game development and OpenGL programming.

## Tech Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| Java | 21 | Language |
| LWJGL | 3.3.6 | OpenGL/GLFW bindings |
| JOML | 1.10.8 | Matrix/vector math |
| Maven | - | Build system |
| OpenGL | 3.3 core | Rendering API |

**Key LWJGL modules used**:
- `lwjgl-glfw` - Window creation and input handling
- `lwjgl-opengl` - OpenGL bindings
- `lwjgl-stb` - Image loading for textures

## Project Structure

```
src/main/java/
  ├── Main.java              # Entry point, game loop
  ├── engine/                # Core engine components
  │   ├── Window.java        # GLFW window wrapper
  │   ├── Input.java         # Keyboard/mouse handling
  │   ├── Shader.java        # GLSL shader loading/compilation
  │   └── Camera.java        # First-person camera
  ├── world/                 # World representation
  │   ├── World.java         # Manages chunks
  │   ├── Chunk.java         # 16x16x16 block container
  │   └── Block.java         # Block types enum
  ├── render/                # Rendering systems
  │   ├── Mesh.java          # VAO/VBO wrapper
  │   ├── ChunkRenderer.java # Chunk mesh generation
  │   └── TextureAtlas.java  # Block texture atlas
  └── util/                  # Utilities
      └── NoiseGenerator.java # Perlin/Simplex noise

src/main/resources/
  ├── shaders/
  │   ├── block.vert         # Vertex shader
  │   └── block.frag         # Fragment shader
  └── textures/
      └── atlas.png          # Block texture atlas
```

## Key Voxel Concepts

### Chunk System
- World divided into 16x16x16 chunks
- Block data stored as `byte[]` array (256 possible block types)
- Only active chunks around player are loaded

### Mesh Generation
- Simple meshing: one quad per visible block face
- Face culling: don't generate faces between adjacent solid blocks
- Greedy meshing (optional optimization): merge adjacent same-type faces

### Terrain Generation
- Perlin/Simplex noise for height map
- Simple threshold for grass placement
- Flat bedrock layer at y=0

## Build & Run

### From command line
```bash
./mvnw compile exec:java
```

### macOS requirement
GLFW requires the main thread on macOS. Use `exec:exec@macos` instead:
```bash
./mvnw compile exec:exec@macos
```

### From IDE
Run `org.lab.Main` with `-XstartOnFirstThread` in VM options (macOS only).

## Development Guidelines

### Code Style
- Standard Java naming conventions (camelCase methods, PascalCase classes)
- Keep code readable for students learning OpenGL
- Comment non-obvious OpenGL calls with explanations
- Prefer clarity over micro-optimization

### OpenGL Practices
- Always use OpenGL 3.3 core profile (no deprecated functions)
- Check for GL errors during development with `glGetError()`
- Clean up resources (delete VAOs, VBOs, shaders, textures)
- Use JOML for all matrix/vector operations

### Educational Focus
- Each class should demonstrate one concept clearly
- Avoid premature optimization
- Include comments explaining "why" not just "what"

## Current State

- Fresh project setup with Maven wrapper (`./mvnw`)
- Empty `org.lab.Main` with stub main method
- Essential LWJGL dependencies: core, GLFW, OpenGL, STB
- JOML for math operations
- Platform natives configured for Linux, macOS (Intel/ARM), and Windows

## Next Steps

1. Create window with GLFW
2. Set up OpenGL context (3.3 core profile)
3. Implement basic game loop
4. Add shader loading and compilation
5. Create camera with WASD + mouse look
6. Implement chunk data structure
7. Generate simple mesh from chunk data
8. Add Perlin noise terrain generation
9. Implement block breaking/placing with raycasting
