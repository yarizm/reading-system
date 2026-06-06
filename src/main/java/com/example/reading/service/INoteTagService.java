package com.example.reading.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.reading.entity.NoteTag;
import java.util.List;

public interface INoteTagService extends IService<NoteTag> {
    void bindTags(Long noteId, List<Long> tagIds);
    void unbindTags(Long noteId, List<Long> tagIds);
    List<Long> getTagIdsByNoteId(Long noteId);
}
