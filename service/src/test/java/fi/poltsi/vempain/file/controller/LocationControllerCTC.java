package fi.poltsi.vempain.file.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Test Class (CTC) for {@link LocationController}.
 *
 * <p>Tests all REST endpoints declared in {@code LocationAPI}:
 * <ul>
 *   <li>GET    /api/location/{id}              – get GPS location by id</li>
 *   <li>GET    /api/location/guard             – list all location guards</li>
 *   <li>GET    /api/location/guard/{id}        – check if GPS location is guarded</li>
 *   <li>POST   /api/location/guard             – create location guard</li>
 *   <li>PUT    /api/location/guard             – update location guard</li>
 *   <li>DELETE /api/location/guard/{id}        – delete location guard</li>
 * </ul>
 *
 * <p>Note: {@code LocationAPI.GUARD_PATH = "/location/guard"} (singular).
 */
class LocationControllerCTC extends AbstractControllerCTC {

	@BeforeEach
	void cleanLocationData() {
		jdbcTemplate.execute("TRUNCATE TABLE location_guard RESTART IDENTITY CASCADE");
		jdbcTemplate.execute("TRUNCATE TABLE gps_locations RESTART IDENTITY CASCADE");
	}

	// -----------------------------------------------------------------------
	// GET /api/location/{id}
	// -----------------------------------------------------------------------

	@Test
	void getLocationById_returns404_whenLocationDoesNotExist() throws Exception {
		doGet("/location/99999")
				.andExpect(status().isNotFound());
	}

