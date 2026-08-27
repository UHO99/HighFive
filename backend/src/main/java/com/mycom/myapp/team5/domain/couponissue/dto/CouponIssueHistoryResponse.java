package com.mycom.myapp.team5.domain.couponissue.dto;

import java.time.LocalDateTime;

import com.mycom.myapp.team5.domain.couponissue.entity.CouponIssue;
import com.mycom.myapp.team5.domain.user.entity.User;
import com.mycom.myapp.team5.global.common.enums.CouponIssueStatus;
import com.mycom.myapp.team5.global.common.util.MaskingUtils;

/**
 * 시나리오 7: 관리자용 — 특정 쿠폰의 발급 이력 한 건.
 * userName/userEmail은 MaskingUtils로 마스킹된 값만 담는다.
 */
public record CouponIssueHistoryResponse( //
		Long issueId, //
		Long userId, //
		String userEmail, // 마스킹된 이메일 (예: h***@example.com), 대상 유저를 못 찾으면 null
		String userName, // 마스킹된 이름 (예: 홍**), 대상 유저를 못 찾으면 null
		Long couponId, //
		CouponIssueStatus status, //
		LocalDateTime issuedAt, //
		LocalDateTime usedAt, //
		LocalDateTime canceledAt, //
		LocalDateTime expiredAt //
) {
	public static CouponIssueHistoryResponse from(CouponIssue issue, User user) {
		return new CouponIssueHistoryResponse( //
				issue.getId(), //
				issue.getUserId(), //
				user != null ? MaskingUtils.maskEmail(user.getEmail()) : null, //
				user != null ? MaskingUtils.maskName(user.getName()) : null, //
				issue.getCouponId(), //
				issue.getStatus(), //
				issue.getIssuedAt(), //
				issue.getUsedAt(), //
				issue.getCanceledAt(), //
				issue.getExpiredAt() //
		);
	}
}
