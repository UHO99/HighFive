package com.mycom.myapp.team5.domain.couponissue.dto;

import com.mycom.myapp.team5.domain.couponissue.entity.CouponIssue;
import com.mycom.myapp.team5.domain.user.entity.User;
import com.mycom.myapp.team5.global.common.enums.CouponIssueStatus;
import com.mycom.myapp.team5.global.common.util.MaskingUtils;

import java.time.LocalDateTime;

/**
 * 시나리오 7: 관리자용 — 특정 쿠폰의 발급 이력 한 건.
 * userName/userEmail은 MaskingUtils로 마스킹된 값만 담는다.
 */
public record CouponIssueHistoryResponse(
        Long issueId,
        Long userId,
        Long couponId,
        CouponIssueStatus status,
        LocalDateTime issuedAt,
        LocalDateTime usedAt,
        LocalDateTime canceledAt,
        LocalDateTime expiredAt,
        String userName,
        String userEmail
) {
    public static CouponIssueHistoryResponse from(CouponIssue issue) {
        return from(issue, null);
    }

    public static CouponIssueHistoryResponse from(CouponIssue issue, User user) {
        return new CouponIssueHistoryResponse(
                issue.getId(),
                issue.getUserId(),
                issue.getCouponId(),
                issue.getStatus(),
                issue.getIssuedAt(),
                issue.getUsedAt(),
                issue.getCanceledAt(),
                issue.getExpiredAt(),
                user == null ? null : MaskingUtils.maskName(user.getName()),
                user == null ? null : MaskingUtils.maskEmail(user.getEmail())
        );
    }
}
