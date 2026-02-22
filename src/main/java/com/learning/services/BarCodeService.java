package com.learning.services;

import com.learning.exceptions.AIException;
import com.learning.model.BarCodeRequest;
import com.learning.model.ImagePayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;

@Slf4j
@Service
public class BarCodeService {

    @Value("${ninjas.api.key}")
    private String ninjaApiKey;

    private final RestClient restClient;

    public BarCodeService() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.api-ninjas.com/v1")
                .build();
    }

    public ImagePayload generateDiscountCoupon(BarCodeRequest request) {
        try {
            log.info("Invoking ninja api to generate bar code for : {}", request.barCodeText());
            String barCodeText;
            if(request.barCodeText().length() < 11) {
                barCodeText = String.format("%-11s", request.barCodeText()).replace(' ', '0');
            } else {
                barCodeText = request.barCodeText();
            }
            byte[] pngBytes = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/qrcode")
                            .queryParam("format", "png")
                            .queryParam("data", barCodeText)
                            .build())
                    .header("X-Api-Key", ninjaApiKey)
                    .header("Accept", "image/png")
                    .retrieve()
                    .body(byte[].class);

            if (pngBytes == null || pngBytes.length == 0) {
                throw new AIException("Empty response received from barcode API");
            }

            log.info("Successfully generated barcode PNG, size: {} bytes", pngBytes.length);
            String base64 = Base64.getEncoder().encodeToString(pngBytes);
            return new ImagePayload("image/png", base64);

        } catch (AIException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error while calling Ninja API for barcode generation: {}", e.getMessage());
            throw new AIException("Error while generating barcode: " + e.getMessage());
        }
    }
}
