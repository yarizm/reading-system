package com.example.reading.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.reading.entity.NoteRelation;
import java.util.List;

public interface INoteRelationService extends IService<NoteRelation> {
    NoteRelation createRelation(Long noteId1, Long noteId2);
    List<NoteRelation> getRelationsByNoteId(Long noteId);
}
