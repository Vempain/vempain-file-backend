package fi.poltsi.vempain.file.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test (UTC) for the catch-all branch of {@link GlobalExceptionHandler}: an unexpected exception becomes a 500 problem detail that carries a correlation
 * id and never the exception message.
 */
class GlobalExceptionHandlerUTC {

    @Test
    void unexpectedExceptionsBecomeAnOpaqueInternalServerError() {
        var problem = new GlobalExceptionHandler().handleUnexpected(new IllegalStateException("database password is hunter2"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).isEqualTo("The request could not be completed");
        assertThat(problem.getProperties()).containsKey("correlation_id");
        assertThat(problem.getProperties()
            .get("correlation_id")
            .toString()).isNotBlank();
        assertThat(problem.toString()).doesNotContain("hunter2");
    }
}
