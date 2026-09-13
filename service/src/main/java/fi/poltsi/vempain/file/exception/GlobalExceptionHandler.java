package fi.poltsi.vempain.file.exception;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.UUID;

/**
 * Global exception handler that maps domain exceptions to appropriate HTTP
 * status codes.
 *
 * <ul>
 *   <li>{@link EntityNotFoundException} → 404 Not Found</li>
 *   <li>{@link IllegalArgumentException} → 400 Bad Request</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(EntityNotFoundException.class)
	public ProblemDetail handleEntityNotFound(EntityNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "The requested entity was not found", ex);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
		return problem(HttpStatus.BAD_REQUEST, "The request was invalid", ex);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
		return problem(HttpStatus.valueOf(ex.getStatusCode()
		                                    .value()), "The request could not be completed", ex);
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
	public ProblemDetail handleInvalidRequest(Exception ex) {
		return problem(HttpStatus.BAD_REQUEST, "The request was invalid", ex);
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) {
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "The request could not be completed", ex);
	}

	private ProblemDetail problem(HttpStatus status, String detail, Exception exception) {
		var correlationId = UUID.randomUUID()
		                        .toString();
		log.warn("Request failed with correlation id {}: {}", correlationId, exception.getMessage(), exception);
		var problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("about:blank"));
		problem.setProperty("correlation_id", correlationId);
		return problem;
	}
}
