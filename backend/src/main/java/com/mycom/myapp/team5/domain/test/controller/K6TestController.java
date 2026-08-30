package com.mycom.myapp.team5.domain.test.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mycom.myapp.team5.domain.test.dto.K6RunRequest;
import com.mycom.myapp.team5.domain.test.dto.K6ScenarioResponse;
import com.mycom.myapp.team5.domain.test.dto.K6StatusResponse;
import com.mycom.myapp.team5.domain.test.dto.K6SummaryResponse;
import com.mycom.myapp.team5.domain.test.service.K6TestService;
import com.mycom.myapp.team5.global.aspect.LogDescription;
import com.mycom.myapp.team5.global.common.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "k6 부하테스트", description = "k6 시나리오 조회 및 부하테스트 실행/중지/상태 조회 API")
@RestController
@RequestMapping("/api/admin/k6")
@RequiredArgsConstructor
public class K6TestController {

	private final K6TestService k6TestService;

	@Operation(summary = "k6 시나리오 목록 조회", description = "실행 가능한 k6 부하테스트 시나리오 목록을 조회합니다. 화이트리스트인 K6Scenario enum에 정의된 것만 노출됩니다.")
	@LogDescription("k6 시나리오 목록 조회")
	@GetMapping("/scenarios")
	public ResponseEntity<ApiResponse<List<K6ScenarioResponse>>> listScenarios() {
		return ResponseEntity.ok(ApiResponse.success(k6TestService.listScenarios()));
	}

	@Operation(summary = "k6 부하테스트 실행", description = "선택한 시나리오로 k6 부하테스트를 실행합니다. 백엔드가 호스트 도커 소켓으로 k6 컨테이너를 띄우는 방식이라 즉시 202로 응답하고, 진행 상태는 상태 조회 API로 확인합니다.")
	@LogDescription("k6 부하테스트 실행")
	@PostMapping("/run")
	public ResponseEntity<ApiResponse<K6StatusResponse>> run(@Valid @RequestBody K6RunRequest request) {
		K6StatusResponse status = k6TestService.start(request);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(status));
	}

	@Operation(summary = "k6 부하테스트 중지", description = "현재 실행 중인 k6 부하테스트를 중지합니다.")
	@LogDescription("k6 부하테스트 중지")
	@PostMapping("/stop")
	public ResponseEntity<ApiResponse<K6StatusResponse>> stop() {
		return ResponseEntity.ok(ApiResponse.success(k6TestService.stop()));
	}

	@Operation(summary = "k6 부하테스트 상태 조회", description = "현재 k6 부하테스트의 실행 여부와 진행 상태를 조회합니다.")
	@LogDescription("k6 부하테스트 상태 조회")
	@GetMapping("/status")
	public ResponseEntity<ApiResponse<K6StatusResponse>> status() {
		return ResponseEntity.ok(ApiResponse.success(k6TestService.status()));
	}

	@Operation(summary = "k6 마지막 실행 요약 조회", description = "가장 최근에 끝난 k6 실행의 TOTAL RESULTS 요약을 조회합니다. 실행 중이거나 없으면 available=false입니다.")
	@LogDescription("k6 실행 결과 요약 조회")
	@GetMapping("/summary")
	public ResponseEntity<ApiResponse<K6SummaryResponse>> summary() {
		return ResponseEntity.ok(ApiResponse.success(k6TestService.summary()));
	}
}