	@Test
	void getLocationById_returns200_whenLocationExists() throws Exception {
		jdbcTemplate.update(
				"INSERT INTO gps_locations (latitude, latitude_ref, longitude, longitude_ref) VALUES (60.17000, 'N', 24.93500, 'E')");
		Long gpsId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM gps_locations", Long.class);

		doGet("/location/" + gpsId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(gpsId.intValue())))
				.andExpect(jsonPath("$.latitude", notNullValue()));
	}

	// -----------------------------------------------------------------------
	// GET /api/location/guard
	// -----------------------------------------------------------------------

	@Test
	void findAllLocationGuards_returnsEmptyList_whenNoGuardsExist() throws Exception {
		doGet("/location/guard")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void findAllLocationGuards_returnsList_whenGuardsExist() throws Exception {
		jdbcTemplate.update(
				"INSERT INTO location_guard (guard_type, primary_longitude, primary_latitude, secondary_longitude, secondary_latitude) VALUES ('SQUARE', 24.90000, 60.10000, 24.95000, 60.15000)");
		jdbcTemplate.update(
				"INSERT INTO location_guard (guard_type, primary_longitude, primary_latitude, radius) VALUES ('CIRCLE', 24.93500, 60.17000, 200.00000)");

		doGet("/location/guard")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$", hasSize(2)));
	}

	// -----------------------------------------------------------------------
	// GET /api/location/guard/{gpsLocationId}
	// -----------------------------------------------------------------------

	@Test
	void isGuardedLocation_returnsFalse_whenLocationDoesNotExist() throws Exception {
		doGet("/location/guard/99999")
				.andExpect(status().isOk())
				.andExpect(content().string("false"));
	}

	@Test
	void isGuardedLocation_returnsFalse_whenNoGuardCoversLocation() throws Exception {
		jdbcTemplate.update(
				"INSERT INTO gps_locations (latitude, latitude_ref, longitude, longitude_ref) VALUES (1.00000, 'N', 1.00000, 'E')");
		Long gpsId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM gps_locations", Long.class);
		// No guards exist → returns false
		doGet("/location/guard/" + gpsId)
				.andExpect(status().isOk())
				.andExpect(content().string("false"));
	}

	@Test
	void isGuardedLocation_returnsTrue_whenSquareGuardCoversLocation() throws Exception {
		// GPS point at 60.12 N, 24.92 E — falls inside the square below
		jdbcTemplate.update(
				"INSERT INTO gps_locations (latitude, latitude_ref, longitude, longitude_ref) VALUES (60.12000, 'N', 24.92000, 'E')");
		Long gpsId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM gps_locations", Long.class);

		// SQUARE guard: (60.10,24.90) → (60.15,24.95)
		jdbcTemplate.update(
				"INSERT INTO location_guard (guard_type, primary_longitude, primary_latitude, secondary_longitude, secondary_latitude) VALUES ('SQUARE', 24.90000, 60.10000, 24.95000, 60.15000)");

		doGet("/location/guard/" + gpsId)
				.andExpect(status().isOk())
				.andExpect(content().string("true"));
	}

	// -----------------------------------------------------------------------
	// POST /api/location/guard  (create)
	// -----------------------------------------------------------------------

	@Test
	void addLocationGuard_circleType_returns200() throws Exception {
		String body = """
				{
				  "guard_type": "CIRCLE",
				  "primary_coordinate": {"latitude": 60.17, "longitude": 24.94},
				  "radius": 200
				}
				""";
		doPost("/location/guard", body)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andExpect(jsonPath("$.guard_type", is("CIRCLE")));
	}

	@Test
	void addLocationGuard_squareType_returns200() throws Exception {
		String body = """
				{
				  "guard_type": "SQUARE",
				  "primary_coordinate": {"latitude": 60.10, "longitude": 24.90},
				  "secondary_coordinate": {"latitude": 60.15, "longitude": 24.95}
				}
				""";
		doPost("/location/guard", body)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andExpect(jsonPath("$.guard_type", is("SQUARE")));
	}

	@Test
	void addLocationGuard_returns400_whenGuardTypeIsMissing() throws Exception {
		// guard_type is @NotNull
		String body = """
				{
				  "primary_coordinate": {"latitude": 60.17, "longitude": 24.94}
				}
				""";
		doPost("/location/guard", body)
				.andExpect(status().isBadRequest());
	}

	// -----------------------------------------------------------------------
	// PUT /api/location/guard  (update)
	// -----------------------------------------------------------------------

	@Test
	void updateLocationGuard_returns200_whenGuardExistsAndRequestIsValid() throws Exception {
		jdbcTemplate.update(
				"INSERT INTO location_guard (guard_type, primary_longitude, primary_latitude, secondary_longitude, secondary_latitude) VALUES ('SQUARE', 24.90000, 60.10000, 24.95000, 60.15000)");
		Long guardId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM location_guard", Long.class);

		String updateBody = """
				{
				  "id": %d,
				  "guard_type": "CIRCLE",
				  "primary_coordinate": {"latitude": 60.17, "longitude": 24.94},
				  "radius": 150
				}
				""".formatted(guardId);

		doPut("/location/guard", updateBody)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(guardId.intValue())))
				.andExpect(jsonPath("$.guard_type", is("CIRCLE")));
	}

	@Test
	void updateLocationGuard_returns400_whenIdIsNull() throws Exception {
		// id=null → LocationService throws IllegalArgumentException → GlobalExceptionHandler → 400
		String body = """
				{
				  "guard_type": "CIRCLE",
				  "primary_coordinate": {"latitude": 60.17, "longitude": 24.94},
				  "radius": 100
				}
				""";
		doPut("/location/guard", body)
				.andExpect(status().isBadRequest());
	}

	// -----------------------------------------------------------------------
	// DELETE /api/location/guard/{id}
	// -----------------------------------------------------------------------

	@Test
	void deleteLocationGuard_returns204_whenGuardExists() throws Exception {
		jdbcTemplate.update(
				"INSERT INTO location_guard (guard_type, primary_longitude, primary_latitude, radius) VALUES ('CIRCLE', 24.93500, 60.17000, 100.00000)");
		Long guardId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM location_guard", Long.class);

		doDelete("/location/guard/" + guardId)
				.andExpect(status().isNoContent());
	}

	@Test
	void deleteLocationGuard_returns204_whenGuardDoesNotExist() throws Exception {
		// LocationService logs a warning but still returns normally (no exception)
		doDelete("/location/guard/99999")
				.andExpect(status().isNoContent());
	}
}

