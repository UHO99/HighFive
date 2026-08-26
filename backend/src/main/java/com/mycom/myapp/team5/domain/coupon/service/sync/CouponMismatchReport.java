package com.mycom.myapp.team5.domain.coupon.service.sync;

/**
 * S013 검증 배치가 쿠폰 한 건을 확인한 결과.
 *
 * @param couponId               검증 대상 쿠폰
 * @param recordedIssuedQuantity S012가 마지막으로 동기화해둔 값 (아직 한 번도 동기화 안 됐으면 null)
 * @param actualIssuedCount      이번 검증 시점에 coupon_issue를 다시 센 값 (ground truth)
 * @param pendingCount           이 쿠폰 스트림의 컨슈머 그룹 미처리(PEL) 건수
 * @param totalQuantity          쿠폰의 재고 수량 - 실측 이력이 이걸 넘으면 초과 발급
 */
public record CouponMismatchReport(
        long couponId,
        Integer recordedIssuedQuantity,
        long actualIssuedCount,
        long pendingCount,
        int totalQuantity
) {

    public boolean isMismatch() {
        // 초과 발급
        if (actualIssuedCount > totalQuantity) {
            return true;
        }
        if (pendingCount > 0) {
            // 아직 coupon_issue로 못 들어간 건이 남아있다 - 지금 세는 count는 최종값이 아니다.
            return true;
        }
        if (recordedIssuedQuantity == null) {
            // 스트림은 다 드레인됐는데 S012가 아직 동기화를 안 했다 - 지연/누락 의심.
            return true;
        }
        return recordedIssuedQuantity != actualIssuedCount;
    }

}
