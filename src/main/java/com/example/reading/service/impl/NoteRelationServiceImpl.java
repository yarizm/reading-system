package com.example.reading.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.reading.entity.NoteRelation;
import com.example.reading.mapper.NoteRelationMapper;
import com.example.reading.service.INoteRelationService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class NoteRelationServiceImpl extends ServiceImpl<NoteRelationMapper, NoteRelation> implements INoteRelationService {

    @Override
    public NoteRelation createRelation(Long noteId1, Long noteId2) {
        // 保证 noteId1 < noteId2
        Long smaller = Math.min(noteId1, noteId2);
        Long larger = Math.max(noteId1, noteId2);

        // 检查是否已存在
        QueryWrapper<NoteRelation> check = new QueryWrapper<>();
        check.eq("note_id_1", smaller).eq("note_id_2", larger);
        if (count(check) > 0) return getOne(check);

        NoteRelation relation = new NoteRelation();
        relation.setNoteId1(smaller);
        relation.setNoteId2(larger);
        try {
            save(relation);
        } catch (DuplicateKeyException e) {
            // 并发请求命中唯一约束，返回已有记录
            return getOne(check);
        }
        return relation;
    }

    @Override
    public List<NoteRelation> getRelationsByNoteId(Long noteId) {
        QueryWrapper<NoteRelation> query = new QueryWrapper<>();
        query.eq("note_id_1", noteId).or().eq("note_id_2", noteId);
        return list(query);
    }
}
