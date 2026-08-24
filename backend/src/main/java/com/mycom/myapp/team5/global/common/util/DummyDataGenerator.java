package com.mycom.myapp.team5.global.common.util;

import com.mycom.myapp.team5.domain.test.controller.DummyDataController;

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
 * 더미데이터 생성 도구
 * IntelliJ에서 main()으로 단독 실행할 수도 있고, {@link DummyDataController}처럼 Spring
 * 컨텍스트 안에서 {@link #run(DataSource)}로 호출할 수도 있다 - DataSource를 넘기면 그 커넥션을
 * 쓰고, null이면(단독 실행) application-dev.properties를 직접 읽어 접속한다.
 * 사전 조건
 * 1) MySQL : SET GLOBAL local_infile = 1;
 * 2) 단독 실행 시 application-dev.properties 의 url : ...&amp;allowLoadLocalInfile=true
 *
 */
public class DummyDataGenerator {

    // ── 설정 ────────────────────────────────────────────
    // 출력 위치. 개인 경로에 의존하지 않도록 시스템 임시 폴더를 사용
    private static final Path OUT_DIR = Path.of(
            System.getProperty("dummy.dir", System.getProperty("java.io.tmpdir")), "highfive");
    private static final Path OUT = OUT_DIR.resolve("users.csv");
    private static final int USER_COUNT = 1_000_000;
    private static final long SEED = 42L;      // 고정 

    // ── 이름 조합 (20 × 25 = 500가지) ───────────────────
    private static final String[] LAST_NAMES = {
            "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임",
            "한", "오", "서", "신", "권", "황", "안", "송", "유", "엄"
    };
    private static final String[] FIRST_NAMES = {
            "민준", "서연", "도윤", "하은", "시우", "지우", "주원", "서윤", "지호", "수아",
            "예준", "지민", "건우", "채원", "우진", "다은", "선우", "유진", "현우", "소율",
            "민하", "주호", "재훈", "수현", "유동"
    };

    // 이메일 도메인
    private static final String[] DOMAINS = {
            "gmail.com", "naver.com", "daum.net", "kakao.com", "test.com"
    };

    // 가입 시각을 1년 범위로 (시드 고정)
    private static final LocalDateTime SIGNUP_BASE = LocalDateTime.of(2025, 1, 1, 0, 0);
    private static final int SIGNUP_RANGE_SEC = 365 * 24 * 60 * 60;
    // 실제 서비스는 id(AUTO_INCREMENT) 순서 = 가입 순서다.
    // 이 둘이 어긋나면 'id 기준 스냅샷'과 '시각 기준 스냅샷'의 대상 집합이 달라지므로
    // created_at 을 단조 증가시킨다.
    private static final int AVG_GAP_SEC = Math.max(1, SIGNUP_RANGE_SEC / USER_COUNT);
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 적재된 users 건수와 단계별 소요시간(대시보드 시각화용). */
    public record Result(long userCount, long csvGenMs, long loadMs) {}

    public static void main(String[] args) throws Exception {
        run(null);
    }

    /**
     * dataSource가 있으면(Spring 컨텍스트 안) 그 커넥션을 쓰고, null이면 단독 실행용 접속을 쓴다.
     */
    public static Result run(DataSource dataSource) throws Exception {
        long t0 = System.currentTimeMillis();
        generateCsv();
        long t1 = System.currentTimeMillis();
        long rows = load(dataSource);
        long t2 = System.currentTimeMillis();
        verify(dataSource);
        return new Result(rows, t1 - t0, t2 - t1);
    }

