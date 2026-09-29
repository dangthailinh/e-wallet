package com.github.yildizmy.config;

import com.github.yildizmy.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@TestPropertySource(properties = {
        "management.endpoints.web.exposure.include=health,prometheus",
        "management.endpoint.health.show-details=never"
})
class ObservabilityIT extends PostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthIsPublicButDoesNotExposeComponentDetails() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"status\":\"UP\"").doesNotContain("components");
        assertThat(response.getHeaders()).containsKey("X-Request-ID");
    }

    @Test
    void prometheusEndpointExposesBusinessMetricsWithoutSensitiveConfiguration() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("ewallet_")
                .doesNotContain("jwt_secret", "inventory_keyring", "secret_key");
    }
}
