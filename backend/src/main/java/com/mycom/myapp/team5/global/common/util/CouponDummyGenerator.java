package com.mycom.myapp.team5.global.common.util;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.Random;
import javax.sql.DataSource;

/**
 * 쿠폰 / 발급 이력 더미데이터 생성 도구
 * DummyDataGenerator 로 users 를 먼저 적재한 뒤 실행. {@link DummyDataGenerator}와 마찬가지로
 * main()으로 단독 실행하거나 {@link #run(DataSource)}로 Spring 컨텍스트 안에서 호출할 수 있다.
 * 사전 조건
 * 1) MySQL : SET GLOBAL local_infile = 1;
 * 2) 단독 실행 시 application-dev.properties 의 url : ...&amp;allowLoadLocalInfile=true
 */
public class CouponDummyGenerator {

    // ── 출력 ────────────────────────────────────────────
    private static final Path OUT_DIR = Path.of(
            System.getProperty("dummy.dir", System.getProperty("java.io.tmpdir")), "highfive");
    private static final Path COUPON_CSV = OUT_DIR.resolve("coupon.csv");
    private static final Path ISSUE_CSV  = OUT_DIR.resolve("coupon_issue.csv");

    // ── 규모 ────────────────────────────────────────────
    // DummyDataAll의 SEED_BASELINE이 참조한다(Before/After 표의 "Seed 값") - package-private.
    static final int PAST_COUPON_COUNT = 30;        // 과거 종료 쿠폰
    static final int ISSUES_PER_COUPON = 100_000;   // 쿠폰당 발급 건수
    private static final long SEED             = 42L;       // 고정 → 항상 같은 데이터

    // 시연용 쿠폰 (부하 테스트 대상)
    // private static final int DEMO_COUPON_ID = PAST_COUPON_COUNT + 1;
    // private static final int DEMO_STOCK     = 10_000;

    // ── 이벤트 일정 ─────────────────────────────────────
    // 쿠폰마다 기간을 다르게 둔다. 모든 데이터가 같은 시각이면
    // 검증 배치의 "기준 시점(as-of) 이전만 검증" 필터가
    // 전부 통과 아니면 전부 제외가 되어 재현성 설계를 시험할 수 없다.
    private static final LocalDateTime EVENT_BASE = LocalDateTime.of(2025, 1, 1, 0, 0);
    private static final int EVENT_INTERVAL_DAYS = 12;  // 쿠폰 간 시작 간격
    private static final int EVENT_DURATION_DAYS = 7;   // 이벤트 진행 기간
    private static final int VALID_DAYS          = 60;  // 발급 후 유효기간

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** LOAD DATA 에서 NULL */
    private static final String NULL_MARK = "\\N";

    /** 적재된 coupon / coupon_issue 건수. */
    public record Counts(long couponCount, long issueCount) {}

    /** 적재 건수와 단계별 소요시간(대시보드 시각화용). */
    public record Result(long couponCount, long issueCount, long csvGenMs, long loadMs) {}

    public static void main(String[] args) throws Exception {
        run(null);
    }

    /** dataSource가 있으면(Spring 컨텍스트 안) 그 커넥션을 쓰고, null이면 단독 실행용 접속을 쓴다. */
    public static Result run(DataSource dataSource) throws Exception {
        long t0 = System.currentTimeMillis();
        generateCsv();
        long t1 = System.currentTimeMillis();
        Counts counts = load(dataSource);
        long t2 = System.currentTimeMillis();
        verify(dataSource);
        return new Result(counts.couponCount(), counts.issueCount(), t1 - t0, t2 - t1);
    }

    // ── 1) CSV 생성 ─────────────────────────────────────
    private static void generateCsv() throws Exception {
        long start = System.currentTimeMillis();
        Random random = new Random(SEED);

        Files.createDirectories(OUT_DIR);

        long issueId = 0;

        try (BufferedWriter cw = Files.newBufferedWriter(COUPON_CSV, StandardCharsets.UTF_8);
             BufferedWriter iw = Files.newBufferedWriter(ISSUE_CSV, StandardCharsets.UTF_8)) {

            for (int k = 1; k <= PAST_COUPON_COUNT; k++) {
                LocalDateTime startAt = EVENT_BASE.plusDays((long) (k - 1) * EVENT_INTERVAL_DAYS);
                LocalDateTime endAt   = startAt.plusDays(EVENT_DURATION_DAYS);

                // 과거 쿠폰은 전량 소진된 것으로 둔다 (선착순 이벤트의 자연스러운 결과)
                cw.write(k
                        + ",이벤트쿠폰_" + k
                        + "," + ISSUES_PER_COUPON                 // total_quantity
                        + "," + startAt.format(FMT)
                        + "," + endAt.format(FMT)
                        + ",CLOSE"
                        + "," + startAt.minusDays(1).format(FMT)  // created_at
                        + "," + endAt.format(FMT)                 // updated_at
                        + "," + ISSUES_PER_COUPON                 // issued_quantity
                        + "\n");

                issueId = writeIssues(iw, random, k, startAt, issueId);
            }

            // 시연용 쿠폰 — 쿠폰 생성 API 완성 전까지 주석 처리.
            // coupon 테이블을 TRUNCATE 하므로, 켜두면 그 API로 만든 쿠폰과 충돌한다.
            // LocalDateTime demoStart =
            // EVENT_BASE.plusDays((long) PAST_COUPON_COUNT * EVENT_INTERVAL_DAYS);
            // cw.write(DEMO_COUPON_ID
            // + ",EVENT_COUPON"
            // + "," + DEMO_STOCK
            // + "," + demoStart.format(FMT)
            // + "," + demoStart.plusDays(EVENT_DURATION_DAYS).format(FMT)
            // + ",READY"
            // + "," + demoStart.minusDays(1).format(FMT)
            // + "," + NULL_MARK                             // updated_at
            // + ",0"                                        // issued_quantity
            // + "\n");
        }

        System.out.printf("CSV 생성 : 쿠폰 %,d건 / 발급 이력 %,d건  %,dms%n",
                PAST_COUPON_COUNT, issueId, System.currentTimeMillis() - start);
    }

