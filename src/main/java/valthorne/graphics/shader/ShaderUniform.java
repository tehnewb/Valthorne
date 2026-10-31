package valthorne.graphics.shader;

import valthorne.utility.properties.PropertyValue;

import java.util.Locale;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.opengl.GL40.*;
import static org.lwjgl.opengl.GL42.*;

/**
 * One immutable, validated uniform default. Numeric arrays are created during
 * asset loading and never exposed. Applying occurs once when a program is
 * constructed or explicitly configured again, never automatically during drawing.
 */
final class ShaderUniform {
    private final String name; // GLSL uniform name, including optional array/struct selectors.
    private final String reflectionName; // Array-zero name used by active-uniform reflection.
    private final String kind; // Validated config type tag.
    private final float[] floats; // Immutable float components; empty for integer uniforms.
    private final int[] integers; // Immutable int components; empty for floating uniforms.

    /**
     * Parses a uniform.type.name property into finite, fixed-size components.
     * @param key full configuration key
     * @param value scalar or comma-separated vector/matrix value
     */
    ShaderUniform(String key, PropertyValue<?> value) {
        String tail = key.substring("uniform.".length());
        int separator = tail.indexOf('.');
        if (separator < 1 || separator == tail.length() - 1) throw new IllegalArgumentException("Expected uniform.type.name: " + key);
        kind = tail.substring(0, separator).toLowerCase(Locale.ROOT);
        name = tail.substring(separator + 1);
        if (name.isBlank() || name.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid uniform name: " + key);
        reflectionName = arrayBase(name);
        int count = switch (kind) {
            case "int", "bool", "float" -> 1;
            case "vec2", "ivec2" -> 2;
            case "vec3" -> 3;
            case "vec4" -> 4;
            case "mat4" -> 16;
            default -> throw new IllegalArgumentException("Unknown uniform type: " + kind);
        };
        boolean integral = kind.equals("int") || kind.equals("bool") || kind.equals("ivec2");
        floats = new float[integral ? 0 : count];
        integers = new int[integral ? count : 0];
        if (kind.equals("bool")) {
            integers[0] = value.asBoolean() ? 1 : 0;
            return;
        }
        String text = value.asString();
        int start = 0;
        for (int i = 0; i < count; i++) {
            int end = text.indexOf(',', start);
            if ((i < count - 1 && end < 0) || (i == count - 1 && end >= 0))
                throw new IllegalArgumentException(key + " requires " + count + " components");
            if (end < 0) end = text.length();
            String component = text.substring(start, end).trim();
            if (integral) {
                double number = Double.parseDouble(component);
                if (!Double.isFinite(number) || number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
                    throw new IllegalArgumentException("Invalid integer uniform: " + key);
                integers[i] = (int) number;
            } else {
                floats[i] = Float.parseFloat(component);
                if (!Float.isFinite(floats[i])) throw new IllegalArgumentException("Nonfinite uniform: " + key);
            }
            start = end + 1;
        }
    }

    /**
     * Returns the GLSL target so duplicate defaults can be rejected.
     * @return uniform name
     */
    String name() {
        return name;
    }

    /**
     * Uses array element zero for reflection while retaining the requested element
     * for location lookup. Active-uniform indices describe the complete array;
     * nonzero elements can have a valid location but no separate uniform index.
     * @param name requested GLSL name
     * @return reflection name, reusing the input when no selectors exist
     */
    private static String arrayBase(String name) {
        if (name.indexOf('[') < 0) return name;
        StringBuilder base = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c != '[') base.append(c);
            else {
                int end = name.indexOf(']', i + 1);
                if (end < 0) throw new IllegalArgumentException("Unclosed uniform array selector: " + name);
                base.append("[0]");
                i = end;
            }
        }
        return base.toString();
    }

