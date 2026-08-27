package com.mycom.myapp.team5.domain.monitoring.controller;

import com.mycom.myapp.team5.domain.coupon.dto.CouponConsistencyStatusResponse;
import com.mycom.myapp.team5.domain.monitoring.dto.MonitoringDashboardResponse;
import com.mycom.myapp.team5.domain.monitoring.service.MonitoringService;
import com.mycom.myapp.team5.global.aspect.LogDescription;
import com.mycom.myapp.team5.global.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "모니터링", description = "관리자 대시보드용 실시간 지표·정합성 상태 조회 및 운영 제어 API")
@RestController
@RequestMapping("/api/admin/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService monitoringService;

    @Operation(summary = "모니터링 대시보드 조회", description = "특정 쿠폰의 서버 리소스/API 응답/발급 파이프라인 등 실시간 모니터링 지표를 조회합니다.")
    @LogDescription("모니터링 대시보드 조회")
    @GetMapping("/coupons/{couponId}")

    public ResponseEntity<ApiResponse<MonitoringDashboardResponse>> getDashboard(@Parameter(description = "모니터링 대상 쿠폰 ID") @PathVariable long couponId) {

        return ResponseEntity.ok(ApiResponse.success(monitoringService.getDashboard(couponId)));
    }

    // couponId와 무관한 시스템 전체 상태라 별도 경로로 둔다 - 오픈된 쿠폰이 없어 위 getDashboard()가
    // 실패하는 동안에도 "정합성 동기화·검증" 카드는 계속 갱신되어야 하기 때문.
    @Operation(summary = "정합성 동기화/검증 상태 조회", description = "쿠폰과 무관한 시스템 전체 관점에서, 정합성 동기화(S012)·검증(S013) 배치의 최근 실행 상태와 불일치 이력을 조회합니다.")
    @LogDescription("정합성 동기화/검증 상태 조회")
    @GetMapping("/consistency-status")
    public ResponseEntity<ApiResponse<CouponConsistencyStatusResponse>> getConsistencyStatus() {
        return ResponseEntity.ok(ApiResponse.success(monitoringService.getConsistencyStatus()));
    }

    // 대시보드 지표만 0으로 초기화한다 - Redis/DB 실 데이터는 건드리지 않는다(MonitoringService 참고).
    @Operation(summary = "모니터링 지표 초기화", description = "대시보드에 표시되는 집계 지표만 0으로 초기화합니다. Redis/DB의 실제 데이터는 변경하지 않습니다.")
    @LogDescription("모니터링 지표 초기화")
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Void>> resetMetrics() {
        monitoringService.resetMetrics();
        return ResponseEntity.ok(ApiResponse.successNoData());
    }

    // 재시도해도 영원히 실패할 PEL을 강제로 비운다(DB에는 반영 안 됨) - 최후 수단, 명시적 호출 전용.
    @Operation(summary = "Stream PEL 강제 드레인", description = "재시도해도 영원히 실패하는 Redis Stream PEL(Pending Entries List)을 강제로 비웁니다. "
            + "대기 중이던 메시지는 DB에 반영되지 않고 그대로 버려지며 되돌릴 수 없는 최후 수단이므로 명시적으로 호출할 때만 사용해야 합니다.")
    @LogDescription("Stream PEL 강제 드레인")
    @PostMapping("/coupons/{couponId}/stream/drain")

    public ResponseEntity<ApiResponse<Integer>> drainPendingStream(@Parameter(description = "대상 쿠폰 ID") @PathVariable long couponId) {

        int acked = monitoringService.drainPendingStream(couponId);
        return ResponseEntity.ok(ApiResponse.success(acked));
    }
}
