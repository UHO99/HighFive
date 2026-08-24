package com.mycom.myapp.team5.domain.test.service;

import com.mycom.myapp.team5.domain.test.dto.DummyDataStatus;

public interface DummyDataLoadService {

    /** 백그라운드 스레드로 적재를 시작하고 즉시 현재 상태를 반환한다. 이미 진행 중이면 거부한다. */
    DummyDataStatus start();

    DummyDataStatus status();

}
