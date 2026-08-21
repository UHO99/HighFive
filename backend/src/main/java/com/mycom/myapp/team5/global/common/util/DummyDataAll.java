package com.mycom.myapp.team5.global.common.util;

import com.mycom.myapp.team5.domain.test.controller.DummyDataController;

import javax.sql.DataSource;

/**
 * 더미데이터 일괄 생성
 * users → coupon/coupon_issue 순서로 실행한다.
 * coupon_issue 가 users 를 FK 로 참조하므로 이 순서를 지켜야 한다.
 * IntelliJ에서 main()으로 단독 실행할 수도 있고, {@link DummyDataController}처럼 Spring
 * 컨텍스트 안에서 {@link #run(DataSource)}로 호출할 수도 있다.
 * 사전 조건
 * 1) 테이블이 있는 상태에서 실행
 * ex)한 번 실행해 Flyway 로 테이블을 만든 뒤 종료
 *    (서버가 켜진 채로 단독 실행하면 안 됨 — 백그라운드 스케줄러가 같은 테이블을
 *     동시에 건드려 TRUNCATE 와 충돌할 수 있다. API로 호출할 때는 OPEN 쿠폰이 없는지
 *     {@link DummyDataController}가 먼저 확인한다)
 * 2) MySQL              : SET GLOBAL local_infile = 1;
 * 3) 단독 실행 시 application-dev.properties 의 url : ...&amp;allowLoadLocalInfile=true
 * 회원만, 또는 쿠폰만 다시 만들고 싶으면
 * {@link DummyDataGenerator} 또는 {@link CouponDummyGenerator} 를 각각 실행해도 된다.
 */
public class DummyDataAll {

    /**
     * 적재된 회원 / 쿠폰 / 발급 이력 건수 + 단계별 소요시간(대시보드 시각화용).
     * {@code *LoadMs}는 {@link #snapshot}(단순 DB 조회)일 땐 null - 방금 재적재한 응답에만 채워진다.
     */
    public record Counts(
            long userCount, long couponCount, long couponIssueCount,
            Long userLoadMs, Long couponIssueLoadMs, Long totalMs
    ) {
        public static Counts snapshot(long userCount, long couponCount, long couponIssueCount) {
            return new Counts(userCount, couponCount, couponIssueCount, null, null, null);
        }
    }

    public static void main(String[] args) throws Exception {
        run(null);
    }

    /** dataSource가 있으면(Spring 컨텍스트 안) 그 커넥션을 쓰고, null이면 단독 실행용 접속을 쓴다. */
    public static Counts run(DataSource dataSource) throws Exception {
        System.out.println("========== 1) 회원 더미데이터 ==========");
        DummyDataGenerator.Result userResult = DummyDataGenerator.run(dataSource);

        System.out.println();
        System.out.println("========== 2) 쿠폰 / 발급 이력 더미데이터 ==========");
        CouponDummyGenerator.Result couponResult = CouponDummyGenerator.run(dataSource);

        long userLoadMs = userResult.csvGenMs() + userResult.loadMs();
        long couponIssueLoadMs = couponResult.csvGenMs() + couponResult.loadMs();

        return new Counts(
                userResult.userCount(), couponResult.couponCount(), couponResult.issueCount(),
                userLoadMs, couponIssueLoadMs, userLoadMs + couponIssueLoadMs
        );
    }
}
