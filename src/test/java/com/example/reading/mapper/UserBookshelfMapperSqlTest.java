package com.example.reading.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class UserBookshelfMapperSqlTest {

    @Test
    void shelfListQueriesPreAggregateChapterMetadataInsteadOfPerRowLookups() throws NoSuchMethodException {
        String myShelfSql = selectSql("selectMyShelf");
        String publicShelfSql = selectSql("selectPublicShelf");

        assertOptimizedShelfSql(myShelfSql);
        assertOptimizedShelfSql(publicShelfSql);
    }

    private static void assertOptimizedShelfSql(String sql) {
        assertThat(sql)
                .contains("chapter_counts")
                .contains("current_chapters")
                .contains("IFNULL(chapter_counts.totalChapters, 0) AS totalChapters")
                .doesNotContain("(SELECT COUNT(*) FROM sys_chapter c WHERE c.book_id = b.id)")
                .doesNotContain("AND c.sort = s.current_chapter_index LIMIT 1");
    }

    private static String selectSql(String methodName) throws NoSuchMethodException {
        Method method = UserBookshelfMapper.class.getMethod(methodName, Long.class);
        return String.join(" ", method.getAnnotation(Select.class).value());
    }
}
