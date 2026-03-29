package com.example.reading.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

/**
 * Elasticsearch 索引文档实体
 * 映射到 ES 索引 "es_book"
 */
@Data
@Document(indexName = "es_book")
@Setting(shards = 1, replicas = 0)
public class EsBookDoc {

    @Id
    private Long id;

    /**
     * 书名 - 使用 ik_max_word 最细粒度分词，搜索时使用 ik_smart 智能分词
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String title;

    /**
     * 作者
     */
    @Field(type = FieldType.Text, analyzer = "ik_smart")
    private String author;

    /**
     * 简介
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String description;

    /**
     * 分类 - 不分词，精确匹配
     */
    @Field(type = FieldType.Keyword)
    private String category;

    /**
     * 封面URL - 不分词，仅存储
     */
    @Field(type = FieldType.Keyword, index = false)
    private String coverUrl;

    /**
     * 所有章节正文拼接 - 核心搜索内容
     * 使用 ik_max_word 建立最细粒度的倒排索引
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String chapterContents;
}
