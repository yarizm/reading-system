package com.example.reading.service;

import com.example.reading.entity.SysBook;
import com.example.reading.entity.SysChapter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class DifyKnowledgeBaseServiceTest {

    private final DifyKnowledgeBaseService service = new DifyKnowledgeBaseService();

    @Test
    void buildChapterDocumentNameUsesBookTitleDisplayChapterNumberAndChapterTitle() {
        SysBook book = new SysBook();
        book.setTitle("三体");

        SysChapter chapter = new SysChapter();
        chapter.setSort(2);
        chapter.setTitle("宇宙闪烁");

        String documentName = ReflectionTestUtils.invokeMethod(
                service,
                "buildChapterDocumentName",
                book,
                chapter
        );

        assertThat(documentName).isEqualTo("三体 - Ch3: 宇宙闪烁");
    }

    @Test
    void buildChapterContentIncludesBookAuthorChapterAndBodyText() {
        SysBook book = new SysBook();
        book.setTitle("三体");
        book.setAuthor("刘慈欣");

        SysChapter chapter = new SysChapter();
        chapter.setTitle("科学边界");
        chapter.setContent("物理学从来就没有存在过。");

        String content = ReflectionTestUtils.invokeMethod(
                service,
                "buildChapterContent",
                book,
                chapter
        );

        assertThat(content)
                .startsWith("# 三体\n\n")
                .contains("刘慈欣")
                .contains("## 科学边界\n\n")
                .contains("物理学从来就没有存在过。")
                .endsWith("\n\n");
    }
}
