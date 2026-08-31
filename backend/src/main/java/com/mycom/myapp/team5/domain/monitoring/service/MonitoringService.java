package com.mycom.myapp.team5.domain.monitoring.service;

import com.mycom.myapp.team5.domain.coupon.dto.CouponConsistencyStatusResponse;
import com.mycom.myapp.team5.domain.monitoring.dto.MonitoringDashboardResponse;

public interface MonitoringService {

    MonitoringDashboardResponse getDashboard(long couponId);

    /**
     * 동기화(CouponStockSyncService)/검증(CouponStockValidationService) 배치가 지금 살아서 도는지 -
     * couponId와 무관한 시스템 전체 상태라 getDashboard()와 별도로 독립 조회한다. 특정 쿠폰이 없어서
     * getDashboard()가 실패하는 동안에도 이 값은 계속 갱신되어야 하기 때문이다.
     */
    CouponConsistencyStatusResponse getConsistencyStatus();

    /**
     * 대시보드 지표(HTTP/발급/DB insert 집계)만 0으로 되돌린다.
     * Redis 재고, Stream, DB의 coupon/coupon_issue 실 데이터는 건드리지 않는다 - 그 데이터를 읽어서
     * 보여주기만 할 뿐, 이 메서드가 초기화하는 대상이 아니다.
     */
    void resetMetrics();

    /**
     * couponId 스트림의 PEL을 강제로 비운다(DB에는 반영 안 됨) - 재시도해도 영원히 실패할 메시지를
     * 관리자가 명시적으로 포기하는 최후 수단.
     * {@link com.mycom.myapp.team5.global.redis.CouponStreamPendingDrainer} 참고.
     *
     * @return 실제로 ACK된 건수
     */
    int drainPendingStream(long couponId);

}
