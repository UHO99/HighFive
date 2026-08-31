package com.mycom.myapp.team5.domain.test.dto;

import java.util.List;

/**
 * k6 로그 파일의 "TOTAL RESULTS" 부분에서, 자주 쓰는 값만 정규식으로 뽑아 구조화한다. 원본 텍스트 줄(lines)도 함께 담아두되, 화면에는 metrics만 카드로 보여준다.
 */
public record K6SummaryResponse(boolean available, List<String> lines, Metrics metrics) {
	public static K6SummaryResponse unavailable() {
		return new K6SummaryResponse(false, List.of(), null);
	}

	public record Metrics(Double throughputPerSecond, Double totalDurationSeconds, Double iterationAvgMs, Double dataReceivedKb, Double dataSentKb) {
	}
}
