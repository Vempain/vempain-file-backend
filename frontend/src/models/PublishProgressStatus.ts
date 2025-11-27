export const PublishProgressStatus = {
	SCHEDULED: 'SCHEDULED' as const,
	STARTED: 'STARTED' as const,
	COMPLETED: 'COMPLETED' as const,
	FAILED: 'FAILED' as const
};

export type PublishProgressStatus = typeof PublishProgressStatus[keyof typeof PublishProgressStatus];

