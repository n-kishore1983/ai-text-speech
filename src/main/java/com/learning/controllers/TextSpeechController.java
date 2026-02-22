package com.learning.controllers;

import com.learning.model.ImagePayload;
import com.learning.model.Question;
import com.learning.services.AIService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TextSpeechController {
    private final AIService aiService;

    public TextSpeechController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping(value = "/text-to-speech", produces = "audio/mpeg")
    public byte[] convertTextToSpeech(@RequestBody Question question) {
        return aiService.convertToAudio(question);
    }

    @PostMapping(value = "/speech-to-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.IMAGE_PNG_VALUE})
    public ResponseEntity<byte[]> convertSpeechToText(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "conversionType", defaultValue = "raw") String conversionType) {

        // Validate conversionType parameter
        if (!conversionType.equals("raw") && !conversionType.equals("polished")) {
            return ResponseEntity.badRequest()
                    .body("Invalid conversionType. Must be 'raw' or 'polished'".getBytes());
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("Please upload an audio file".getBytes());
        }

        try {
            ImagePayload transcribedText = aiService.convertAudioToText(file, conversionType);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"qrcode.png\"")
                    .body(Base64.getDecoder().decode(transcribedText.base64()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to transcribe audio: " .getBytes());
        }
    }
}
