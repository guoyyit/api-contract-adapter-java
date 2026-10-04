package com.github.guoyyit.apicontractadapterjava.transform;

import com.github.guoyyit.apicontractadapterjava.contract.TypeConversion;

public final class TypeConverter {

    public static class ConversionException extends RuntimeException {
        public ConversionException(String message) {
            super(message);
        }
    }

    private TypeConverter() {
    }

    public static Object convert(Object value, TypeConversion conversion, String path) {
        if (value == null || conversion == null || conversion == TypeConversion.NONE) {
            return value;
        }
        return switch (conversion) {
            case NONE -> value;
            case TO_STRING -> toString(value, path);
            case TO_INTEGER -> toInteger(value, path);
            case TO_LONG -> toLong(value, path);
            case TO_DOUBLE -> toDouble(value, path);
            case TO_BOOLEAN -> toBoolean(value, path);
        };
    }

    private static Object toString(Object value, String path) {
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        throw failure(value, path, "STRING");
    }

    private static Object toInteger(Object value, String path) {
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return ((Number) value).intValue();
        }
        if (value instanceof Long longValue) {
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                return longValue.intValue();
            }
            throw failure(value, path, "INTEGER (out of range)");
        }
        if (value instanceof Double doubleValue) {
            if (doubleValue == Math.rint(doubleValue) && !Double.isInfinite(doubleValue)
                    && doubleValue >= Integer.MIN_VALUE && doubleValue <= Integer.MAX_VALUE) {
                return doubleValue.intValue();
            }
            throw failure(value, path, "INTEGER (not an integral number)");
        }
        if (value instanceof String text) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException ex) {
                throw failure(value, path, "INTEGER");
            }
        }
        throw failure(value, path, "INTEGER");
    }

    private static Object toLong(Object value, String path) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short
                || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        if (value instanceof Double doubleValue) {
            if (doubleValue == Math.rint(doubleValue) && !Double.isInfinite(doubleValue)
                    && doubleValue >= Long.MIN_VALUE && doubleValue <= Long.MAX_VALUE) {
                return doubleValue.longValue();
            }
            throw failure(value, path, "LONG (not an integral number)");
        }
        if (value instanceof String text) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ex) {
                throw failure(value, path, "LONG");
            }
        }
        throw failure(value, path, "LONG");
    }

    private static Object toDouble(Object value, String path) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                double parsed = Double.parseDouble(text.trim());
                if (Double.isFinite(parsed)) {
                    return parsed;
                }
                throw failure(value, path, "DOUBLE (non-finite)");
            } catch (NumberFormatException ex) {
                throw failure(value, path, "DOUBLE");
            }
        }
        throw failure(value, path, "DOUBLE");
    }

    private static Object toBoolean(Object value, String path) {
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(text)) {
                return Boolean.FALSE;
            }
        }
        throw failure(value, path, "BOOLEAN");
    }

    private static ConversionException failure(Object value, String path, String targetType) {
        String shown = value instanceof String ? "\"" + value + "\"" : String.valueOf(value);
        return new ConversionException(
                "cannot convert value " + shown + " (" + typeName(value) + ") to " + targetType);
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
