package com.mycom.myapp.team5.domain.test.exception;

import com.mycom.myapp.team5.global.exception.BaseException;

public class K6TestException extends BaseException {

    public K6TestException(K6ErrorCode errorCode) {
        super(errorCode);
    }

}
