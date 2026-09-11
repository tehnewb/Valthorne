package valthorne.utility;

import java.util.Objects;

/**
 * Provides JavaBean-style accessor names and exact-signature method discovery.
 * Name generation does not verify that an accessor exists or invoke it. Discovery
 * includes methods declared directly on a class and public inherited methods, without
 * changing accessibility or performing argument conversion.
 *
 * @author Albert Beaupre
 */
public class ReflectionUtility {

    /**
     * Converts a field name to its corresponding setter method name.
     * For example, "fieldName" becomes "setFieldName".
     *
     * @param fieldName The name of the field.
     * @return The setter method name.
     * @throws IllegalArgumentException if fieldName is null or empty.
     */
    public static String toSetterName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            throw new IllegalArgumentException("Field name cannot be null or empty");
        }
        return "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }

    /**
     * Converts a field name to its corresponding getter method name.
     * For boolean fields, returns "isFieldName"; for others, returns "getFieldName".
     *
     * @param field The field to generate the getter name for.
     * @return The getter method name.
     * @throws IllegalArgumentException if field is null.
     */
    public static String toGetterName(java.lang.reflect.Field field) {
        if (field == null) {
            throw new IllegalArgumentException("Field cannot be null");
        }
        String fieldName = field.getName();
        Class<?> type = field.getType();
        String prefix = (type == boolean.class || type == Boolean.class) ? "is" : "get";
        return prefix + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }

    /**
     * Checks for an exact method signature, first among declared methods of any
     * visibility and then among public inherited methods. Parameter types must match
     * the declaration exactly; boxing and assignable argument types are not resolved.
     * A security failure in the initial lookup is reported to standard error and
     * returns false; a security failure during the inherited lookup can propagate.
     *
     * @param clazz          the class to inspect
     * @param methodName     the name of the method
     * @param parameterTypes the parameter types of the method
     * @return true if the method exists, false otherwise
     * @throws NullPointerException if the class or method name is null
     */
    public static boolean hasMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        Objects.requireNonNull(clazz, "Class cannot be null");
        Objects.requireNonNull(methodName, "Method name cannot be null");

        try {
            clazz.getDeclaredMethod(methodName, parameterTypes);
            return true;
        } catch (NoSuchMethodException e) {
            // Check inherited methods
            try {
                clazz.getMethod(methodName, parameterTypes);
                return true;
            } catch (NoSuchMethodException ignored) {
                return false;
            }
        } catch (SecurityException e) {
            System.err.println("Security restriction accessing method " + methodName + " in " + clazz.getName());
            return false;
        }
    }

}
