package com.mycom.myapp.team5.global.common.enums;

public enum CouponStatus {

    READY,
    OPEN,
    CLOSE;

    /**
     * READY -> OPEN -> CLOSE 단방향 전이만 허용
     */
    public boolean canTransitTo(CouponStatus target) {
    	return switch (this) {
	    	case READY -> target == OPEN;
	    	case OPEN -> target == CLOSE;
	    	case CLOSE -> false;
    	};
    }
}
