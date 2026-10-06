package fi.poltsi.vempain.file.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Long-running actions of the file backend that run as background tasks. The task progress facility itself is type agnostic
 * (it carries the type as a string) so that it can be extracted into a shared component; this enum documents the types this
 * service emits and the result payload each one attaches to the finished task.
 */
@Schema(description = "Background task types emitted by the file backend",
		allowableValues = {"PUBLISH_FILE_GROUP", "PUBLISH_ALL_FILE_GROUPS", "SCAN_DIRECTORIES", "PUBLISH_MUSIC_DATA", "PUBLISH_GPS_TIME_SERIES",
						   "TAG_REMOVE_FROM_ALL", "TAG_REPLACE_ACROSS_ALL", "TAG_RENAME_ACROSS_ALL"})
public enum TaskTypeEnum {
	/**
	 * Uploads every file of one file group to the admin backend. No result payload.
	 */
	PUBLISH_FILE_GROUP,
	/**
	 * Uploads every file group the caller may modify to the admin backend. No result payload.
	 */
	PUBLISH_ALL_FILE_GROUPS,
	/**
	 * Scans original and/or export directories. Result payload: {@code ScanResponses}.
	 */
	SCAN_DIRECTORIES,
	/**
	 * Generates and publishes the music data set. Result payload: admin {@code DataResponse}.
	 */
	PUBLISH_MUSIC_DATA,
	/**
	 * Generates and publishes a GPS time series data set. Result payload: admin {@code DataResponse}.
	 */
	PUBLISH_GPS_TIME_SERIES,
	/**
	 * Removes a tag from every tagged file (rewrites file metadata). No result payload.
	 */
	TAG_REMOVE_FROM_ALL,
	/**
	 * Replaces a tag on every tagged file (rewrites file metadata). No result payload.
	 */
	TAG_REPLACE_ACROSS_ALL,
	/**
	 * Renames a tag on every tagged file (rewrites file metadata). No result payload.
	 */
	TAG_RENAME_ACROSS_ALL
}
