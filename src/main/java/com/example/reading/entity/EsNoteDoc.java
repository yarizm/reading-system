package com.example.reading.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(indexName = "es_note")
@Setting(shards = 1, replicas = 0)
public class EsNoteDoc {

    @Id
    private Long id;

    @Field(type = FieldType.Long)
    private Long userId;

    @Field(type = FieldType.Long)
    private Long bookId;

    @Field(type = FieldType.Text, analyzer = "cjk")
    private String bookTitle;

    @Field(type = FieldType.Text, analyzer = "cjk")
    private String selectedText;

    @Field(type = FieldType.Text, analyzer = "cjk")
    private String content;

    @Field(type = FieldType.Keyword)
    private List<String> tagIds;

    @Field(type = FieldType.Text, analyzer = "cjk")
    private String tagNames;

    @Field(type = FieldType.Date)
    private LocalDateTime createTime;
}
