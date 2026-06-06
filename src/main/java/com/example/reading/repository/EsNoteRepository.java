package com.example.reading.repository;

import com.example.reading.entity.EsNoteDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EsNoteRepository extends ElasticsearchRepository<EsNoteDoc, Long> {
}
