package com.example.reading.utils;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MapParamUtilsTest {

    // ===== asString =====

    @Test
    void asStringReturnsStringValues() {
        Map<String, Object> params = Map.of("k", "hello");
        assertThat(MapParamUtils.asString(params, "k")).isEqualTo("hello");
    }

    @Test
    void asStringConvertsNumbersToString() {
        Map<String, Object> params = Map.of("k", 42);
        assertThat(MapParamUtils.asString(params, "k")).isEqualTo("42");
    }

    @Test
    void asStringReturnsNullForNullMap() {
        assertThat(MapParamUtils.asString(null, "k")).isNull();
    }

    @Test
    void asStringReturnsNullForMissingKey() {
        Map<String, Object> params = Map.of();
        assertThat(MapParamUtils.asString(params, "k")).isNull();
    }

    @Test
    void asStringReturnsNullForIncompatibleType() {
        Map<String, Object> params = Map.of("k", true);
        assertThat(MapParamUtils.asString(params, "k")).isNull();
    }

    // ===== asInt =====

    @Test
    void asIntReturnsIntegerValues() {
        Map<String, Object> params = Map.of("k", 42);
        assertThat(MapParamUtils.asInt(params, "k")).isEqualTo(42);
    }

    @Test
    void asIntParsesNumericStrings() {
        Map<String, Object> params = Map.of("k", "7");
        assertThat(MapParamUtils.asInt(params, "k")).isEqualTo(7);
    }

    @Test
    void asIntReturnsNullForNonNumericString() {
        Map<String, Object> params = Map.of("k", "abc");
        assertThat(MapParamUtils.asInt(params, "k")).isNull();
    }

    @Test
    void asIntReturnsNullForNullMap() {
        assertThat(MapParamUtils.asInt(null, "k")).isNull();
    }

    @Test
    void asIntReturnsNullForMissingKey() {
        Map<String, Object> params = Map.of();
        assertThat(MapParamUtils.asInt(params, "k")).isNull();
    }

    // ===== asLong =====

    @Test
    void asLongReturnsLongValues() {
        Map<String, Object> params = Map.of("k", 100L);
        assertThat(MapParamUtils.asLong(params, "k")).isEqualTo(100L);
    }

    @Test
    void asLongConvertsIntToLong() {
        Map<String, Object> params = Map.of("k", 5);
        assertThat(MapParamUtils.asLong(params, "k")).isEqualTo(5L);
    }

    @Test
    void asLongParsesNumericStrings() {
        Map<String, Object> params = Map.of("k", "999");
        assertThat(MapParamUtils.asLong(params, "k")).isEqualTo(999L);
    }

    @Test
    void asLongReturnsNullForNonNumericString() {
        Map<String, Object> params = Map.of("k", "abc");
        assertThat(MapParamUtils.asLong(params, "k")).isNull();
    }

    @Test
    void asLongReturnsNullForOverflowString() {
        Map<String, Object> params = Map.of("k", "999999999999999999999");
        assertThat(MapParamUtils.asLong(params, "k")).isNull();
    }

    @Test
    void asLongReturnsNullForNullMap() {
        assertThat(MapParamUtils.asLong(null, "k")).isNull();
    }

    @Test
    void asLongReturnsNullForMissingKey() {
        Map<String, Object> params = Map.of();
        assertThat(MapParamUtils.asLong(params, "k")).isNull();
    }

    @Test
    void asLongReturnsNullForIncompatibleType() {
        Map<String, Object> params = Map.of("k", true);
        assertThat(MapParamUtils.asLong(params, "k")).isNull();
    }
}
