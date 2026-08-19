package ch.atexxi.chronivaro.rest.dto;

public record SystemMetricsDto(
		long heapUsedBytes,
		long heapMaxBytes,
		long heapFreeBytes,
		long nonHeapUsedBytes,
		int activeThreads,
		int peakThreadCount,
		long totalStartedThreadCount,
		int availableProcessors,
		double systemLoadAverage,
		long uptimeMs,
		String timestamp
) {
}
