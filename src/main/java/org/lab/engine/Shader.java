package org.lab.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Shader program wrapper.
 * Handles compilation, linking, and uniform setting for GLSL shaders.
 */
public class Shader implements AutoCloseable {
    private final int programId;
    private final Map<String, Integer> uniformCache = new HashMap<>();

    /**
     * Creates a shader program from vertex and fragment shader source files.
     * Files are loaded from the classpath (resources folder).
     *
     * @param vertexPath   path to vertex shader (e.g., "shaders/block.vert")
     * @param fragmentPath path to fragment shader (e.g., "shaders/block.frag")
     */
    public Shader(String vertexPath, String fragmentPath) {
        String vertexSource = loadResource(vertexPath);
        String fragmentSource = loadResource(fragmentPath);

        int vertexShader = compileShader(vertexSource, GL_VERTEX_SHADER);
        int fragmentShader = compileShader(fragmentSource, GL_FRAGMENT_SHADER);

        programId = glCreateProgram();
        glAttachShader(programId, vertexShader);
        glAttachShader(programId, fragmentShader);
        glLinkProgram(programId);

        // Check for linking errors
        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(programId);
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
            glDeleteProgram(programId);
            throw new RuntimeException("Shader program linking failed:\n" + log);
        }

        // Shaders can be deleted after linking
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
    }

    /**
     * Compiles a shader from source code.
     */
    private int compileShader(String source, int type) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            String typeName = (type == GL_VERTEX_SHADER) ? "vertex" : "fragment";
            glDeleteShader(shader);
            throw new RuntimeException(typeName + " shader compilation failed:\n" + log);
        }

        return shader;
    }

    /**
     * Loads a text file from the classpath.
     */
    private String loadResource(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource: " + path, e);
        }
    }

    /**
     * Activates this shader program.
     */
    public void use() {
        glUseProgram(programId);
    }

    /**
     * Gets the location of a uniform, caching the result.
     */
    private int getUniformLocation(String name) {
        return uniformCache.computeIfAbsent(name, n -> {
            int location = glGetUniformLocation(programId, n);
            if (location == -1) {
                System.err.println("Warning: uniform '" + n + "' not found in shader");
            }
            return location;
        });
    }

    /**
     * Sets a mat4 uniform.
     */
    public void setMatrix4f(String name, Matrix4f matrix) {
        int location = getUniformLocation(name);
        if (location != -1) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer buffer = stack.mallocFloat(16);
                matrix.get(buffer);
                glUniformMatrix4fv(location, false, buffer);
            }
        }
    }

    /**
     * Sets a vec3 uniform.
     */
    public void setVector3f(String name, Vector3f vector) {
        int location = getUniformLocation(name);
        if (location != -1) {
            glUniform3f(location, vector.x, vector.y, vector.z);
        }
    }

    /**
     * Sets a vec3 uniform from individual components.
     */
    public void setVector3f(String name, float x, float y, float z) {
        int location = getUniformLocation(name);
        if (location != -1) {
            glUniform3f(location, x, y, z);
        }
    }

    /**
     * Sets a float uniform.
     */
    public void setFloat(String name, float value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            glUniform1f(location, value);
        }
    }

    /**
     * Sets an int uniform (used for sampler bindings).
     */
    public void setInt(String name, int value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            glUniform1i(location, value);
        }
    }

    public int getProgramId() {
        return programId;
    }

    @Override
    public void close() {
        glDeleteProgram(programId);
    }
}
