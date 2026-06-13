package com.example.reading.utils;

public final class PaginationUtils {

    private PaginationUtils() {
    }

    public static int pageNum(int pageNum) {
        return Math.max(1, pageNum);
    }

    public static int pageNum(Integer pageNum) {
        return pageNum == null ? 1 : pageNum(pageNum.intValue());
    }

    public static int pageSize(int pageSize) {
        return Math.max(1, pageSize);
    }

    public static int pageSize(Integer pageSize) {
        return pageSize == null ? 1 : pageSize(pageSize.intValue());
    }
}
