package com.learning.services;

import com.learning.config.AppProperties;
import com.learning.exceptions.AIException;
import com.learning.model.BarCodeRequest;
import com.learning.model.DiscountDecision;
import com.learning.model.ImagePayload;
import com.learning.model.Question;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.*;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AIServiceImpl  implements AIService {

    private final OpenAiAudioSpeechModel openAiAudioSpeechModel;
    private final OpenAiAudioTranscriptionModel openAiAudioTranscriptionModel;
    private final ChatModel chatModel;
    private final AppProperties appProperties;
    private final BarCodeService barCodeService;

    public AIServiceImpl(OpenAiAudioSpeechModel openAiAudioSpeechModel,
                         OpenAiAudioTranscriptionModel openAiAudioTranscriptionModel,
                         ChatModel chatModel,
                         AppProperties appProperties,
                         BarCodeService barCodeService) {
        this.openAiAudioSpeechModel = openAiAudioSpeechModel;
        this.openAiAudioTranscriptionModel = openAiAudioTranscriptionModel;
        this.chatModel = chatModel;
        this.appProperties = appProperties;
        this.barCodeService = barCodeService;
    }

    @Override
    public byte[] convertToAudio(Question question) {

        OpenAiAudioSpeechOptions openAiAudioSpeechOptions = OpenAiAudioSpeechOptions.builder()
                .voice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY)
                .speed(1.0)
                .responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
                .model(OpenAiAudioApi.TtsModel.TTS_1.value)
                .build();

        String filteredText = filterText(question.question());
        TextToSpeechPrompt textToSpeechPrompt = new TextToSpeechPrompt(filteredText, openAiAudioSpeechOptions);
        TextToSpeechResponse textToSpeechResponse = openAiAudioSpeechModel.call(textToSpeechPrompt);

        return textToSpeechResponse.getResult().getOutput();
    }

    @Override
    public ImagePayload convertAudioToText(MultipartFile audioFile, String conversionType) {
        try {
            log.info("Converting audio file to text: {} with conversionType: {}",
                    audioFile.getOriginalFilename(), conversionType);

            // Create a ByteArrayResource from the uploaded file
            ByteArrayResource resource = new ByteArrayResource(audioFile.getBytes()) {
                @Override
                public String getFilename() {
                    return audioFile.getOriginalFilename();
                }
            };

            // Configure transcription options
            OpenAiAudioTranscriptionOptions.Builder optionsBuilder = OpenAiAudioTranscriptionOptions.builder()
                    .model("whisper-1")
                    .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT);

            // Set temperature to 0.0 for raw conversion to get more deterministic results
            if ("raw".equalsIgnoreCase(conversionType)) {
                optionsBuilder.temperature(0.0f);
            }

            OpenAiAudioTranscriptionOptions options = optionsBuilder.build();

            // Create transcription prompt and call the API
            AudioTranscriptionPrompt transcriptionPrompt = new AudioTranscriptionPrompt(resource, options);
            AudioTranscriptionResponse response = openAiAudioTranscriptionModel.call(transcriptionPrompt);

            String transcribedText = response.getResult().getOutput();
            log.info("Transcription completed successfully");

            // If conversionType is "polished", refine the text using ChatModel
            if ("polished".equalsIgnoreCase(conversionType)) {
                log.info("Polishing transcribed text");
                transcribedText = polishText(transcribedText);
            }

            DiscountDecision discountDecision = checkIfDiscount(transcribedText);
            log.info("Discount Decision: {}", discountDecision);
            if(discountDecision.isApplyDiscount()) {
                return barCodeService.generateDiscountCoupon(new BarCodeRequest("DISCOUNT2024"));
            } else {
                log.info("No discount needed based on the transcribed text");
            }
        } catch (IOException e) {
            log.error("Error reading audio file", e);
            throw new RuntimeException("Failed to read audio file: " + e.getMessage(), e);
        }
        return new ImagePayload("text/plain", "Transcribed Text does not indicate a discount is needed");
    }

    private String polishText(String rawText) {
        String polishPrompt = String.format(
            "Please improve the following transcribed text by:\n" +
            "1. Fixing any grammatical errors\n" +
            "2. Adding proper punctuation\n" +
            "3. Correcting capitalization\n" +
            "4. Improving sentence structure while maintaining the original meaning\n" +
            "5. Removing filler words (um, uh, like, etc.)\n\n" +
            "Original text:\n%s\n\n" +
            "Polished text:", rawText);

        String polishedText = chatModel.call(polishPrompt);
        log.info("Text polishing completed");
        return polishedText;
    }

    private String filterText(String input) {
        String result = input;
        for (String word : appProperties.getBlockedWords()) {
            result = result.replaceAll("(?i)\\b" + Pattern.quote(word) + "\\b", "****");
        }
        return result;
    }

    private DiscountDecision checkIfDiscount(String transcribedText) {
        BeanOutputConverter<DiscountDecision> converter = new BeanOutputConverter<>(DiscountDecision.class);
        String outputFormat = converter.getFormat();
        String discountPrompt = String.format(
                """
                        Based on the following transcribed text, determine if a Discount should be applied. \
                        Discount should be applied  if the text contains shopping / checkout / shipping functionality failure
                        Provide your answer in the following format:
                        %s
                        Transcribed text:
                        %s

                '""", transcribedText, outputFormat);

        String  response = chatModel.call(discountPrompt);

        if (response == null || response.isEmpty()) {
            throw new AIException("No response received from AI");
        } else {
            return converter.convert(response);
        }
    }
}