    // ── 1) CSV 생성 ─────────────────────────────────────
    private static void generateCsv() throws Exception {
        long start = System.currentTimeMillis();
        Random random = new Random(SEED);

        Files.createDirectories(OUT.getParent());
        long signupCursor = 0;
        try (BufferedWriter w = Files.newBufferedWriter(OUT, StandardCharsets.UTF_8)) {
            for (int i = 1; i <= USER_COUNT; i++) {
                String name = LAST_NAMES[random.nextInt(LAST_NAMES.length)]
                            + FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
                String email = "user" + i + "@" + DOMAINS[random.nextInt(DOMAINS.length)];
                String phone = "010" + (10_000_000 + random.nextInt(89_999_999));

                signupCursor += random.nextInt(AVG_GAP_SEC * 2) + 1;
                String createdAt = SIGNUP_BASE.plusSeconds(signupCursor).format(FMT);

                w.write(i + "," + email + "," + name + "," + phone + "," + createdAt + "\n");
            }
        }

        System.out.printf("CSV 생성 : %,d건  %,dms  → %s%n",
                USER_COUNT, System.currentTimeMillis() - start, OUT);
    }

    // ── 2) 적재 ─────────────────────────────────────────
    /**
     * CSV 를 users 테이블에 적재
     * 대량 적재 동안에는 FK/UNIQUE 검사를 끈다. 생성 단계에서 정합성을 보장하고
     * 적재 후 {@link #verify(DataSource)} 로 직접 확인한다.
     */
    private static long load(DataSource dataSource) throws Exception {
        String path = OUT.toString().replace('\\', '/');
        String sql = "LOAD DATA LOCAL INFILE '" + path + "' "
                   + "INTO TABLE users CHARACTER SET utf8mb4 "
                   + "FIELDS TERMINATED BY ',' LINES TERMINATED BY '\\n' "
                   + "(id, email, name, phone, created_at)";

        try (Connection con = connect(dataSource);
             Statement st = con.createStatement()) {

            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            st.execute("SET UNIQUE_CHECKS = 0");
            st.execute("TRUNCATE TABLE users");
            System.out.println("TRUNCATE : users 비움");

            long start = System.currentTimeMillis();
            int rows = st.executeUpdate(sql);
            long elapsed = System.currentTimeMillis() - start;

            st.execute("SET UNIQUE_CHECKS = 1");
            st.execute("SET FOREIGN_KEY_CHECKS = 1");

            System.out.printf("적재    : %,d건  %,dms%n", rows, elapsed);
            return rows;
        }
    }

    // ── 3) 검증 ─────────────────────────────────────────
    private static void verify(DataSource dataSource) throws Exception {
        try (Connection con = connect(dataSource);
             Statement st = con.createStatement()) {

            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
                rs.next();
                System.out.printf("검증    : users %,d건%n", rs.getLong(1));
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT id, email, name, phone FROM users ORDER BY id LIMIT 3")) {
                while (rs.next()) {
                    System.out.printf("  샘플  : %d | %s | %s | %s%n",
                            rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4));
                }
            }
        }
    }

    // ── DB 접속 : dataSource가 있으면(Spring 컨텍스트) 그 커넥션을 쓰고,
    // 없으면(단독 실행) application-dev.properties를 직접 읽는다 ─────
    private static Connection connect(DataSource dataSource) throws Exception {
        if (dataSource != null) {
            return dataSource.getConnection();
        }

        Properties p = new Properties();
        try (InputStream in = DummyDataGenerator.class.getClassLoader()
                .getResourceAsStream("application-dev.properties")) {
            if (in == null) {
                throw new IllegalStateException(
                        "application-dev.properties 를 찾을 수 없습니다. "
                      + "src/main/resources 에 파일이 있는지 확인하세요.");
            }
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        String url = p.getProperty("spring.datasource.url");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "spring.datasource.url 이 설정되지 않았습니다. "
                  + "application-dev.properties 를 확인하세요.");
        }
        if (!url.contains("allowLoadLocalInfile=true")) {
            System.err.println("[경고] url 에 allowLoadLocalInfile=true 가 없습니다. "
                             + "LOAD DATA 가 거부될 수 있습니다.");
        }

        return DriverManager.getConnection(
                url,
                p.getProperty("spring.datasource.username"),
                p.getProperty("spring.datasource.password"));
    }
}