    /**
     * Identifies sampler types whose integer default selects a texture unit.
     * @param type reflected GLSL type
     * @return whether glUniform1i accepts this sampler type
     */
    private static boolean sampler(int type) {
        return switch (type) {
            case GL_SAMPLER_1D, GL_SAMPLER_2D, GL_SAMPLER_3D, GL_SAMPLER_CUBE,
                 GL_SAMPLER_1D_SHADOW, GL_SAMPLER_2D_SHADOW, GL_SAMPLER_2D_RECT, GL_SAMPLER_2D_RECT_SHADOW,
                 GL_SAMPLER_1D_ARRAY, GL_SAMPLER_2D_ARRAY, GL_SAMPLER_BUFFER, GL_SAMPLER_1D_ARRAY_SHADOW,
                 GL_SAMPLER_2D_ARRAY_SHADOW, GL_SAMPLER_CUBE_SHADOW, GL_INT_SAMPLER_1D, GL_INT_SAMPLER_2D,
                 GL_INT_SAMPLER_3D, GL_INT_SAMPLER_CUBE, GL_INT_SAMPLER_2D_RECT, GL_INT_SAMPLER_1D_ARRAY,
                 GL_INT_SAMPLER_2D_ARRAY, GL_INT_SAMPLER_BUFFER, GL_UNSIGNED_INT_SAMPLER_1D,
                 GL_UNSIGNED_INT_SAMPLER_2D, GL_UNSIGNED_INT_SAMPLER_3D, GL_UNSIGNED_INT_SAMPLER_CUBE,
                 GL_UNSIGNED_INT_SAMPLER_2D_RECT, GL_UNSIGNED_INT_SAMPLER_1D_ARRAY,
                 GL_UNSIGNED_INT_SAMPLER_2D_ARRAY, GL_UNSIGNED_INT_SAMPLER_BUFFER,
                 GL_SAMPLER_2D_MULTISAMPLE, GL_INT_SAMPLER_2D_MULTISAMPLE, GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE,
                 GL_SAMPLER_2D_MULTISAMPLE_ARRAY, GL_INT_SAMPLER_2D_MULTISAMPLE_ARRAY,
                 GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE_ARRAY, GL_SAMPLER_CUBE_MAP_ARRAY,
                 GL_SAMPLER_CUBE_MAP_ARRAY_SHADOW, GL_INT_SAMPLER_CUBE_MAP_ARRAY, GL_UNSIGNED_INT_SAMPLER_CUBE_MAP_ARRAY -> true;
            default -> false;
        };
    }

    /**
     * Validates reflected type and applies a default to the currently bound program.
     * Missing/optimized-out locations are ignored unless strict mode is enabled.
     * @param program bound program
     * @param strict whether missing uniforms must fail
     */
    void apply(int program, boolean strict) {
        int location = glGetUniformLocation(program, name);
        if (location < 0) {
            if (strict) throw new IllegalArgumentException("Missing active uniform: " + name);
            return;
        }
        int index = glGetUniformIndices(program, reflectionName);
        if (index == GL_INVALID_INDEX) throw new IllegalArgumentException("Cannot reflect active uniform: " + name);
        int type = glGetActiveUniformsi(program, index, GL_UNIFORM_TYPE);
        boolean textureUnit = sampler(type);
        boolean imageUnit = type >= GL_IMAGE_1D && type <= GL_UNSIGNED_INT_IMAGE_2D_MULTISAMPLE_ARRAY;
        int expected = switch (kind) {
            case "float" -> GL_FLOAT;
            case "vec2" -> GL_FLOAT_VEC2;
            case "vec3" -> GL_FLOAT_VEC3;
            case "vec4" -> GL_FLOAT_VEC4;
            case "mat4" -> GL_FLOAT_MAT4;
            case "ivec2" -> GL_INT_VEC2;
            case "bool" -> GL_BOOL;
            default -> GL_INT;
        };
        if (type != expected && !(kind.equals("int") && (type == GL_BOOL || textureUnit || imageUnit)))
            throw new IllegalArgumentException("Uniform type mismatch for " + name + ": " + kind);
        if (kind.equals("int") && (textureUnit || imageUnit)) {
            int maximum = glGetInteger(imageUnit ? GL_MAX_IMAGE_UNITS : GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
            if (integers[0] < 0 || integers[0] >= maximum) throw new IllegalArgumentException("Uniform unit index out of range: " + name);
        }
        switch (kind) {
            case "int", "bool" -> glUniform1i(location, integers[0]);
            case "ivec2" -> glUniform2i(location, integers[0], integers[1]);
            case "float" -> glUniform1f(location, floats[0]);
            case "vec2" -> glUniform2f(location, floats[0], floats[1]);
            case "vec3" -> glUniform3f(location, floats[0], floats[1], floats[2]);
            case "vec4" -> glUniform4f(location, floats[0], floats[1], floats[2], floats[3]);
            case "mat4" -> glUniformMatrix4fv(location, false, floats);
        }
    }
}
