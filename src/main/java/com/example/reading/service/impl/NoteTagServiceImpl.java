package com.example.reading.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.reading.entity.NoteTag;
import com.example.reading.mapper.NoteTagMapper;
import com.example.reading.service.INoteTagService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class NoteTagServiceImpl extends ServiceImpl<NoteTagMapper, NoteTag> implements INoteTagService {

    @Override
    public void bindTags(Long noteId, List<Long> tagIds) {
        // 先删除旧绑定
        QueryWrapper<NoteTag> deleteQuery = new QueryWrapper<>();
        deleteQuery.eq("note_id", noteId);
        remove(deleteQuery);
        // 批量插入新绑定
        if (tagIds != null && !tagIds.isEmpty()) {
            List<NoteTag> bindings = tagIds.stream().map(tagId -> {
                NoteTag nt = new NoteTag();
                nt.setNoteId(noteId);
                nt.setTagId(tagId);
                return nt;
            }).toList();
            saveBatch(bindings);
        }
    }

    @Override
    public void unbindTags(Long noteId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return;
        QueryWrapper<NoteTag> deleteQuery = new QueryWrapper<>();
        deleteQuery.eq("note_id", noteId).in("tag_id", tagIds);
        remove(deleteQuery);
    }

    @Override
    public List<Long> getTagIdsByNoteId(Long noteId) {
        QueryWrapper<NoteTag> query = new QueryWrapper<>();
        query.eq("note_id", noteId);
        return list(query).stream().map(NoteTag::getTagId).toList();
    }
}
