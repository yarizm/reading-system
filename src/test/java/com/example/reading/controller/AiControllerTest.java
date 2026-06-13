package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.dto.AiRequestDto;
import com.example.reading.dto.AudioGenerateRequest;
import com.example.reading.dto.AudioGenerateResponse;
import com.example.reading.mapper.SysChapterMapper;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.tts.AudioSynthesisService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    @Mock
    private AudioSynthesisService audioSynthesisService;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private SysChapterMapper chapterMapper;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AiController controller;

    @Test
    void ttsRejectsMissingBodyWithoutCallingSynthesis() {
        when(authContextService.isAuthenticated(request)).thenReturn(true);

        Result<String> result = controller.tts(null, request);

        assertThat(result.getCode()).isEqualTo("400");
        verify(audioSynthesisService, never()).tts(any(AiRequestDto.class));
    }

    @Test
    void generateAudioRejectsMissingBodyWithoutCallingSynthesis() {
        when(authContextService.isAuthenticated(request)).thenReturn(true);

        Result<AudioGenerateResponse> result = controller.generateAudio(null, request);

        assertThat(result.getCode()).isEqualTo("400");
        verify(audioSynthesisService, never()).generateAudio(any(AudioGenerateRequest.class));
    }
}
