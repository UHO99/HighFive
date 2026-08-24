package com.mycom.myapp.team5.domain.test.service;

import com.mycom.myapp.team5.domain.test.dto.DummyDataStatus;
import com.mycom.myapp.team5.domain.test.exception.DummyDataErrorCode;
import com.mycom.myapp.team5.domain.test.exception.DummyDataException;
import com.mycom.myapp.team5.global.common.util.DummyDataAll;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.Instant;

/**
 * K6TestServiceImpl과 같은 패턴 - 적재는 수십 초 걸리는 무거운 작업이라 HTTP 요청/응답 안에서
 * 동기로 끝내지 않고 백그라운드 스레드로 돌린 뒤, 진행 상태를 폴링용으로 남긴다. 새로고침해도
 * "적재 중"이 유지되는 이유가 이거다 - 프런트 로컬 상태가 아니라 여기 상태를 폴링해서 보여준다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DummyDataLoadServiceImpl implements DummyDataLoadService {

    private final DataSource dataSource;

    private final Object lock = new Object();
    private volatile DummyDataStatus current = DummyDataStatus.idle();

    @Override
    public DummyDataStatus start(DummyDataAll.Counts before) {
        synchronized (lock) {
            if (current.loading()) {
                throw new DummyDataException(DummyDataErrorCode.ALREADY_LOADING);
            }
            current = new DummyDataStatus(true, Instant.now(), null, DummyDataAll.SEED_BASELINE, current.lastResult(), null);
        }

        Thread thread = new Thread(this::runLoad, "dummy-data-load");
        thread.setDaemon(true);
        thread.start();

        return status();
    }

    private void runLoad() {
        try {
            DummyDataAll.Counts result = DummyDataAll.run(dataSource);
            synchronized (lock) {
                current = new DummyDataStatus(false, current.startedAt(), Instant.now(), DummyDataAll.SEED_BASELINE, result, null);
            }
            log.info("더미데이터 적재 완료 - userCount={} couponCount={} couponIssueCount={}",
                    result.userCount(), result.couponCount(), result.couponIssueCount());
        } catch (Exception e) {
            log.error("더미데이터 적재 실패", e);
            synchronized (lock) {
                current = new DummyDataStatus(false, current.startedAt(), Instant.now(), DummyDataAll.SEED_BASELINE, current.lastResult(), e.getMessage());
            }
        }
    }

    @Override
    public DummyDataStatus status() {
        synchronized (lock) {
            return current;
        }
    }
}
