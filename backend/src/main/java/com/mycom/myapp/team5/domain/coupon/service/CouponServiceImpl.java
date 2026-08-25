package com.mycom.myapp.team5.domain.coupon.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import com.mycom.myapp.team5.domain.coupon.dto.*;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.global.redis.CouponStockRedisService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

	private final CouponRepository couponRepository;
	private final CouponStockRedisService couponStockRedisService;
	private final CouponIssueRepository couponIssueRepository;

	@Override
	public CouponResponse getExampleById(Long id) {
		return CouponResponse.from(Coupon.builder().name("example").totalQuantity(0).build());
	}

	/**
	 * A003: DB에만 쿠폰을 생성한다. Redis 재고 적재(initStock)는 OPEN 스케줄 시점에 수행한다.
	 */
	@Override
	@Transactional
	public CouponResponse create(CouponRequest request) {
		validatePeriod(request.startAt(), request.endAt());

		Coupon coupon = Coupon.builder().name(request.name()).totalQuantity(request.totalQuantity()).startAt(request.startAt()).endAt(request.endAt()).build();

		return CouponResponse.from(couponRepository.save(coupon));
	}

	/**
	 * A004: READY 상태 쿠폰의 재고/기간을 DB에서 수정한다. OPEN 상태는 마감 예약(endAt)만 허용한다
	 * (재고/시작시각을 같이 바꾸려는 요청은 CP003). Redis는 갱신하지 않는다.
	 */
	@Override
	@Transactional
	public CouponResponse update(long couponId, CouponUpdateRequest request) {
		Coupon coupon = couponRepository.findById(couponId).orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

		if (coupon.getStatus() == CouponStatus.READY) {
			LocalDateTime nextStartAt = request.startAt() != null ? request.startAt() : coupon.getStartAt();
			LocalDateTime nextEndAt = request.endAt() != null ? request.endAt() : coupon.getEndAt();
			validatePeriod(nextStartAt, nextEndAt);
			coupon.updateStockAndPeriod(request.totalQuantity(), request.startAt(), request.endAt());
			return CouponResponse.from(coupon);
		}

		if (coupon.getStatus() == CouponStatus.OPEN) {
			if (request.totalQuantity() != null || request.startAt() != null) {
				throw new CouponException(CouponErrorCode.COUPON_STATUS_CONFLICT);
			}
			validatePeriod(coupon.getStartAt(), request.endAt());
			coupon.updateStockAndPeriod(null, null, request.endAt());
			return CouponResponse.from(coupon);
		}

		throw new CouponException(CouponErrorCode.COUPON_STATUS_CONFLICT);
	}

	@Override
	@Transactional(readOnly = true)
	public CouponResponse getCoupon(long couponId) {
		Coupon coupon = couponRepository.findById(couponId).orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
		return CouponResponse.from(coupon);
	}

	private void validatePeriod(LocalDateTime startAt, LocalDateTime endAt) {
		if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
			throw new CouponException(CouponErrorCode.COUPON_INVALID_PERIOD);
		}
	}

	@Override
	@Transactional
	public int decreaseStockBatch(long couponId, int requestedCount) {
		if (requestedCount <= 0) {
			return 0;
		}

		Coupon coupon = couponRepository.findByIdForUpdate(couponId).orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

		int granted = Math.min(coupon.getTotalQuantity(), requestedCount);
		if (granted > 0) {
			couponRepository.decreaseStockBy(couponId, granted);
		}
		return granted;
	}

	/**
	 * U001: 쿠폰 목록 조회.
	 */
	@Override
	@Transactional(readOnly = true)
	public List<CouponResponse> getCoupons() {
		return couponRepository.findAll().stream()
				.map(CouponResponse::from)
				.toList();
	}

	/**
	 * A005: 단건 현황 (DB 발급 건수 + Redis 잔여).
	 */
	@Override
	@Transactional(readOnly = true)
	public CouponOverviewResponse getOverview(long couponId) {
		Coupon coupon = couponRepository.findById(couponId)
				.orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
		return toOverview(coupon);
	}

	/**
	 * A005: 전체 현황 목록.
	 */
	@Override
	@Transactional(readOnly = true)
	public List<CouponOverviewResponse> getOverviews() {
		return couponRepository.findAll().stream()
				.map(this::toOverview)
				.toList();
	}

	private CouponOverviewResponse toOverview(Coupon coupon) {
		long issued = resolveIssuedQuantity(coupon);
		Integer redisRemaining = couponStockRedisService.getStock(coupon.getId());
		return CouponOverviewResponse.of(coupon, issued, redisRemaining);
	}

	private long resolveIssuedQuantity(Coupon coupon) {
		if (coupon.getIssuedQuantity() != null) {
			return coupon.getIssuedQuantity();
		}
		return couponIssueRepository.countByCouponId(coupon.getId());
	}

	@Override
	@Transactional(readOnly = true)
	public void validateIssueable(long couponId) {
		Coupon coupon = couponRepository.findById(couponId)
				.orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

		if (coupon.getStatus() != CouponStatus.OPEN) {
			throw new CouponException(CouponErrorCode.COUPON_NOT_OPEN);
		}
	}


	@Override
	public List<CouponSummary> listAll() {
		return couponRepository.findAll().stream().sorted(Comparator.comparing(Coupon::getId)).map(CouponSummary::from).toList();
	}

	@Override
	public List<CouponSummary> listByStatus(CouponStatus status) {
		return couponRepository.findByStatus(status).stream().sorted(Comparator.comparing(Coupon::getId)).map(CouponSummary::from).toList();
	}

}
