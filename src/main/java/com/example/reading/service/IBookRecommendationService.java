package com.example.reading.service;

import com.example.reading.entity.SysBook;

import java.util.List;

public interface IBookRecommendationService {

    List<SysBook> recommendHomeBooks(Long userId, boolean refresh);
}
