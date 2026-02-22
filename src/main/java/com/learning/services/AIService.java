package com.learning.services;

import com.learning.model.ImagePayload;
import com.learning.model.Question;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;

public interface AIService {

    byte[] convertToAudio(Question question);

    ImagePayload convertAudioToText(MultipartFile audioFile, String conversionType);
}
