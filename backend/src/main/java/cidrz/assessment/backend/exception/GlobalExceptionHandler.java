package cidrz.assessment.backend.exception;

import cidrz.assessment.backend.common.ErrorResponse;
import cidrz.assessment.backend.config.LoggingContextConfig;
import cidrz.assessment.backend.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * The single place where any failure becomes an HTTP response, so every error leaves the service in
 * the same shape — including 401s raised by the auth filter and 404s for routes that do not exist.
 *
 * <p>Unexpected failures are logged in full with their stack trace; the caller gets a generic
 * message and the correlation ID. Internal detail never crosses the wire. Client errors are logged
 * at warn with the code only: the exception text can contain the rejected input, and the rejected
 * input can be patient data.
 */
@Component
@Order(-2) // ahead of Spring Boot's own error handler
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private static final byte[] FALLBACK_BODY =
            ("{\"error\":{\"code\":\"INTERNAL_ERROR\",\"message\":\"Something went wrong on the server.\","
                    + "\"correlation_id\":null}}").getBytes(StandardCharsets.UTF_8);

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            // Something already started writing; there is no status left to change.
            return Mono.error(ex);
        }

        String correlationId = exchange.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        MappedError mapped = mapException(ex, correlationId);
        logFailure(ex, mapped, correlationId);

        response.setStatusCode(HttpStatus.valueOf(mapped.status()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.fromCallable(() -> {
            byte[] body;
            try {
                body = objectMapper.writeValueAsBytes(mapped.response());
            } catch (RuntimeException serialisationFailure) {
                log.error("Could not serialise the error response", serialisationFailure);
                body = FALLBACK_BODY;
            }
            return response.bufferFactory().wrap(body);
        }));
    }

    private static MappedError mapException(Throwable ex, String correlationId) {
        return switch (ex) {

            case BaseException baseEx ->
                    new MappedError(baseEx.getHttpStatus(),
                            body(baseEx.getErrorCode(), baseEx.getMessage(), correlationId));

            // Malformed JSON, a missing body, or a path variable that will not convert.
            // The reason is not echoed back: it can quote the rejected input.
            case ServerWebInputException ignored ->
                    new MappedError(HttpStatus.BAD_REQUEST.value(),
                            body("VALIDATION_FAILED", "The request could not be read", correlationId));

            case MethodNotAllowedException methodEx ->
                    new MappedError(HttpStatus.METHOD_NOT_ALLOWED.value(),
                            body("METHOD_NOT_ALLOWED",
                                    "HTTP method " + methodEx.getHttpMethod() + " is not supported here",
                                    correlationId));

            case ResponseStatusException rsEx when rsEx.getStatusCode().value() == HttpStatus.NOT_FOUND.value() ->
                    new MappedError(HttpStatus.NOT_FOUND.value(),
                            body("NOT_FOUND", "No such endpoint", correlationId));

            case ResponseStatusException rsEx when rsEx.getStatusCode().is4xxClientError() ->
                    new MappedError(rsEx.getStatusCode().value(),
                            body("REQUEST_REJECTED", "The request was not accepted", correlationId));

            default ->
                    new MappedError(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                            body("INTERNAL_ERROR",
                                    "Something went wrong on the server. "
                                            + "Quote the correlation ID if you report this.",
                                    correlationId));
        };
    }

    private static ErrorResponse body(String code, String message, String correlationId) {
        return ErrorResponse.builder()
                .error(ErrorResponse.Body.builder()
                        .code(code)
                        .message(message)
                        .correlationId(correlationId)
                        .build())
                .build();
    }

    private static void logFailure(Throwable ex, MappedError mapped, String correlationId) {
        try (MDC.MDCCloseable ignored = correlationId == null
                ? null
                : MDC.putCloseable(LoggingContextConfig.CORRELATION_ID, correlationId)) {
            if (mapped.status() >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                log.atError().setCause(ex)
                        .addKeyValue("error.code", mapped.response().getError().getCode())
                        .log("request failed");
            } else {
                log.atWarn()
                        .addKeyValue("error.code", mapped.response().getError().getCode())
                        .addKeyValue("http.status", mapped.status())
                        .log("request rejected");
            }
        }
    }

    private record MappedError(int status, ErrorResponse response) {
    }
}
