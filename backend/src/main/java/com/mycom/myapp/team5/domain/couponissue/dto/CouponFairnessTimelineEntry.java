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
        LocalDateTime issuedAt,
        /**
         * 컨트롤러 도달 → Redis 게이트 진입까지 걸린 시간(ms) - validateIssueable() 등 게이트 진입 전
         * 단계 소요. 시각 기록 추가 전에 쌓인 레거시 fairness-log 항목이면 null.
         */
        Long gateWaitMs,
        /**
         * Redis 게이트 진입 → Lua 스크립트 처리(원자적 재고 차감/기록)까지 걸린 시간(ms). 앱 서버와
         * Redis 서버 시계가 어긋나면 음수가 나올 수 있다. 레거시 항목이면 null.
         */
        Long redisWaitMs
) {
}
