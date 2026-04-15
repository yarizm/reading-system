package com.example.reading.service.tts;

import java.io.File;

public interface TtsProvider {

    String getProviderName();

    String getFileExtension();

    void synthesizeToFile(String text, String voiceKey, File outputFile) throws Exception;
}
