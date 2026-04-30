package fi.poltsi.vempain.file.feign;

import fi.poltsi.vempain.auth.api.response.LoginResponse;
import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VempainAdminTokenProviderUTC {

    @Mock
    private VempainAdminLoginClient vempainAdminLoginClient;

    @InjectMocks
    private VempainAdminTokenProvider tokenProvider;

    @BeforeEach
    void injectFields() {
        ReflectionTestUtils.setField(tokenProvider, "adminUsername", "test-user");
        ReflectionTestUtils.setField(tokenProvider, "adminUserPassword", "test-pass");
    }

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        void login_success_storesToken() {
            var loginResponse = LoginResponse.builder().token("jwt-token-123").build();
            when(vempainAdminLoginClient.authenticateUser(any())).thenReturn(ResponseEntity.ok(loginResponse));
            tokenProvider.login();
            assertThat(tokenProvider.getToken()).isEqualTo("jwt-token-123");
        }

        @Test
        void login_nullResponse_throwsAuthException() {
            when(vempainAdminLoginClient.authenticateUser(any())).thenReturn(null);
            assertThrows(VempainAuthenticationException.class, () -> tokenProvider.login());
        }

        @Test
        void login_nonSuccessStatus_throwsAuthException() {
            when(vempainAdminLoginClient.authenticateUser(any())).thenReturn(ResponseEntity.badRequest().build());
            assertThrows(VempainAuthenticationException.class, () -> tokenProvider.login());
        }

        @Test
        void login_nullBodyInResponse_throwsAuthException() {
            when(vempainAdminLoginClient.authenticateUser(any())).thenReturn(ResponseEntity.ok(null));
            assertThrows(VempainAuthenticationException.class, () -> tokenProvider.login());
        }

        @Test
        void login_nullTokenInBody_throwsAuthException() {
            var loginResponse = LoginResponse.builder().token(null).build();
            when(vempainAdminLoginClient.authenticateUser(any())).thenReturn(ResponseEntity.ok(loginResponse));
            assertThrows(VempainAuthenticationException.class, () -> tokenProvider.login());
        }

        @Test
        void login_exceptionFromClient_throwsAuthException() {
            when(vempainAdminLoginClient.authenticateUser(any())).thenThrow(new RuntimeException("connection refused"));
            assertThrows(VempainAuthenticationException.class, () -> tokenProvider.login());
        }
    }

    @Nested
    @DisplayName("getToken()")
    class GetTokenTests {

        @Test
        void getToken_whenNoToken_callsLogin() {
            var loginResponse = LoginResponse.builder().token("fresh-token").build();
            when(vempainAdminLoginClient.authenticateUser(any())).thenReturn(ResponseEntity.ok(loginResponse));
            var token = tokenProvider.getToken();
            assertThat(token).isEqualTo("fresh-token");
            verify(vempainAdminLoginClient, times(1)).authenticateUser(any());
        }

        @Test
        void getToken_whenValidToken_doesNotRelogin() {
            var loginResponse = LoginResponse.builder().token("valid-token").build();
            when(vempainAdminLoginClient.authenticateUser(any())).thenReturn(ResponseEntity.ok(loginResponse));
            tokenProvider.getToken();
            var token = tokenProvider.getToken();
            assertThat(token).isEqualTo("valid-token");
            verify(vempainAdminLoginClient, times(1)).authenticateUser(any());
        }

        @Test
        void getToken_whenTokenExpired_callsLoginAgain() {
            var loginResponse = LoginResponse.builder().token("new-token").build();
            when(vempainAdminLoginClient.authenticateUser(any())).thenReturn(ResponseEntity.ok(loginResponse));
            tokenProvider.getToken();
            ReflectionTestUtils.setField(tokenProvider, "jwtTokenRegistrationTimeExpires",
                                         java.time.Instant.now().minusSeconds(10));
            var token = tokenProvider.getToken();
            assertThat(token).isEqualTo("new-token");
            verify(vempainAdminLoginClient, times(2)).authenticateUser(any());
        }
    }
}
