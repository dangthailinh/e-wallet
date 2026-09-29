package com.github.yildizmy.inventory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InventoryCryptoProperties.class)
class InventoryCryptoConfiguration {

    @Bean
    InventoryCryptoService inventoryCryptoService(InventoryCryptoProperties properties) {
        return new InventoryCryptoService(properties);
    }
}
