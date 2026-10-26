package org.ecom.shared.config;

import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sends logs through OpenTelemetry (to Loki in local development), with the trace context of each log line.
 * Only when an OTLP logs endpoint is configured: in AWS, logs go to CloudWatch through the container's output.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("management.opentelemetry.logging.export.otlp.endpoint")
class OpenTelemetryLogsConfig {

    @Bean
    InitializingBean openTelemetryLogAppender(OpenTelemetry openTelemetry) {
        return () -> {
            var context = (LoggerContext) LoggerFactory.getILoggerFactory();
            var appender = new OpenTelemetryAppender();
            appender.setContext(context);
            appender.setName("OPEN_TELEMETRY");
            appender.setCaptureCodeAttributes(true);
            appender.start();
            context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(appender);
            OpenTelemetryAppender.install(openTelemetry);
        };
    }

}
