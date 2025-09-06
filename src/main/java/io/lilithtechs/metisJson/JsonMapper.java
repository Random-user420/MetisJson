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

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JsonMapper {
    /**
     * Converts a Java object into its JSON string representation.
     *
     * @param value The object to serialize.
     * @return A JSON string representation of the object.
     */
    public String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        try {
            toJson(value, sb);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to serialize object to JSON", e);
        }
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
    public <T> T fromJson(String json, Class<T> clazz) {
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

    /**
     * Clears the internal cache.
     */
    public void clearCache()
    {
        classInfoCache.clear();
    }

    // Cache to store reflection data for classes. Thread-safe.
    private final Map<Class<?>, ClassInfo> classInfoCache = new ConcurrentHashMap<>();

    private record ClassInfo(List<Field> fields) {
    }

    private ClassInfo getClassInfo(Class<?> clazz) {
        // computeIfAbsent ensures this is done atomically and only once per class.
        return classInfoCache.computeIfAbsent(clazz, c -> {
            List<Field> serializableFields = new ArrayList<>();
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isTransient(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                serializableFields.add(field);
            }
            return new ClassInfo(serializableFields);
        });
    }

    private <T> List<T> parseArray(String json, Class<T> itemClazz) throws Exception {
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


    public Object convertJsonValueToFieldType(String jsonValue, Field field) {
        Class<?> fieldType = field.getType();

        if (Collection.class.isAssignableFrom(fieldType)) {
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType pt) {
                Class<?> itemType = (Class<?>) pt.getActualTypeArguments()[0];
                try {
                    return parseArray(jsonValue, itemType);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return fromJson(jsonValue, fieldType);
    }

    public static Map<String, String> parseJsonToMap(String json) {
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

    private void toJson(Object value, StringBuilder sb) throws IllegalAccessException {
        if (value == null) {
            sb.append("null");
            return;
        }

        if (value instanceof String) {
            sb.append('"').append(escapeString((String) value)).append('"');
            return;
        }

        if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
            return;
        }

        if (value.getClass().isArray()) {
            sb.append('[');
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                toJson(Array.get(value, i), sb);
                if (i < length - 1) {
                    sb.append(',');
                }
            }
            sb.append(']');
            return;
        }

        if (value instanceof Collection<?> collection) {
            sb.append('[');
            Iterator<?> iterator = collection.iterator();
            while (iterator.hasNext()) {
                toJson(iterator.next(), sb);
                if (iterator.hasNext()) {
                    sb.append(',');
                }
            }
            sb.append(']');
            return;
        }

        if (value instanceof Map<?, ?> map) {
            sb.append('{');
            Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<?, ?> entry = iterator.next();
                sb.append('"').append(escapeString(String.valueOf(entry.getKey()))).append("\":");
                toJson(entry.getValue(), sb);
                if (iterator.hasNext()) {
                    sb.append(',');
                }
            }
            sb.append('}');
            return;
        }

        ClassInfo info = getClassInfo(value.getClass());
        sb.append('{');

        List<String> fieldPairs = new ArrayList<>();
        for (Field field : info.fields()) {
            Object fieldValue = field.get(value);
            if (fieldValue != null) {
                StringBuilder fieldValueBuilder = new StringBuilder();
                toJson(fieldValue, fieldValueBuilder);
                fieldPairs.add("\"" + field.getName() + "\":" + fieldValueBuilder.toString());
            }
        }
        sb.append(String.join(",", fieldPairs));
        sb.append('}');
    }

    // Helper to avoid trailing commas for null fields
    private boolean hasNextSerializableField(Iterator<Field> iterator, Object parent) throws IllegalAccessException {
        while (iterator.hasNext()) {
            Field nextField = iterator.next();
            if (nextField.get(parent) != null) {
                //TODO handle that correctly
                return true;
            }
        }
        return false;
    }

    private <T> T parseObject(String json, Class<T> clazz) throws Exception {
        T instance = clazz.getDeclaredConstructor().newInstance();
        Map<String, String> jsonMap = parseJsonToMap(json);

        ClassInfo info = getClassInfo(clazz);
        for (Field field : info.fields()) {
            String jsonValue = jsonMap.get(field.getName());
            if (jsonValue != null) {
                Object value = convertJsonValueToFieldType(jsonValue, field);
                field.set(instance, value);
            }
        }
        return instance;
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