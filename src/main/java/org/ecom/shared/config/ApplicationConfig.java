package org.ecom.shared.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

}
