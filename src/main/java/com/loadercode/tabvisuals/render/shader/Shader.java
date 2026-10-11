/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.shader;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Shader {
    private final int programId;

    /**
     * Кэш локаций uniform'ов. glGetUniformLocation — строковый поиск имени внутри
     * драйвера, а раньше он шёл на каждый uniform каждый кадр. Локация не
     * меняется всю жизнь программы, поэтому ищем ровно один раз.
     */
    private final Map<String, Integer> uniformLocations = new HashMap<>();

    /** Переиспользуемые буферы матрицы: allocateDirect — off-heap сегмент и syscall. */
    private final float[] matrixArray = new float[16];
    private final FloatBuffer matrixBuffer = ByteBuffer.allocateDirect(16 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();

    public Shader(String folder, String name) {
        String vertexPath = "/assets/tabvisuals/shaders/" + folder + "/" + name + ".vsh";
        String fragmentPath = "/assets/tabvisuals/shaders/" + folder + "/" + name + ".fsh";

        int vertexId = 0;
        int fragmentId = 0;
        int linkedProgramId = 0;
        try {
            vertexId = createShader(vertexPath, GL20.GL_VERTEX_SHADER);
            fragmentId = createShader(fragmentPath, GL20.GL_FRAGMENT_SHADER);

            linkedProgramId = GL20.glCreateProgram();
            GL20.glAttachShader(linkedProgramId, vertexId);
            GL20.glAttachShader(linkedProgramId, fragmentId);
            GL20.glLinkProgram(linkedProgramId);

            if (GL20.glGetProgrami(linkedProgramId, GL20.GL_LINK_STATUS) == 0) {
                throw new RuntimeException("Failed to link shader program '" + folder + "/" + name
                        + "':\n" + GL20.glGetProgramInfoLog(linkedProgramId));
            }

            this.programId = linkedProgramId;
            linkedProgramId = 0;
        } finally {
            if (linkedProgramId != 0) GL20.glDeleteProgram(linkedProgramId);
            if (vertexId != 0) GL20.glDeleteShader(vertexId);
            if (fragmentId != 0) GL20.glDeleteShader(fragmentId);
        }
    }

    private int createShader(String path, int type) {
        final String source;
        try {
            source = readShaderFile(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader: " + path, e);
        }

        int shaderId = GL20.glCreateShader(type);
        boolean compiled = false;
        try {
            GL20.glShaderSource(shaderId, source);
            GL20.glCompileShader(shaderId);

            if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
                throw new RuntimeException("Failed to compile shader: " + path + "\n"
                        + GL20.glGetShaderInfoLog(shaderId));
            }

            compiled = true;
            return shaderId;
        } finally {
            if (!compiled) GL20.glDeleteShader(shaderId);
        }
    }

    private String readShaderFile(String path) throws IOException {
        InputStream stream = Shader.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IOException("Shader file not found: " + path);
        }

        StringBuilder source = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                source.append(line).append("\n");
            }
        }
        return source.toString();
    }

    public void bind() {
        GL20.glUseProgram(this.programId);
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public void setUniform1i(String name, int value) {
        int location = location(name);
        if (location != -1) GL20.glUniform1i(location, value);
    }

    public void setUniform1f(String name, float value) {
        int location = location(name);
        if (location != -1) GL20.glUniform1f(location, value);
    }

    public void setUniform2f(String name, float x, float y) {
        int location = location(name);
        if (location != -1) GL20.glUniform2f(location, x, y);
    }

    public void setUniform2f(String name, Vector2f vec) {
        setUniform2f(name, vec.x, vec.y);
    }

    public void setUniform3f(String name, float x, float y, float z) {
        int location = location(name);
        if (location != -1) GL20.glUniform3f(location, x, y, z);
    }

    public void setUniform3f(String name, Vector3f vec) {
        setUniform3f(name, vec.x, vec.y, vec.z);
    }

    public void setUniformBool(String name, boolean value) {
        setUniform1i(name, value ? 1 : 0);
    }

    public void setUniformMatrix4f(String name, Matrix4f mat) {
        int location = location(name);
        if (location != -1) {
            mat.get(matrixArray);
            matrixBuffer.clear();
            matrixBuffer.put(matrixArray);
            matrixBuffer.rewind();
            GL20.glUniformMatrix4fv(location, false, matrixBuffer);
        }
    }

    /** Локация uniform'а, найденная один раз и запомненная навсегда. */
    private int location(String name) {
        Integer cached = uniformLocations.get(name);
        if (cached != null) return cached;
        int location = GL20.glGetUniformLocation(this.programId, name);
        uniformLocations.put(name, location);
        return location;
    }

    public void delete() {
        uniformLocations.clear();
        GL20.glDeleteProgram(this.programId);
    }
}
