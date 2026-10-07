package cidrz.assessment.backend.config;

import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;

/**
 * Reactive code hops between threads, so a thread-local logging context (the MDC) does not follow a
 * request on its own. With {@code spring.reactor.context-propagation=auto}, Reactor restores this
 * accessor's value from the Reactor Context around each operator, so every log line written inside a
 * request carries its correlation ID — which is what makes a clinic's "it failed at about ten past
 * nine" traceable.
 */
@Configuration
public class LoggingContextConfig {

    public static final String CORRELATION_ID = "correlationId";

    @PostConstruct
    void registerCorrelationIdPropagation() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(
                CORRELATION_ID,
                () -> MDC.get(CORRELATION_ID),
                value -> MDC.put(CORRELATION_ID, value),
                () -> MDC.remove(CORRELATION_ID));
    }
}
