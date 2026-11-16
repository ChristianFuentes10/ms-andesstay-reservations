package cl.duoc.andesstay.reservations.exception;

import cl.duoc.andesstay.reservations.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;

import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Traduce las excepciones del dominio y del proxy hacia el catalogo en
 * respuestas JSON consistentes con ErrorResponse (mismo formato que el BFF
 * y ms-andesstay-audit), en vez de dejar pasar stack traces.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ReservationNotFoundException ex,
                                                          HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(), "not_found", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(InvalidReservationStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(InvalidReservationStateException ex,
                                                              HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.CONFLICT.value(), "conflict", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(UnitNotAvailableException.class)
    public ResponseEntity<ErrorResponse> handleUnitNotAvailable(UnitNotAvailableException ex,
                                                                  HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.CONFLICT.value(), "unit_not_available", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /** El microservicio de catalogo respondio con un error HTTP (4xx/5xx): se propaga el mismo status. */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleDownstreamHttpError(WebClientResponseException ex,
                                                                    HttpServletRequest request) {
        HttpStatusCode status = ex.getStatusCode();
        ErrorResponse body = ErrorResponse.of(
                status.value(),
                status.toString(),
                "El microservicio de catalogo respondio con un error: " + ex.getStatusText(),
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    /** No se pudo ni siquiera contactar al microservicio de catalogo (caido, DNS, timeout, etc). */
    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<ErrorResponse> handleDownstreamUnavailable(WebClientRequestException ex,
                                                                      HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_GATEWAY.value(),
                "bad_gateway",
                "No fue posible comunicarse con el microservicio de catalogo.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    /**
     * .block(Duration) de CatalogClient lanza esto (no WebClientRequestException)
     * cuando el timeout se agota sin que catalog responda. Sin este handler,
     * el timeout evita el cuelgue infinito pero termina en un 500 sin formato.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleBlockTimeout(IllegalStateException ex,
                                                              HttpServletRequest request) {
        if (ex.getMessage() == null || !ex.getMessage().contains("Timeout on blocking read")) {
            throw ex; // no es el timeout que esperamos: no lo escondamos
        }
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_GATEWAY.value(),
                "bad_gateway",
                "El microservicio de catalogo no respondio a tiempo.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    /** Cuerpo de la peticion invalido segun las anotaciones @NotNull/@Positive/@FutureOrPresent de los DTO. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "bad_request", message, request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }
}