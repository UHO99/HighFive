package com.mycom.myapp.team5.domain.test.dto;

import java.time.Instant;

public record K6StatusResponse(
        boolean running,
        String scenarioId,
        String scenarioFile,
        Long couponId,
        Instant startedAt,
        Integer exitCode
) {
    public static K6StatusResponse idle() {
        return new K6StatusResponse(false, null, null, null, null, null);
    }
}
