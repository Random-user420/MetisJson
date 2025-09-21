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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    public void clearCache() {
        classInfoCache.clear();
    }

    // Cache to store reflection data for classes. Thread-safe.
    private final Map<Class<?>, ClassInfo> classInfoCache = new ConcurrentHashMap<>();

    private record ClassInfo(List<Field> fields) {
    }

    private ClassInfo getClassInfo(Class<?> clazz) {
        return classInfoCache.computeIfAbsent(clazz, c -> {
            List<Field> serializableFields = new ArrayList<>();
            // Use getDeclaredFields to access private fields
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isTransient(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true); // Crucial for private fields
                serializableFields.add(field);
            }
            return new ClassInfo(serializableFields);
        });
    }

    private Object parseArray(String json, Class<?> itemClazz) throws Exception {
        List<Object> list = new ArrayList<>();
        String content = json.substring(1, json.length() - 1).trim();
        if (content.isEmpty()) {
            return Array.newInstance(itemClazz, 0);
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

        Object array = Array.newInstance(itemClazz, list.size());
        for (int i = 0; i < list.size(); i++) {
            Array.set(array, i, list.get(i));
        }
        return array;
    }


    public Object convertJsonValueToFieldType(String jsonValue, Field field) {
        Class<?> fieldType = field.getType();

        if (Collection.class.isAssignableFrom(fieldType)) {
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType pt) {
                Class<?> itemType = (Class<?>) pt.getActualTypeArguments()[0];
                try {
                    Object parsedResult = parseArray(jsonValue, itemType);
                    int length = Array.getLength(parsedResult);
                    List<Object> resultList = new ArrayList<>(length);
                    for (int i = 0; i < length; i++) {
                        resultList.add(Array.get(parsedResult, i));
                    }
                    return resultList;

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
        if (content.isEmpty()) {
            return map;
        }

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