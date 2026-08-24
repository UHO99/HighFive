package com.mycom.myapp.team5.domain.test.exception;

import com.mycom.myapp.team5.global.exception.BaseException;

public class DummyDataException extends BaseException {

    public DummyDataException(DummyDataErrorCode errorCode) {
        super(errorCode);
    }

}
