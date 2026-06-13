package com.example.reading.utils;

import java.util.Map;

/**
 * 从 Map&lt;String, Object&gt; 请求体中安全提取类型化参数的工具方法。
 * 统一处理 null、类型不匹配和数字格式异常，替代各控制器中的内联 try-catch 解析。
 */
public final class MapParamUtils {

    private MapParamUtils() {}

    /** 提取字符串参数；null map / null value / 非字符串非数字类型 → null */
    public static String asString(Map<String, Object> params, String key) {
        if (params == null) return null;
        Object value = params.get(key);
        if (value instanceof CharSequence || value instanceof Number) {
            return value.toString();
        }
        return null;
    }

    /** 提取整型参数；支持 Number 直取或字符串解析，异常 → null */
    public static Integer asInt(Map<String, Object> params, String key) {
        if (params == null) return null;
        Object value = params.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof CharSequence text) {
            try {
                return Integer.valueOf(text.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 提取长整型参数；支持 Number 直取或字符串解析，异常 → null */
    public static Long asLong(Map<String, Object> params, String key) {
        if (params == null) return null;
        Object value = params.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof CharSequence text) {
            try {
                return Long.valueOf(text.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
