package com.mycom.myapp.team5.domain.test.service;

import com.mycom.myapp.team5.domain.test.dto.DummyDataStatus;
import com.mycom.myapp.team5.global.common.util.DummyDataAll;

public interface DummyDataLoadService {

    /**
     * 백그라운드 스레드로 적재를 시작하고 즉시 현재 상태를 반환한다. 이미 진행 중이면 거부한다.
     * @param before 적재 시작 직전 DB 스냅샷(회원/쿠폰/발급 이력 건수) - Before/After 비교용으로 그대로 보존된다.
     */
    DummyDataStatus start(DummyDataAll.Counts before);

    DummyDataStatus status();

}
