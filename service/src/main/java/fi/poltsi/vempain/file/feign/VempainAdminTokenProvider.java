package fi.poltsi.vempain.file.feign;

import fi.poltsi.vempain.auth.api.request.LoginRequest;
import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
@Component
public class VempainAdminTokenProvider {

	private final VempainAdminLoginClient vempainAdminLoginClient;

	private String  jwtToken;
	private Instant jwtTokenRegistrationTimeExpires;

	@Value("${vempain.service.admin-backend-username}")
	private String adminUsername;

	@Value("${vempain.service.admin-backend-password}")
	private String adminUserPassword;

	public void login() {
		var loginRequest = LoginRequest.builder()
									   .login(adminUsername)
									   .password(adminUserPassword)
									   .build();
		log.info("Logging in to Vempain Admin service");
		var responseEntity = vempainAdminLoginClient.authenticateUser(loginRequest);

		if (responseEntity == null || !responseEntity.getStatusCode()
													 .is2xxSuccessful()) {
			log.error("Login to Vempain admin failed");
			throw new VempainAuthenticationException();
		}

		var loginResponse = responseEntity.getBody();

		if (loginResponse == null || loginResponse.getToken() == null) {
			log.error("Login response is invalid or token is missing");
			throw new VempainAuthenticationException();
		}

		jwtTokenRegistrationTimeExpires = Instant.now()
												 .plusSeconds(3_600L);
		jwtToken                        = loginResponse.getToken();

		log.info("Logged in to Vempain Admin");
	}

	public String getToken() {
		if (jwtToken == null
			|| jwtToken.isEmpty()
			|| jwtTokenRegistrationTimeExpires == null
			|| Instant.now()
					  .isAfter(jwtTokenRegistrationTimeExpires)) {
			log.info("JWT token is not set, logging in to Vempain Admin service");
			login();
		}

		return jwtToken;
	}
}
