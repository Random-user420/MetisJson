/*
 Copyright 2025 Lilith

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/
package io.lilithtechs.metisJson;


import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A simple JSON serialization and deserialization library.
 * This class provides static methods to convert Java objects to JSON strings (toJson)
 * and JSON strings to Java objects (fromJson). It does not rely on any external dependencies.
 */
public class JsonUtils {
    private JsonUtils() {}
    /**
     * Converts a Java object into its JSON string representation.
     * Supports primitives, Strings, Numbers, Booleans, Arrays, Collections, Maps, and custom objects.
     *
     * @param value The object to serialize.
     * @return A JSON string representation of the object.
     * @throws IllegalAccessException if a field is inaccessible.
     */
    public static String toJson(Object value) throws IllegalAccessException {
        if (value == null) {
            return "null";
        }

        if (value instanceof String) {
            return "\"" + escapeString((String) value) + "\"";
        }

        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }

        if (value.getClass().isArray()) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                sb.append(toJson(Array.get(value, i)));
                if (i < length - 1) {
                    sb.append(",");
                }
            }
            sb.append("]");
            return sb.toString();
        }

        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(item -> {
                        try {
                            return toJson(item);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.joining(",", "[", "]"));
        }

        if (value instanceof Map<?, ?> map) {
            String mapContent = map.entrySet().stream()
                    .map(entry -> {
                        try {
                            return "\"" + escapeString(String.valueOf(entry.getKey())) + "\":" + toJson(entry.getValue());
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.joining(","));
            return "{" + mapContent + "}";
        }

        Class<?> clazz = value.getClass();
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        Field[] fields = clazz.getDeclaredFields();
        List<String> fieldStrings = new ArrayList<>();

        for (Field field : fields) {
            if (Modifier.isTransient(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Object fieldValue = field.get(value);
            if (fieldValue != null) {
                fieldStrings.add("\"" + field.getName() + "\":" + toJson(fieldValue));
            }
        }
        sb.append(String.join(",", fieldStrings));
        sb.append("}");
        return sb.toString();
    }

    /**
     * Deserializes a JSON string into an object of the specified class.
     *
     * @param json  The JSON string to deserialize.
     * @param clazz The class of the target object.
     * @param <T>   The type of the target object.
     * @return An object of type T.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            String trimmedJson = json.trim();
            if (trimmedJson.equals("null")) {
                return null;
            }
            if (trimmedJson.startsWith("[")) {
                if (clazz.isArray()) {
                    return (T) parseArray(trimmedJson, clazz.getComponentType());
                } else {
                    throw new IllegalArgumentException("Direct deserialization of a List is not supported without type information. Use a container object.");
                }
            }
            if (trimmedJson.startsWith("{")) {
                return parseObject(trimmedJson, clazz);
            }
            if (clazz == String.class) {
                return (T) trimmedJson.substring(1, trimmedJson.length() - 1);
            }
            if (clazz == Integer.class || clazz == int.class) {
                return (T) Integer.valueOf(trimmedJson);
            }
            if (clazz == Long.class || clazz == long.class) {
                return (T) Long.valueOf(trimmedJson);
            }
            if (clazz == Double.class || clazz == double.class) {
                return (T) Double.valueOf(trimmedJson);
            }
            if (clazz == Boolean.class || clazz == boolean.class) {
                return (T) Boolean.valueOf(trimmedJson);
            }

            throw new IllegalArgumentException("Unsupported JSON format or class type: " + trimmedJson);

        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSON", e);
        }
    }

    private static <T> T parseObject(String json, Class<T> clazz) throws Exception {
        T instance = clazz.getDeclaredConstructor().newInstance();
        Map<String, String> jsonMap = parseJsonToMap(json);

        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isTransient(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            String fieldName = field.getName();
            String jsonValue = jsonMap.get(fieldName);

            if (jsonValue != null) {
                Object value = convertJsonValueToFieldType(jsonValue, field);
                field.set(instance, value);
            }
        }
        return instance;
    }

    private static <T> List<T> parseArray(String json, Class<T> itemClazz) throws Exception {
        List<T> list = new ArrayList<>();
        String content = json.substring(1, json.length() - 1).trim();
        if (content.isEmpty()) {
            return list;
        }

        List<String> items = new ArrayList<>();
        int level = 0;
        int lastIndex = 0;
        boolean inString = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (c == '"' && (i == 0 || content.charAt(i - 1) != '\\')) {
                inString = !inString;
            }

            if (!inString) {
                if (c == '{' || c == '[') {
                    level++;
                } else if (c == '}' || c == ']') {
                    level--;
                } else if (c == ',' && level == 0) {
                    items.add(content.substring(lastIndex, i));
                    lastIndex = i + 1;
                }
            }
        }
        items.add(content.substring(lastIndex));

        for (String itemStr : items) {
            if (!itemStr.trim().isEmpty()) {
                list.add(fromJson(itemStr.trim(), itemClazz));
            }
        }
        return list;
    }


    private static Object convertJsonValueToFieldType(String jsonValue, Field field) {
        Class<?> fieldType = field.getType();

        if (Collection.class.isAssignableFrom(fieldType)) {
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType pt) {
                Class<?> itemType = (Class<?>) pt.getActualTypeArguments()[0];
                try {
                    return parseArray(jsonValue, itemType);
                } catch(Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return fromJson(jsonValue, fieldType);
    }

    private static Map<String, String> parseJsonToMap(String json) {
        Map<String, String> map = new HashMap<>();
        String content = json.substring(1, json.length() - 1).trim();

        int level = 0;
        int lastIndex = 0;
        boolean inString = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (c == '"' && (i == 0 || content.charAt(i - 1) != '\\')) {
                inString = !inString;
            }

            if (!inString) {
                if (c == '{' || c == '[') {
                    level++;
                } else if (c == '}' || c == ']') {
                    level--;
                } else if (c == ',' && level == 0) {
                    String pair = content.substring(lastIndex, i);
                    addKeyValuePairToMap(pair, map);
                    lastIndex = i + 1;
                }
            }
        }
        String lastPair = content.substring(lastIndex);
        addKeyValuePairToMap(lastPair, map);

        return map;
    }

    private static void addKeyValuePairToMap(String pair, Map<String, String> map) {
        String[] keyValue = pair.split(":", 2);
        if (keyValue.length == 2) {
            String key = keyValue[0].trim().replaceAll("\"", "");
            String value = keyValue[1].trim();
            map.put(key, value);
        }
    }


    private static String escapeString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
