package com.mycom.myapp.team5.domain.test.controller;

import com.mycom.myapp.team5.domain.test.dto.K6RunRequest;
import com.mycom.myapp.team5.domain.test.dto.K6ScenarioResponse;
import com.mycom.myapp.team5.domain.test.dto.K6StatusResponse;
import com.mycom.myapp.team5.domain.test.service.K6TestService;
import com.mycom.myapp.team5.global.aspect.LogDescription;
import com.mycom.myapp.team5.global.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/k6")
@RequiredArgsConstructor
public class K6TestController {

    private final K6TestService k6TestService;

    @LogDescription("k6 시나리오 목록 조회")
    @GetMapping("/scenarios")
    public ResponseEntity<ApiResponse<List<K6ScenarioResponse>>> listScenarios() {
        return ResponseEntity.ok(ApiResponse.success(k6TestService.listScenarios()));
    }

    @LogDescription("k6 부하테스트 실행")
    @PostMapping("/run")
    public ResponseEntity<ApiResponse<K6StatusResponse>> run(@Valid @RequestBody K6RunRequest request) {
        K6StatusResponse status = k6TestService.start(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(status));
    }

    @LogDescription("k6 부하테스트 중지")
    @PostMapping("/stop")
    public ResponseEntity<ApiResponse<K6StatusResponse>> stop() {
        return ResponseEntity.ok(ApiResponse.success(k6TestService.stop()));
    }

    @LogDescription("k6 부하테스트 상태 조회")
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<K6StatusResponse>> status() {
        return ResponseEntity.ok(ApiResponse.success(k6TestService.status()));
    }

}
