package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.dto.AiRequestDto;
import com.example.reading.dto.AudioGenerateRequest;
import com.example.reading.dto.AudioGenerateResponse;
import com.example.reading.entity.SysChapter;
import com.example.reading.mapper.SysChapterMapper;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.tts.AudioSynthesisService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
public class AiController {

    private final AudioSynthesisService audioSynthesisService;
    private final AuthContextService authContextService;
    private final SysChapterMapper chapterMapper;

    public AiController(AudioSynthesisService audioSynthesisService,
                        AuthContextService authContextService,
                        SysChapterMapper chapterMapper) {
        this.audioSynthesisService = audioSynthesisService;
        this.authContextService = authContextService;
        this.chapterMapper = chapterMapper;
    }

    private boolean canGenerateForAudioRequest(AudioGenerateRequest request, HttpServletRequest httpRequest) {
        Long bookId = request.getBookId();
        if (request.getChapterId() != null) {
            SysChapter chapter = chapterMapper.selectById(request.getChapterId());
            if (chapter == null) {
                return false;
            }
            if (bookId != null && !bookId.equals(chapter.getBookId())) {
                return false;
            }
            bookId = chapter.getBookId();
            request.setBookId(bookId);
        }
        return authContextService.canViewBook(bookId, httpRequest);
    }

    @PostMapping("/tts")
    public Result<String> tts(@RequestBody AiRequestDto request,
                              HttpServletRequest httpRequest) {
        if (!authContextService.isAuthenticated(httpRequest)) {
            return Result.error("403", "Forbidden");
        }
        if (!authContextService.canViewBook(request.getBookId(), httpRequest)) {
            return Result.error("403", "Forbidden");
        }
        return audioSynthesisService.tts(request);
    }

    @PostMapping("/audio/generate")
    public Result<AudioGenerateResponse> generateAudio(@RequestBody AudioGenerateRequest request,
                                                       HttpServletRequest httpRequest) {
        if (!authContextService.isAuthenticated(httpRequest)) {
            return Result.error("403", "Forbidden");
        }
        if (!canGenerateForAudioRequest(request, httpRequest)) {
            return Result.error("403", "Forbidden");
        }
        return audioSynthesisService.generateAudio(request);
    }
}