    /**
     * 쿠폰 하나에 대한 발급 이력을 쓴다.
     * UNIQUE(user_id, coupon_id) 위반을 구조적으로 막기 위해
     * 쿠폰마다 user 구간을 일정 폭만큼 밀어서 배정한다.
     * 같은 쿠폰 안에서 user_id 가 겹치지 않으면 조합은 항상 유일하다.
     * @return 마지막으로 사용한 coupon_issue id
     */
    private static long writeIssues(BufferedWriter w, Random random, int couponId,
                                    LocalDateTime startAt, long issueId) throws Exception {

        // 쿠폰마다 3만씩 밀어서 배정 → 30번째 쿠폰도 97만까지만 사용 (users 100만 이내)
        int userOffset = (couponId - 1) * 30_000;

        // 이벤트 기간 안에서 발급 시각을 단조 증가시킨다.
        // id 순서와 issued_at 순서가 어긋나면
        // "id 기준 스냅샷"과 "시각 기준 스냅샷"의 대상 집합이 달라진다.
        long durationSec = (long) EVENT_DURATION_DAYS * 24 * 60 * 60;
        int avgGap = (int) (durationSec / ISSUES_PER_COUPON);
        long cursor = 0;

        for (int j = 1; j <= ISSUES_PER_COUPON; j++) {
            long userId = userOffset + j;

            cursor = Math.min(cursor + random.nextInt(avgGap * 2) + 1, durationSec);
            LocalDateTime issuedAt = startAt.plusSeconds(cursor);

            // 상태 분포 : 발급 40 / 사용 40 / 취소 10 / 만료 10
            // 한 상태로만 채우면 검증 배치가 상태 전이를 하나도 검사하지 못한다.
            int r = random.nextInt(100);
            String status;
            String usedAt = NULL_MARK;
            String canceledAt = NULL_MARK;
            String expiredAt = NULL_MARK;

            if (r < 40) {
                status = "ISSUED";
            } else if (r < 80) {
                status = "USED";
                usedAt = issuedAt.plusSeconds(3600 + random.nextInt(30 * 24 * 3600)).format(FMT);
            } else if (r < 90) {
                status = "CANCELED";
                canceledAt = issuedAt.plusSeconds(60 + random.nextInt(7 * 24 * 3600)).format(FMT);
            } else {
                status = "EXPIRED";
                expiredAt = issuedAt.plusDays(VALID_DAYS).format(FMT);
            }

            w.write(++issueId
                    + "," + userId
                    + "," + couponId
                    + "," + status
                    + "," + issuedAt.format(FMT)
                    + "," + usedAt
                    + "," + canceledAt
                    + "," + expiredAt
                    + "\n");
        }
        return issueId;
    }

    // ── 2) 적재 ─────────────────────────────────────────
    private static Counts load(DataSource dataSource) throws Exception {
        String couponSql = loadSql(COUPON_CSV, "coupon",
                "(id, name, total_quantity, start_at, end_at, status, "
              + "created_at, updated_at, issued_quantity)");
        String issueSql = loadSql(ISSUE_CSV, "coupon_issue",
                "(id, user_id, coupon_id, status, issued_at, used_at, canceled_at, expired_at)");

        try (Connection con = connect(dataSource);
             Statement st = con.createStatement()) {

            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            st.execute("SET UNIQUE_CHECKS = 0");

            // 자식 → 부모 순으로 비운다
            st.execute("TRUNCATE TABLE coupon_issue");
            st.execute("TRUNCATE TABLE coupon");
            System.out.println("TRUNCATE : coupon_issue, coupon 비움");

            long t1 = System.currentTimeMillis();
            int coupons = st.executeUpdate(couponSql);
            long t2 = System.currentTimeMillis();
            int issues = st.executeUpdate(issueSql);
            long t3 = System.currentTimeMillis();

            st.execute("SET UNIQUE_CHECKS = 1");
            st.execute("SET FOREIGN_KEY_CHECKS = 1");

            System.out.printf("적재     : 쿠폰 %,d건 %,dms / 발급 이력 %,d건 %,dms%n",
                    coupons, t2 - t1, issues, t3 - t2);
            return new Counts(coupons, issues);
        }
    }

