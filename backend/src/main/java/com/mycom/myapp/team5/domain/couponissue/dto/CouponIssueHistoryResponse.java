package com.mycom.myapp.team5.domain.couponissue.dto;

import com.mycom.myapp.team5.domain.couponissue.entity.CouponIssue;
import com.mycom.myapp.team5.global.common.enums.CouponIssueStatus;

import java.time.LocalDateTime;

/**
 * 시나리오 7: 관리자용 — 특정 쿠폰의 발급 이력 한 건.
 */
public record CouponIssueHistoryResponse(
        Long issueId,
        Long userId,
        Long couponId,
        CouponIssueStatus status,
        LocalDateTime issuedAt,
        LocalDateTime usedAt,
        LocalDateTime canceledAt,
        LocalDateTime expiredAt
) {
    public static CouponIssueHistoryResponse from(CouponIssue issue) {
        return new CouponIssueHistoryResponse(
                issue.getId(),
                issue.getUserId(),
                issue.getCouponId(),
                issue.getStatus(),
                issue.getIssuedAt(),
                issue.getUsedAt(),
                issue.getCanceledAt(),
                issue.getExpiredAt()
        );
    }
}
