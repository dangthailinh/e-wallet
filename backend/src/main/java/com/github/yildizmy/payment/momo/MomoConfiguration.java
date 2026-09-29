package com.github.yildizmy.payment.momo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.payment.momo", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MomoProperties.class)
class MomoConfiguration {

    @Bean
    MomoClient momoClient(MomoProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return new MomoClient(RestClient.builder().requestFactory(requestFactory).build(), properties);
    }

    @Bean
    MomoPaymentProvider momoPaymentProvider(
            MomoClient client,
            MomoProperties properties,
            ObjectMapper objectMapper
    ) {
        return new MomoPaymentProvider(client, properties, objectMapper);
    }
}
