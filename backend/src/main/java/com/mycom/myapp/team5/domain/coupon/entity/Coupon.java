package com.mycom.myapp.team5.domain.coupon.entity;

import com.mycom.myapp.team5.global.common.enums.CouponStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /**
     * 전체 발급 가능 수량
     */
    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    /**
     * 발급 캠페인 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * S012 정합성 동기화 배치가 채우는 실 발급 건수. null이면 아직 동기화되지 않은 상태.
     */
    @Column(name = "issued_quantity")
    private Integer issuedQuantity;

    /**
     * S013 정합성 검증 배치가 "드레인 완료 + 기록값=실측값"을 처음 확인한 시각.
     * null이면 아직 확정되지 않아 S013 재검증 대상. 한 번 채워지면 S013 대상에서 영구히 제외되고,
     * 이 시점에 해당 쿠폰의 Redis Stream/컨슈머 그룹도 함께 정리된다.
     */
    @Column(name = "consistency_confirmed_at")
    private LocalDateTime consistencyConfirmedAt;

    @Builder
    public Coupon(String name, Integer totalQuantity, LocalDateTime startAt, LocalDateTime endAt) {
        this.name = name;
        this.totalQuantity = totalQuantity;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = CouponStatus.READY;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * A004: 재고/기간 변경. null인 항목은 유지한다.
     */
    public void updateStockAndPeriod(Integer totalQuantity, LocalDateTime startAt, LocalDateTime endAt) {
        if (totalQuantity != null) {
            this.totalQuantity = totalQuantity;
        }
        if (startAt != null) {
            this.startAt = startAt;
        }
        if (endAt != null) {
            this.endAt = endAt;
        }
    }

    public void open() {
        this.status = CouponStatus.OPEN;
    }

    public void close() {
        this.status = CouponStatus.CLOSE;
    }

    public void syncIssuedQuantity(int issuedQuantity) {
        this.issuedQuantity = issuedQuantity;
    }

    public void confirmConsistency(LocalDateTime confirmedAt) {
        this.consistencyConfirmedAt = confirmedAt;
    }
}
