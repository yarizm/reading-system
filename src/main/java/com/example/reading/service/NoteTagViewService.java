package com.example.reading.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.entity.NoteTag;
import com.example.reading.entity.SysTag;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class NoteTagViewService {

    private final INoteTagService noteTagService;
    private final ISysTagService tagService;

    public NoteTagViewService(INoteTagService noteTagService, ISysTagService tagService) {
        this.noteTagService = noteTagService;
        this.tagService = tagService;
    }

    public Map<Long, List<Map<String, Object>>> listTagInfoByNoteIds(Collection<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return Map.of();
        }

        QueryWrapper<NoteTag> query = new QueryWrapper<>();
        query.in("note_id", noteIds);

        Map<Long, List<Long>> tagIdsByNoteId = new LinkedHashMap<>();
        Set<Long> allTagIds = new LinkedHashSet<>();
        for (NoteTag noteTag : noteTagService.list(query)) {
            if (noteTag.getNoteId() == null || noteTag.getTagId() == null) {
                continue;
            }
            tagIdsByNoteId.computeIfAbsent(noteTag.getNoteId(), key -> new ArrayList<>()).add(noteTag.getTagId());
            allTagIds.add(noteTag.getTagId());
        }

        Map<Long, SysTag> tagMap = new HashMap<>();
        if (!allTagIds.isEmpty()) {
            for (SysTag tag : tagService.listByIds(allTagIds)) {
                tagMap.put(tag.getId(), tag);
            }
        }

        Map<Long, List<Map<String, Object>>> result = new HashMap<>();
        for (Map.Entry<Long, List<Long>> entry : tagIdsByNoteId.entrySet()) {
            List<Map<String, Object>> tags = entry.getValue().stream()
                    .map(tagMap::get)
                    .filter(Objects::nonNull)
                    .map(this::toTagInfo)
                    .toList();
            result.put(entry.getKey(), tags);
        }
        return result;
    }

    private Map<String, Object> toTagInfo(SysTag tag) {
        Map<String, Object> tagInfo = new HashMap<>();
        tagInfo.put("id", tag.getId());
        tagInfo.put("name", tag.getName());
        tagInfo.put("color", tag.getColor());
        return tagInfo;
    }
}
