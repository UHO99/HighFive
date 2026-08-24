package com.mycom.myapp.team5.domain.monitoring.controller;

import com.mycom.myapp.team5.domain.monitoring.dto.MonitoringDashboardResponse;
import com.mycom.myapp.team5.domain.monitoring.service.MonitoringService;
import com.mycom.myapp.team5.global.aspect.LogDescription;
import com.mycom.myapp.team5.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService monitoringService;

    @LogDescription("모니터링 대시보드 조회")
    @GetMapping("/coupons/{couponId}")
    public ResponseEntity<ApiResponse<MonitoringDashboardResponse>> getDashboard(@PathVariable long couponId) {
        return ResponseEntity.ok(ApiResponse.success(monitoringService.getDashboard(couponId)));
    }

    // 대시보드 지표만 0으로 초기화한다 - Redis/DB 실 데이터는 건드리지 않는다(MonitoringService 참고).
    @LogDescription("모니터링 지표 초기화")
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Void>> resetMetrics() {
        monitoringService.resetMetrics();
        return ResponseEntity.ok(ApiResponse.successNoData());
    }

    // 재시도해도 영원히 실패할 PEL을 강제로 비운다(DB에는 반영 안 됨) - 최후 수단, 명시적 호출 전용.
    @LogDescription("Stream PEL 강제 드레인")
    @PostMapping("/coupons/{couponId}/stream/drain")
    public ResponseEntity<ApiResponse<Integer>> drainPendingStream(@PathVariable long couponId) {
        int acked = monitoringService.drainPendingStream(couponId);
        return ResponseEntity.ok(ApiResponse.success(acked));
    }
}
