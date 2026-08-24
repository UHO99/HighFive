package com.mycom.myapp.team5.global.common.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record ErrorResponse(
        String code,
        String message,
        LocalDateTime timestamp
) {

    // DB에 안 들어가고 바로 JSON으로 나가는 값이라 JDBC connectionTimeZone 보정을 못 받는다 -
    // 컨테이너 TZ와 무관하게 항상 KST가 되도록 명시적으로 zone을 지정한다.
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, LocalDateTime.now(ZoneId.of("Asia/Seoul")));
    }

}
