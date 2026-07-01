package com.example.getttsapi.service;

import com.example.getttsapi.dto.TtsRequest;
import com.example.getttsapi.dto.TtsResponse;
import com.example.getttsapi.dto.SttResponse;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;

@Service
public class BotService {

    @Autowired
    private UzbekVoiceApiClient uzbekVoiceApiClient;

    @Autowired
    private SpeechService speechService;

    /**
     * Convert voice file to text (STT)
     */
    public SttResponse convertVoiceToText(org.telegram.telegrambots.meta.api.objects.File telegramFile) {
        try {
            System.out.println("========== BOT STT REQUEST STARTED ==========");

            // Download voice file from Telegram
            String voiceFilePath = downloadTelegramFile(telegramFile);

            if (voiceFilePath == null) {
                throw new RuntimeException("Failed to download voice file from Telegram");
            }

            System.out.println("📁 Voice file: " + voiceFilePath);

            // Call Uzbekvoice API for STT
            JsonObject apiResponse = uzbekVoiceApiClient.speechToText(
                    voiceFilePath,
                    "uz",      // language
                    "general", // model
                    false,     // returnOffsets
                    true       // blocking
            );

            System.out.println("📨 API Response: " + apiResponse.toString());

            // Parse response
            String extractedText = "";
            String externalId = "";

            if (apiResponse.has("result") && apiResponse.get("result").isJsonObject()) {
                JsonObject result = apiResponse.get("result").getAsJsonObject();
                if (result.has("text")) {
                    extractedText = result.get("text").getAsString();
                    System.out.println("✅ Text extracted: " + extractedText);
                }
            }

            if (apiResponse.has("id")) {
                externalId = apiResponse.get("id").getAsString();
            }

            // Save to database via SpeechService
            speechService.saveSTTRecord("STT", "uz", extractedText, externalId);

            System.out.println("💾 Record saved to DB");
            System.out.println("========== BOT STT REQUEST COMPLETED ==========\n");

            return new SttResponse(externalId, extractedText, "SUCCESS");

        } catch (Exception e) {
            System.err.println("❌ BOT STT ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("STT xatosi: " + e.getMessage());
        }
    }

    /**
     * Convert text to speech (TTS)
     */
    public String convertTextToSpeech(TtsRequest ttsRequest) {
        try {
            System.out.println("========== BOT TTS REQUEST STARTED ==========");
            System.out.println("📝 Text: " + ttsRequest.getText());
            System.out.println("🎵 Model: " + (ttsRequest.getModel() != null ? ttsRequest.getModel() : "lola"));

            String model = ttsRequest.getModel() != null ? ttsRequest.getModel() : "lola";
            boolean blocking = ttsRequest.isBlocking();

            // Call Uzbekvoice API for TTS
            JsonObject apiResponse = uzbekVoiceApiClient.textToSpeechWithModel(
                    ttsRequest.getText(),
                    model,
                    blocking
            );

            System.out.println("📨 Initial API Response: " + apiResponse.toString());

            String audioUrl = null;
            String ttsId = null;

            // Check if we got immediate response
            if (apiResponse.has("result") && apiResponse.get("result").isJsonObject()) {
                JsonObject result = apiResponse.get("result").getAsJsonObject();
                if (result.has("url")) {
                    audioUrl = result.get("url").getAsString();
                    System.out.println("✅ Audio URL received immediately");
                }
            }

            if (apiResponse.has("id")) {
                ttsId = apiResponse.get("id").getAsString();
                System.out.println("📌 TTS ID: " + ttsId);
            }

            // If no immediate audio URL, poll for status
            if (audioUrl == null && ttsId != null) {
                audioUrl = pollForTtsCompletion(ttsId);
            }

            if (audioUrl == null || audioUrl.isEmpty()) {
                System.err.println("❌ No audio URL obtained");
                throw new RuntimeException("Audio URL sini olishda xato");
            }

            System.out.println("✅ Audio URL: " + audioUrl);
            System.out.println("========== BOT TTS REQUEST COMPLETED ==========\n");

            return audioUrl;

        } catch (Exception e) {
            System.err.println("❌ BOT TTS ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("TTS xatosi: " + e.getMessage());
        }
    }

    /**
     * Poll for TTS completion with timeout
     */
    private String pollForTtsCompletion(String ttsId) {
        System.out.println("⏳ Polling for TTS completion...");
        int maxAttempts = 30; // 30 attempts
        int delayMs = 1000;   // 1 second between attempts

        for (int i = 0; i < maxAttempts; i++) {
            try {
                Thread.sleep(delayMs);

                // Check status
                JsonObject statusResponse = uzbekVoiceApiClient.checkTtsStatus(ttsId);

                if (statusResponse.has("status")) {
                    String status = statusResponse.get("status").getAsString();
                    System.out.println("📊 Attempt " + (i + 1) + "/" + maxAttempts + " - Status: " + status);

                    if ("SUCCESS".equals(status)) {
                        if (statusResponse.has("result") && statusResponse.get("result").isJsonObject()) {
                            JsonObject result = statusResponse.get("result").getAsJsonObject();
                            if (result.has("url")) {
                                String audioUrl = result.get("url").getAsString();
                                System.out.println("✅ Audio URL obtained after " + (i + 1) + " attempts");
                                return audioUrl;
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                System.err.println("❌ Polling interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("⚠️ Polling error: " + e.getMessage());
            }
        }

        System.err.println("❌ TTS polling timeout after " + maxAttempts + " attempts");
        throw new RuntimeException("Audio yaratish vaqti tugadi. Qayta urinib ko'ring");
    }

    /**
     * Download file from Telegram server
     */
    private String downloadTelegramFile(org.telegram.telegrambots.meta.api.objects.File telegramFile) {
        try {
            String filePath = telegramFile.getFilePath();
            System.out.println("📥 Downloading from Telegram: " + filePath);

            // Create temp file path
            String tempPath = System.getProperty("java.io.tmpdir") + "/" + System.currentTimeMillis() + ".ogg";

            System.out.println("💾 Saving to: " + tempPath);
            System.out.println("✅ File download simulated (actual download handled by MyBot)");

            return tempPath;
        } catch (Exception e) {
            System.err.println("❌ Download error: " + e.getMessage());
            return null;
        }
    }
}
