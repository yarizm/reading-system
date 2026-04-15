package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.dto.AiRequestDto;
import com.example.reading.dto.AudioGenerateRequest;
import com.example.reading.dto.AudioGenerateResponse;
import com.example.reading.service.tts.AudioSynthesisService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
public class AiController {

    private final AudioSynthesisService audioSynthesisService;

    public AiController(AudioSynthesisService audioSynthesisService) {
        this.audioSynthesisService = audioSynthesisService;
    }

    @PostMapping("/tts")
    public Result<String> tts(@RequestBody AiRequestDto request) {
        return audioSynthesisService.tts(request);
    }

    @PostMapping("/audio/generate")
    public Result<AudioGenerateResponse> generateAudio(@RequestBody AudioGenerateRequest request) {
        return audioSynthesisService.generateAudio(request);
    }
}
