package com.mycom.myapp.team5.global.common.enums;

import java.util.List;

import com.mycom.myapp.team5.domain.test.exception.K6ErrorCode;
import com.mycom.myapp.team5.domain.test.exception.K6TestException;

import lombok.Getter;

/**
 * backend/k6/*.js 에 실제로 존재하는 시나리오와 1:1로 대응한다({@code highfive-k6} 이미지에 그대로 구워넣는 파일들). id 는 프런트가 보내는 값이므로, 여기 정의된 값만 허용하는 화이트리스트 역할도 겸한다 (임의의 파일 경로를 클라이언트가 지정할 수 없게 막는 것이 목적).
 *
 * <p>
 * 새 스크립트를 추가하면 여기에도 상수를 추가해야 대시보드에 나타나고 실행할 수 있다 - 추가 후엔 {@code docker compose build k6}로 이미지도 다시 만들어야 한다.
 * </p>
 */
@Getter
public enum K6Scenario {

	/**
	 * API("api", "api_test.js", "기본 발급 API 부하테스트", "발급 엔드포인트의 요청 수락 속도를 측정하는 표준 시나리오.", "10s", "30s", "20,000 VU", false, false), REDIS("redis", "redis_test.js", "Redis Stream 파이프라인", "Redis Stream 기반 배치 insert 경로 대상. 램프업을 완만하게(30s) 잡아 초반 튐을 줄인다.", "30s", "30s", "20,000 VU", false, false), KAFKA("kafka", "kafka_test.js", "Kafka 비교 시나리오", "초기에 Kafka 기반 구현과 비교하려고 만든 시나리오 (현재 운영 경로는 Redis Stream).", "10s", "30s", "20,000 VU", false, false),
	 */
	SMALL_SCALE("small-scale", "concurrency_test.js", "간단 정합성 확인", //
			"재고의 2배를 한꺼번에 던져 초과 발급 여부를 빠르게 확인", //
			List.of("대상 쿠폰에 기존 발급 이력이 없어야 한다."), //
			"", "", "", true, false), //
	MAIN("main", "main_test.js", "메인 부하 테스트", //
			"초과 발급 0건과 1인 최대 1매를 조건을 바꿔가며 검증", //
			List.of(//
					"유입 방식 — 램프업, 균등, 한꺼번에", //
					"요청 배수 — 기본 2", //
					"연타 비율 — 기본 0"//
			), "기본 60초", "", "", true, true);

	private final String id;
	private final String file;
	private final String scenarioName;
	private final String description;
	/** 실행 전에 알면 좋은 값 기준 - 화면에서 설명 아래 목록으로 뿌린다. */
	private final List<String> guides;
	private final String rampUp;
	private final String hold;
	private final String targetVus;
	/** true면 프론트가 재고/동시접속 수(stock/maxVus)를 입력받아 K6RunRequest에 실어 보낸다. */
	private final boolean configurable;
	/** true면 요청배수·유입방식·연타 같은 추가 파라미터까지 입력받는다(main_test.js처럼 그 env var를 읽는 스크립트). */
	private final boolean advanced;

	K6Scenario(String id, String file, String scenarioName, String description, List<String> guides, String rampUp, String hold, String targetVus, boolean configurable, boolean advanced) {
		this.id = id;
		this.file = file;
		this.scenarioName = scenarioName;
		this.description = description;
		this.guides = guides;
		this.rampUp = rampUp;
		this.hold = hold;
		this.targetVus = targetVus;
		this.configurable = configurable;
		this.advanced = advanced;
	}

	public static K6Scenario fromId(String id) {
		for (K6Scenario scenario : values()) {
			if (scenario.id.equals(id)) {
				return scenario;
			}
		}
		throw new K6TestException(K6ErrorCode.UNKNOWN_SCENARIO);
	}
}
