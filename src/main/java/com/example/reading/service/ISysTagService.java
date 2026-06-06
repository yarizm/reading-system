package com.example.reading.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.reading.entity.SysTag;
import java.util.List;

public interface ISysTagService extends IService<SysTag> {
    List<SysTag> getUserTags(Long userId);
    void ensureSystemTags(Long userId);
}
