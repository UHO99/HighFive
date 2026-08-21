package com.mycom.myapp.team5.domain.coupon.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycom.myapp.team5.domain.coupon.dto.CouponRequest;
import com.mycom.myapp.team5.domain.coupon.dto.CouponResponse;
import com.mycom.myapp.team5.domain.coupon.dto.CouponSummary;
import com.mycom.myapp.team5.domain.coupon.dto.CouponUpdateRequest;
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
	 * A004: READY 상태 쿠폰의 재고/기간만 DB에서 수정한다. Redis는 갱신하지 않는다.
	 */
	@Override
	@Transactional
	public CouponResponse update(long couponId, CouponUpdateRequest request) {
		Coupon coupon = couponRepository.findById(couponId).orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

		if (coupon.getStatus() != CouponStatus.READY) {
			throw new CouponException(CouponErrorCode.COUPON_STATUS_CONFLICT);
		}

		LocalDateTime nextStartAt = request.startAt() != null ? request.startAt() : coupon.getStartAt();
		LocalDateTime nextEndAt = request.endAt() != null ? request.endAt() : coupon.getEndAt();
		validatePeriod(nextStartAt, nextEndAt);

		coupon.updateStockAndPeriod(request.totalQuantity(), request.startAt(), request.endAt());
		return CouponResponse.from(coupon);
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

	@Override
	public void validateIssueable(long couponId) {
		// 1) 쿠폰 존재 확인 -> 없으면 CP001
		Coupon coupon = couponRepository.findById(couponId).orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

		// 2) OPEN 상태가 아니면 발급 불가 -> CP002
		//		(Redis 키 유무로 간접 판정하던 기존 방식 대신 DB 상태를 직접 확인.
		//		 Redis 재시작 직후 재고 키가 아직 복구되지 않아도 OPEN 이면 발급 허용)
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
