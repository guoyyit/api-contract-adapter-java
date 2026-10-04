package com.github.guoyyit.apicontractadapterjava.tests;

import com.github.guoyyit.apicontractadapterjava.contract.TypeConversion;
import com.github.guoyyit.apicontractadapterjava.transform.TypeConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TypeConverterTest {

    @Test
    void nullPassesThroughAnyConversion() {
        for (TypeConversion conversion : TypeConversion.values()) {
            assertNull(TypeConverter.convert(null, conversion, "p"));
        }
    }

    @Test
    void noneReturnsValueAsIs() {
        Object value = new Object();
        assertEquals(value, TypeConverter.convert(value, TypeConversion.NONE, "p"));
    }

    @Test
    void convertsToIntegerStrictly() {
        assertEquals(42, TypeConverter.convert("42", TypeConversion.TO_INTEGER, "p"));
        assertEquals(42, TypeConverter.convert(42, TypeConversion.TO_INTEGER, "p"));
        assertEquals(42, TypeConverter.convert(42L, TypeConversion.TO_INTEGER, "p"));
        assertEquals(42, TypeConverter.convert(42.0, TypeConversion.TO_INTEGER, "p"));
        assertThrows(TypeConverter.ConversionException.class,
                () -> TypeConverter.convert("4x", TypeConversion.TO_INTEGER, "p"));
        assertThrows(TypeConverter.ConversionException.class,
                () -> TypeConverter.convert(4.5, TypeConversion.TO_INTEGER, "p"));
        assertThrows(TypeConverter.ConversionException.class,
                () -> TypeConverter.convert(true, TypeConversion.TO_INTEGER, "p"));
        assertThrows(TypeConverter.ConversionException.class,
                () -> TypeConverter.convert(3_000_000_000L, TypeConversion.TO_INTEGER, "p"));
    }

    @Test
    void convertsToLongStrictly() {
        assertEquals(9_223_372_036_854_775_807L,
                TypeConverter.convert("9223372036854775807", TypeConversion.TO_LONG, "p"));
        assertEquals(7L, TypeConverter.convert(7, TypeConversion.TO_LONG, "p"));
        assertThrows(TypeConverter.ConversionException.class,
                () -> TypeConverter.convert("1.5", TypeConversion.TO_LONG, "p"));
    }

    @Test
    void convertsToDoubleStrictly() {
        assertEquals(1.5, TypeConverter.convert("1.5", TypeConversion.TO_DOUBLE, "p"));
        assertEquals(2.0, TypeConverter.convert(2, TypeConversion.TO_DOUBLE, "p"));
        assertThrows(TypeConverter.ConversionException.class,
                () -> TypeConverter.convert("NaN", TypeConversion.TO_DOUBLE, "p"));
        assertThrows(TypeConverter.ConversionException.class,
                () -> TypeConverter.convert("abc", TypeConversion.TO_DOUBLE, "p"));
    }

    @Test
    void convertsToBooleanStrictly() {
        assertEquals(true, TypeConverter.convert("true", TypeConversion.TO_BOOLEAN, "p"));
        assertEquals(false, TypeConverter.convert("FALSE", TypeConversion.TO_BOOLEAN, "p"));
        assertEquals(true, TypeConverter.convert(true, TypeConversion.TO_BOOLEAN, "p"));
        assertThrows(TypeConverter.ConversionException.class,
                () -> TypeConverter.convert("yes", TypeConversion.TO_BOOLEAN, "p"));
        assertThrows(TypeConverter.ConversionException.class,
                () -> TypeConverter.convert(1, TypeConversion.TO_BOOLEAN, "p"));
    }

    @Test
    void convertsToString() {
        assertEquals("5", TypeConverter.convert(5, TypeConversion.TO_STRING, "p"));
        assertEquals("true", TypeConverter.convert(true, TypeConversion.TO_STRING, "p"));
        assertEquals("x", TypeConverter.convert("x", TypeConversion.TO_STRING, "p"));
    }

    @Test
    void failureMessageContainsValueAndTargetType() {
        TypeConverter.ConversionException ex = assertThrows(TypeConverter.ConversionException.class,
                () -> TypeConverter.convert("abc", TypeConversion.TO_INTEGER, "age"));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("abc"));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("INTEGER"));
    }
}
