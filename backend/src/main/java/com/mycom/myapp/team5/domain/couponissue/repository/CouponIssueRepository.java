package com.mycom.myapp.team5.domain.couponissue.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.mycom.myapp.team5.domain.couponissue.entity.CouponIssue;

public interface CouponIssueRepository extends JpaRepository<CouponIssue, Long> {

    long countByCouponId(Long couponId);

    @Transactional
    void deleteByCouponId(Long couponId);

    // S012/S013가 쿠폰마다 countByCouponId를 따로 호출하던 N+1을 없애기 위한 배치 집계 쿼리.
    @Query("SELECT ci.couponId AS couponId, COUNT(ci) AS issuedCount " +
            "FROM CouponIssue ci WHERE ci.couponId IN :couponIds GROUP BY ci.couponId")
    List<CouponIssueCount> countGroupedByCouponIds(@Param("couponIds") List<Long> couponIds);

    interface CouponIssueCount {
        Long getCouponId();
        Long getIssuedCount();
    }

    // (유스케이스 U003) 내 쿠폰 목록 - 최근 발급 순 정렬
    List<CouponIssue> findByUserIdOrderByIssuedAtDesc(Long userId);
    
    // (유스케이스 U003) 본인 소유 쿠폰 단건 - userId로 소유권까지 검증
    Optional<CouponIssue> findByIdAndUserId(Long id, Long userId);

    // "몇 번째로 발급받았는지" - 발급 id는 auto-increment라 오름차순 = 발급 순서. 같은 쿠폰에서
    // 자기 id 이하인 발급 건수를 세면 그게 곧 자기 순번이다.
    long countByCouponIdAndIdLessThanEqual(Long couponId, Long id);

    // (유스케이스 U004) 비관적 락으로 소유권 검증 + 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ci FROM CouponIssue ci WHERE ci.id = :id AND ci.userId = :userId")
    Optional<CouponIssue> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
