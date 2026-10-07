package cidrz.assessment.backend.web;

import cidrz.assessment.backend.config.LoggingContextConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request a correlation ID, returns it in the response header, and writes one access
 * log line. The ID is what a clinic quotes when they ring: "it failed at about ten past nine" is
 * not searchable, a correlation ID is.
 *
 * <p>A client-supplied ID is accepted only if it is short and plain, so a caller cannot inject
 * newlines or arbitrary text into the log stream. The access line deliberately omits the query
 * string and the body: {@code facility_id} is harmless but {@code patient_number} would not be, and
 * a rule that says "log the path, never the query" is one a reviewer can actually hold a change to.
 *
 * <p>A named logger rather than {@code @Slf4j}, so the access log can be routed and sampled
 * separately from application logging.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements WebFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("carelink.access");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String supplied = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = supplied != null && SAFE_ID.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();
        long startNanos = System.nanoTime();

        exchange.getAttributes().put(ATTRIBUTE, correlationId);
        exchange.getResponse().getHeaders().set(HEADER, correlationId);

        // beforeCommit runs for every response, including ones written by the error handler, so the
        // status logged here is always the status the caller actually received.
        exchange.getResponse().beforeCommit(() -> {
            logAccess(exchange, correlationId, startNanos);
            return Mono.empty();
        });

        return chain.filter(exchange)
                .contextWrite(Context.of(LoggingContextConfig.CORRELATION_ID, correlationId));
    }

    private static void logAccess(ServerWebExchange exchange, String correlationId, long startNanos) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        try (MDC.MDCCloseable ignored = MDC.putCloseable(LoggingContextConfig.CORRELATION_ID, correlationId)) {
            ACCESS_LOG.atInfo()
                    .addKeyValue("http.method", exchange.getRequest().getMethod().name())
                    .addKeyValue("url.path", exchange.getRequest().getPath().value())
                    .addKeyValue("http.status", status == null ? 200 : status.value())
                    .addKeyValue("duration_ms", (System.nanoTime() - startNanos) / 1_000_000)
                    .log("request completed");
        }
    }
}
