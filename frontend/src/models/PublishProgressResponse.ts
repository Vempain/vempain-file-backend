import type {PublishProgressStatus} from './PublishProgressStatus';

export interface PublishProgressResponse {
	total_groups: number;
	scheduled: number;
	started: number;
	completed: number;
	failed: number;
	// JSON object with group id keys (strings) mapping to status values
	per_group_status: Record<string, PublishProgressStatus>;
	// ISO-8601 timestamp string
	last_updated: string;
}

