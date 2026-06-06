package com.example.reading.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.reading.entity.SysTag;
import com.example.reading.mapper.SysTagMapper;
import com.example.reading.service.ISysTagService;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;

@Service
public class SysTagServiceImpl extends ServiceImpl<SysTagMapper, SysTag> implements ISysTagService {

    private static final List<String[]> SYSTEM_TAGS = Arrays.asList(
            new String[]{"核心观点", "#e74c3c"},
            new String[]{"疑问", "#f39c12"},
            new String[]{"灵感", "#2ecc71"},
            new String[]{"金句", "#9b59b6"}
    );

    @Override
    public List<SysTag> getUserTags(Long userId) {
        QueryWrapper<SysTag> query = new QueryWrapper<>();
        query.eq("user_id", userId).orderByDesc("is_system").orderByAsc("create_time");
        return list(query);
    }

    @Override
    public void ensureSystemTags(Long userId) {
        QueryWrapper<SysTag> check = new QueryWrapper<>();
        check.eq("user_id", userId).eq("is_system", 1);
        if (count(check) > 0) return;

        List<SysTag> systemTags = SYSTEM_TAGS.stream().map(t -> {
            SysTag tag = new SysTag();
            tag.setUserId(userId);
            tag.setName(t[0]);
            tag.setColor(t[1]);
            tag.setIsSystem(1);
            return tag;
        }).toList();
        saveBatch(systemTags);
    }
}
