package com.mycom.myapp.team5.domain.couponissue.dto;

import java.time.LocalDateTime;

import com.mycom.myapp.team5.global.common.enums.CouponIssueStatus;

/**
 * 대시보드 "쿠폰 발급 이력 · 선착순" 카드용 - Redis fairness-log의 실제 원자적 처리 순서(rank)에
 * DB의 상태/시각을 얹은 항목. rank는 Redis Lua 스크립트 안에서 재고 차감과 함께 매겨지므로 실제
 * 처리 순서를 정확히 반영하고, DB 쪽(status/issuedAt)은 outcome이 SUCCESS여서 coupon_issue에
 * 실제로 적재된 건에만 채워진다(SOLDOUT/DUPLICATE는 애초에 DB 행이 없다).
 */
public record CouponFairnessTimelineEntry(
        /** Redis fairness-log seq - 이 쿠폰에서 몇 번째로 "처리"됐는지(성공/품절/중복 모두 포함) */
        long rank,
        long userId,
        /** Redis가 그 순간 기록한 처리 결과: SUCCESS / SOLDOUT / DUPLICATE */
        String outcome,
        /** outcome=SUCCESS일 때만 채워지는 DB 상태(ISSUED/USED/CANCELED/EXPIRED) - 그 외 null */
        CouponIssueStatus status,
        /** DB coupon_issue.issued_at - Stream 배치가 아직 안 넣었거나 outcome!=SUCCESS면 null */
        LocalDateTime issuedAt
) {
}
