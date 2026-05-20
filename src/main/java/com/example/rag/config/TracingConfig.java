
package com.example.rag.config;

import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TracingConfig {

    @Bean
    public Sampler sampler() {
        return Sampler.ALWAYS_SAMPLE;
    }

    @Bean
    public CurrentTraceContext currentTraceContext() {
        return ThreadLocalCurrentTraceContext.newBuilder()
                .addScopeDecorator((context, scope) -> {
                    if (context != null) {
                        MDC.put("traceId", context.traceIdString());
                        MDC.put("spanId", context.spanIdString());
                    }
                    return () -> {
                        try {
                            scope.close();
                        } finally {
                            MDC.remove("traceId");
                            MDC.remove("spanId");
                        }
                    };
                })
                .build();
    }
}
