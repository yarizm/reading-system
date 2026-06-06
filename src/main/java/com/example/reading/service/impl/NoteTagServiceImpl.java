package com.example.reading.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.reading.entity.NoteTag;
import com.example.reading.mapper.NoteTagMapper;
import com.example.reading.service.INoteTagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class NoteTagServiceImpl extends ServiceImpl<NoteTagMapper, NoteTag> implements INoteTagService {

    @Override
    @Transactional
    public void bindTags(Long noteId, List<Long> tagIds) {
        // 查询已有绑定，增量添加新标签
        QueryWrapper<NoteTag> existingQuery = new QueryWrapper<>();
        existingQuery.eq("note_id", noteId);
        List<Long> existingTagIds = list(existingQuery).stream().map(NoteTag::getTagId).toList();

        if (tagIds != null && !tagIds.isEmpty()) {
            // 只添加尚未绑定的标签
            List<Long> toAdd = tagIds.stream()
                    .filter(tid -> !existingTagIds.contains(tid))
                    .toList();
            if (!toAdd.isEmpty()) {
                List<NoteTag> bindings = toAdd.stream().map(tagId -> {
                    NoteTag nt = new NoteTag();
                    nt.setNoteId(noteId);
                    nt.setTagId(tagId);
                    return nt;
                }).toList();
                saveBatch(bindings);
            }
        }
    }

    /**
     * 全量替换标签绑定（用于明确需要覆盖的场景）
     */
    @Override
    @Transactional
    public void setTags(Long noteId, List<Long> tagIds) {
        QueryWrapper<NoteTag> deleteQuery = new QueryWrapper<>();
        deleteQuery.eq("note_id", noteId);
        remove(deleteQuery);
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
