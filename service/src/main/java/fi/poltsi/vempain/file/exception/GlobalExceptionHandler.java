package fi.poltsi.vempain.file.exception;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
	public ResponseEntity<String> handleEntityNotFound(EntityNotFoundException ex) {
		log.warn("Entity not found: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
		                     .body(ex.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
		log.warn("Illegal argument: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
		                     .body(ex.getMessage());
	}
}

