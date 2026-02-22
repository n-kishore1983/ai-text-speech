package com.learning.model;

import org.springframework.web.multipart.MultipartFile;

public record VoiceMessage(MultipartFile audioFile) {
}
