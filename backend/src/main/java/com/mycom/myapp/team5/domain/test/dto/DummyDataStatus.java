package com.mycom.myapp.team5.domain.test.dto;

import com.mycom.myapp.team5.global.common.util.DummyDataAll;

import java.time.Instant;

/**
 * 더미데이터 적재 진행 상태 - K6StatusResponse와 같은 이유로 존재한다: 적재가 몇십 초 걸리는 동안
 * 새로고침해도 "적재 중"이 유지되도록, 프런트가 이 상태를 폴링해서 판단한다(로컬 UI 상태가 아님).
 */
public record DummyDataStatus(
        boolean loading,
        Instant startedAt,
        Instant finishedAt,
        /**
         * 더미데이터 생성기가 항상 만들어내는 고정 시드값(DummyDataAll.SEED_BASELINE) - 대시보드
         * Before/After 표의 BEFORE. 세션 상태가 아니라 상수라서 재적재 여부와 무관하게 항상 같다 -
         * AFTER(실시간 DB 값)와 비교하면 시드 이후 관리자가 수동으로 만든 쿠폰/발급 건수가 델타로 드러난다.
         */
        DummyDataAll.Counts before,
        DummyDataAll.Counts lastResult,
        String lastError
) {
    public static DummyDataStatus idle() {
        return new DummyDataStatus(false, null, null, DummyDataAll.SEED_BASELINE, null, null);
    }
}
