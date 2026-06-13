package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.ImportService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportControllerTest {

    @Mock
    private ImportService importService;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private MultipartFile file;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private ImportController controller;

    @Test
    void uploadRejectsUnauthenticatedWithoutCallingService() {
        when(authContextService.currentUserId(httpRequest)).thenReturn(null);

        Result<?> result = controller.upload(file, "epub", httpRequest);

        assertThat(result.getCode()).isEqualTo("403");
        verify(importService, never()).importFile(any(), any(), any());
    }

    @Test
    void uploadRejectsEmptyFileWithoutCallingService() {
        when(authContextService.currentUserId(httpRequest)).thenReturn(1L);
        when(file.isEmpty()).thenReturn(true);

        Result<?> result = controller.upload(file, "epub", httpRequest);

        assertThat(result.getCode()).isEqualTo("400");
        verify(importService, never()).importFile(any(), any(), any());
    }

    @Test
    void uploadDelegatesToServiceForNonEmptyFile() {
        when(authContextService.currentUserId(httpRequest)).thenReturn(1L);
        when(file.isEmpty()).thenReturn(false);
        ImportService.ImportResult serviceResult = new ImportService.ImportResult();
        when(importService.importFile(file, "epub", 1L)).thenReturn(serviceResult);

        Result<?> result = controller.upload(file, "epub", httpRequest);

        assertThat(result.getCode()).isEqualTo("200");
        assertThat(result.getData()).isSameAs(serviceResult);
    }
}
