package com.github.guoyyit.apicontractadapterjava.contract.engine;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 类型转换器：按目标类型名转换标量值，失败时抛出带原因的异常。
 */
@Component
public class TypeConverter {

    private static final Set<String> SUPPORTED = Set.of(
            "string", "integer", "long", "double", "boolean");

    /**
     * 将值转换为目标类型。
     *
     * @param value      原始值（非 null）
     * @param targetType 目标类型名
     * @return 转换后的值
     * @throws IllegalArgumentException 转换失败
     */
    public Object convert(Object value, String targetType) {
        if (targetType == null) {
            return value;
        }
        return switch (targetType) {
            case "string" -> toStringValue(value);
            case "integer" -> toInteger(value);
            case "long" -> toLong(value);
            case "double" -> toDouble(value);
            case "boolean" -> toBoolean(value);
            default -> throw new IllegalArgumentException("unsupported target type: " + targetType);
        };
    }

    /**
     * 目标类型名是否受支持。
     */
    public boolean supports(String targetType) {
        return SUPPORTED.contains(targetType);
    }

    private Object toStringValue(Object value) {
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        throw new IllegalArgumentException(
                "cannot convert " + value.getClass().getSimpleName() + " to string");
    }

    private Object toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("cannot convert \"" + text + "\" to integer");
            }
        }
        throw new IllegalArgumentException(
                "cannot convert " + value.getClass().getSimpleName() + " to integer");
    }

    private Object toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("cannot convert \"" + text + "\" to long");
            }
        }
        throw new IllegalArgumentException(
                "cannot convert " + value.getClass().getSimpleName() + " to long");
    }

    private Object toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.valueOf(text.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("cannot convert \"" + text + "\" to double");
            }
        }
        throw new IllegalArgumentException(
                "cannot convert " + value.getClass().getSimpleName() + " to double");
    }

    private Object toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if ("true".equalsIgnoreCase(trimmed)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(trimmed)) {
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("cannot convert \"" + text + "\" to boolean");
        }
        throw new IllegalArgumentException(
                "cannot convert " + value.getClass().getSimpleName() + " to boolean");
    }
}