    private static String loadSql(Path csv, String table, String columns) {
        return "LOAD DATA LOCAL INFILE '" + csv.toString().replace('\\', '/') + "' "
             + "INTO TABLE " + table + " CHARACTER SET utf8mb4 "
             + "FIELDS TERMINATED BY ',' LINES TERMINATED BY '\\n' "
             + columns;
    }

    // ── 3) 검증 ─────────────────────────────────────────
    // 적재 중 제약 검사를 껐으므로 위반 데이터가 들어갔는지 직접 확인한다.
    // 검사를 다시 켜도 이미 들어간 데이터를 소급 검증하지는 않는다.
    private static void verify(DataSource dataSource) throws Exception {
        try (Connection con = connect(dataSource);
             Statement st = con.createStatement()) {

            System.out.println();
            System.out.println("-- 검증 -----------------------------------------");

            count(st, "쿠폰                 ", "SELECT COUNT(*) FROM coupon");
            count(st, "발급 이력            ", "SELECT COUNT(*) FROM coupon_issue");

            check(st, "중복 조합            ",
                    "SELECT COUNT(*) FROM (SELECT user_id, coupon_id FROM coupon_issue "
                  + "GROUP BY user_id, coupon_id HAVING COUNT(*) > 1) t");

            check(st, "고아 레코드 (user)   ",
                    "SELECT COUNT(*) FROM coupon_issue ci "
                  + "LEFT JOIN users u ON u.id = ci.user_id WHERE u.id IS NULL");

            check(st, "고아 레코드 (coupon) ",
                    "SELECT COUNT(*) FROM coupon_issue ci "
                  + "LEFT JOIN coupon c ON c.id = ci.coupon_id WHERE c.id IS NULL");

            check(st, "발급수 > 총수량      ",
                    "SELECT COUNT(*) FROM coupon c WHERE "
                  + "(SELECT COUNT(*) FROM coupon_issue ci WHERE ci.coupon_id = c.id) "
                  + "> c.total_quantity");

            check(st, "issued_quantity 불일치",
                    "SELECT COUNT(*) FROM coupon c WHERE c.status = 'CLOSE' AND "
                  + "c.issued_quantity <> "
                  + "(SELECT COUNT(*) FROM coupon_issue ci WHERE ci.coupon_id = c.id)");

            check(st, "상태-시각 불일치     ",
                    "SELECT COUNT(*) FROM coupon_issue WHERE "
                  + "(status = 'USED'     AND (used_at     IS NULL OR used_at     <= issued_at)) OR "
                  + "(status = 'CANCELED' AND (canceled_at IS NULL OR canceled_at <= issued_at)) OR "
                  + "(status = 'EXPIRED'  AND (expired_at  IS NULL OR expired_at  <= issued_at)) OR "
                  + "(status = 'ISSUED'   AND (used_at IS NOT NULL OR canceled_at IS NOT NULL "
                  + "OR expired_at IS NOT NULL))");

            System.out.println();
            try (ResultSet rs = st.executeQuery(
                    "SELECT status, COUNT(*) FROM coupon_issue GROUP BY status ORDER BY 2 DESC")) {
                while (rs.next()) {
                    System.out.printf("  %-9s : %,10d건%n", rs.getString(1), rs.getLong(2));
                }
            }
            System.out.println("------------------------------------------------");
        }
    }

    private static void count(Statement st, String label, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            System.out.printf("  %s : %,10d건%n", label, rs.getLong(1));
        }
    }

    /** 0이어야 정상인 항목 */
    private static void check(Statement st, String label, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            long n = rs.getLong(1);
            System.out.printf("  %s : %,10d건  %s%n", label, n, n == 0 ? "OK" : "!! 확인 필요");
        }
    }

    // ── DB 접속 : dataSource가 있으면(Spring 컨텍스트) 그 커넥션을 쓰고,
    // 없으면(단독 실행) application-dev.properties를 직접 읽는다 ─────
    private static Connection connect(DataSource dataSource) throws Exception {
        if (dataSource != null) {
            return dataSource.getConnection();
        }

        Properties p = new Properties();
        try (InputStream in = CouponDummyGenerator.class.getClassLoader()
                .getResourceAsStream("application-dev.properties")) {
            if (in == null) {
                throw new IllegalStateException("application-dev.properties 를 찾을 수 없습니다.");
            }
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        String url = p.getProperty("spring.datasource.url");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("spring.datasource.url 이 설정되지 않았습니다.");
        }
        if (!url.contains("allowLoadLocalInfile=true")) {
            System.err.println("[경고] url 에 allowLoadLocalInfile=true 가 없습니다.");
        }

        return DriverManager.getConnection(
                url,
                p.getProperty("spring.datasource.username"),
                p.getProperty("spring.datasource.password"));
    }
}
