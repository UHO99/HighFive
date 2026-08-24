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
        DummyDataAll.Counts lastResult,
        String lastError
) {
    public static DummyDataStatus idle() {
        return new DummyDataStatus(false, null, null, null, null);
    }
}
