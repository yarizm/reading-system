package com.example.reading.utils;

import com.example.reading.dto.AudioShareRequest;
import com.example.reading.dto.ParagraphShareRequest;
import com.example.reading.entity.BookShare;
import com.example.reading.entity.SysBook;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShareMessageBuilderTest {

    @Test
    void buildAudioShareContentProducesParseableJsonWithEscapedTextAndNullNumbers() {
        AudioShareRequest request = new AudioShareRequest();
        request.setAudioUrl("https://cdn.example/audio?name=\"chapter\"\\clip\npart");
        request.setTitle("  Listen \"now\" \\  ");
        request.setSourceType(null);
        request.setBookId(null);
        request.setChapterIndex(2);
        request.setParagraphIndex(null);
        request.setMessage("  hello \"reader\"\\friend\nnext  ");

        JsonObject payload = parsePayload(
                ShareMessageBuilder.buildAudioShareContent(request),
                ShareMessageBuilder.AUDIO_SHARE_PREFIX
        );

        assertThat(payload.get("audioUrl").getAsString())
                .isEqualTo("https://cdn.example/audio?name=\"chapter\"\\clip\npart");
        assertThat(payload.get("title").getAsString()).isEqualTo("Listen \"now\" \\");
        assertThat(payload.get("sourceType").getAsString()).isEqualTo(ShareMessageBuilder.DEFAULT_AUDIO_SOURCE_TYPE);
        assertThat(payload.get("bookId").isJsonNull()).isTrue();
        assertThat(payload.get("chapterIndex").getAsInt()).isEqualTo(2);
        assertThat(payload.get("paragraphIndex").isJsonNull()).isTrue();
        assertThat(payload.get("message").getAsString()).isEqualTo("hello \"reader\"\\friend\nnext");
    }

    @Test
    void buildAudioShareContentUsesReadableDefaultTitle() {
        AudioShareRequest request = new AudioShareRequest();
        request.setAudioUrl("/files/a.mp3");

        JsonObject payload = parsePayload(
                ShareMessageBuilder.buildAudioShareContent(request),
                ShareMessageBuilder.AUDIO_SHARE_PREFIX
        );

        assertThat(ShareMessageBuilder.DEFAULT_AUDIO_TITLE).isEqualTo("朗读音频");
        assertThat(payload.get("title").getAsString()).isEqualTo("朗读音频");
        assertThat(payload.get("sourceType").getAsString()).isEqualTo("paragraph");
    }

    @Test
    void paragraphShareDefaultBookTitleIsReadable() {
        assertThat(ShareMessageBuilder.DEFAULT_PARAGRAPH_BOOK_TITLE).isEqualTo("当前书籍");
    }

    @Test
    void buildParagraphShareContentNormalizesTruncatesAndEscapesPayload() {
        ParagraphShareRequest request = new ParagraphShareRequest();
        request.setBookId(8L);
        request.setChapterIndex(0);
        request.setParagraphIndex(5);
        request.setQuote("  \"quote\" line\r\n" + "x".repeat(200));
        request.setMessage("  note\r\n" + "m".repeat(130));

        String normalizedQuote = ShareMessageBuilder.normalizeParagraphText(request.getQuote());
        JsonObject payload = parsePayload(
                ShareMessageBuilder.buildParagraphShareContent("Book \"A\" \\ B", request, normalizedQuote),
                ShareMessageBuilder.PARAGRAPH_SHARE_PREFIX
        );

        assertThat(payload.get("bookId").getAsLong()).isEqualTo(8L);
        assertThat(payload.get("bookTitle").getAsString()).isEqualTo("Book \"A\" \\ B");
        assertThat(payload.get("chapterIndex").getAsInt()).isEqualTo(0);
        assertThat(payload.get("paragraphIndex").getAsInt()).isEqualTo(5);
        assertThat(payload.get("quote").getAsString()).isEqualTo(truncate(normalizedQuote, 180));
        assertThat(payload.get("message").getAsString())
                .isEqualTo(truncate(ShareMessageBuilder.normalizeParagraphText(request.getMessage()), 120));
    }

    @Test
    void buildBookShareContentUsesBookMetadataAndTrimsMessage() {
        BookShare share = new BookShare();
        share.setId(12L);
        share.setBookId(34L);
        share.setMessage("  read \"this\" \\  ");

        SysBook book = new SysBook();
        book.setTitle("Title \"Q\"");
        book.setAuthor("Author \\ Name");
        book.setCoverUrl("/files/cover\"1\".jpg");

        JsonObject payload = parsePayload(
                ShareMessageBuilder.buildBookShareContent(share, book),
                ShareMessageBuilder.BOOK_SHARE_PREFIX
        );

        assertThat(payload.get("shareId").getAsLong()).isEqualTo(12L);
        assertThat(payload.get("bookId").getAsLong()).isEqualTo(34L);
        assertThat(payload.get("bookTitle").getAsString()).isEqualTo("Title \"Q\"");
        assertThat(payload.get("bookAuthor").getAsString()).isEqualTo("Author \\ Name");
        assertThat(payload.get("coverUrl").getAsString()).isEqualTo("/files/cover\"1\".jpg");
        assertThat(payload.get("message").getAsString()).isEqualTo("read \"this\" \\");
    }

    private static JsonObject parsePayload(String content, String prefix) {
        assertThat(content).startsWith(prefix);
        return JsonParser.parseString(content.substring(prefix.length())).getAsJsonObject();
    }

    private static String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
