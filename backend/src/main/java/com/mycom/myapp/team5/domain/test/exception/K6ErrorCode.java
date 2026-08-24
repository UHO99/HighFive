package com.mycom.myapp.team5.domain.test.exception;

import com.mycom.myapp.team5.global.common.enums.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum K6ErrorCode implements ErrorCode {

    UNKNOWN_SCENARIO(HttpStatus.BAD_REQUEST, "K6001", "존재하지 않는 시나리오입니다."),
    ALREADY_RUNNING(HttpStatus.CONFLICT, "K6002", "이미 다른 부하테스트가 실행 중입니다."),
    START_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "K6003", "부하테스트 컨테이너 실행에 실패했습니다."),
    STOP_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "K6004", "부하테스트 컨테이너 중지에 실패했습니다."),
    SCRIPT_NOT_IN_IMAGE(HttpStatus.CONFLICT, "K6006", "선택한 스크립트가 아직 k6 이미지에 반영되지 않았습니다. docker compose build k6 실행 후 다시 시도하세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

}
