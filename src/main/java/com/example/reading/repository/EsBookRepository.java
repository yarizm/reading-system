package com.example.reading.repository;

import com.example.reading.entity.EsBookDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * Elasticsearch 书籍文档 Repository
 * Spring Data 自动实现基本 CRUD
 */
@Repository
public interface EsBookRepository extends ElasticsearchRepository<EsBookDoc, Long> {
}
