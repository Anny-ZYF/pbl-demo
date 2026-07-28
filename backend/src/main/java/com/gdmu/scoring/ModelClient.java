package com.gdmu.scoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

@Component
public class ModelClient {
    private static final Logger log = LoggerFactory.getLogger(ModelClient.class);
    private final RestClient client;
    private final String token;

    public ModelClient(ModelProperties properties, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        this.client = builder.baseUrl(properties.baseUrl()).requestFactory(factory).build();
        this.token = properties.token();
    }

    public Optional<ModelBatchResponse> predict(List<ModelItemRequest> items) {
        if (token == null || token.isBlank() || items.isEmpty()) {
            return Optional.empty();
        }
        try {
            ModelBatchResponse response = client.post().uri("/v1/predict")
                    .header("Authorization", "Bearer " + token)
                    .body(new ModelBatchRequest(items))
                    .retrieve().body(ModelBatchResponse.class);
            return Optional.ofNullable(response);
        } catch (RestClientException ex) {
            log.warn("Model service unavailable; deterministic rule fallback will be used: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public record ModelBatchRequest(List<ModelItemRequest> items) { }
    public record ModelItemRequest(String studentId, double contribution, int standardGroupSize,
                                   int actualGroupSize, double groupScore) { }
    public record ModelBatchResponse(String ruleVersion, String modelVersion, String artifactSha256,
                                     List<ModelItemResponse> items) { }
    public record ModelItemResponse(String studentId, double ruleScore, Double modelScore, double finalSuggestion,
                                    String mode, Double delta, boolean anomaly, String explanation) { }
}
