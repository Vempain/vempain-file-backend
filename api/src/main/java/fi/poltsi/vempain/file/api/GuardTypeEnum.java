package fi.poltsi.vempain.file.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Type of location guard shape")
public enum GuardTypeEnum {
	@Schema(description = "Square/rectangle defined by two corner coordinates")
	SQUARE,
	@Schema(description = "Circle defined by center and radius point (secondary ignored)")
	CIRCLE
}

