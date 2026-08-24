package com.mycom.myapp.team5.global.exception;

import com.mycom.myapp.team5.global.common.dto.ErrorResponse;
import com.mycom.myapp.team5.global.common.enums.CommonErrorCode;
import com.mycom.myapp.team5.global.common.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException e) {
        ErrorCode errorCode = e.getErrorCode();

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(
                        errorCode.getCode(),
                        errorCode.getMessage()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError() != null
                         ? e.getBindingResult().getFieldError().getDefaultMessage()
                         : CommonErrorCode.INVALID_INPUT_VALUE.getMessage();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        CommonErrorCode.INVALID_INPUT_VALUE.getCode(),
                        message
                ));
    }

    /** 브라우저 favicon 등 정적 리소스 없음 — 기능 영향 없음, ERROR 로그 억제 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("exception : ", e);

        return ResponseEntity
                .status(CommonErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ErrorResponse.of(
                        CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()
                ));
    }

}
