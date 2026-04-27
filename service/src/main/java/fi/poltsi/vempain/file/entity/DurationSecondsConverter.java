package fi.poltsi.vempain.file.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Persist Duration values as whole seconds (BigDecimal) to match NUMERIC(5,0) schema columns.
 * Clamps to [0..99999] to prevent numeric overflow.
 */
@Converter
public class DurationSecondsConverter implements AttributeConverter<Duration, BigDecimal> {

	private static final long MAX_SECONDS = 99_999L;

	@Override
	public BigDecimal convertToDatabaseColumn(Duration attribute) {
		if (attribute == null || attribute.isNegative()) {
			return BigDecimal.ZERO;
		}

		var seconds = attribute.toSeconds();
		if (seconds > MAX_SECONDS) {
			seconds = MAX_SECONDS;
		}

		return BigDecimal.valueOf(seconds);
	}

	@Override
	public Duration convertToEntityAttribute(BigDecimal dbData) {
		if (dbData == null || dbData.signum() < 0) {
			return Duration.ZERO;
		}
		return Duration.ofSeconds(dbData.longValue());
	}
}
