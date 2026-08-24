package com.mycom.myapp.team5.global.aspect;

import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;
import com.mycom.myapp.team5.domain.monitoring.metric.CouponIssueMetricsRecorder;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class CouponIssueMetricsAspect {

    private final CouponIssueMetricsRecorder recorder;

    @Around("execution(* com.mycom.myapp.team5.global.redis.CouponIssueStreamProducer.requestIssue(..))")
    public Object recordIssueAttempt(ProceedingJoinPoint joinPoint) throws Throwable {
        long couponId = (long) joinPoint.getArgs()[0];
        try {
            Object result = joinPoint.proceed();
            recorder.recordSuccess(couponId);
            return result;
        } catch (CouponException e) {
            recorder.recordFailure(couponId, (CouponErrorCode) e.getErrorCode());
            throw e;
        }
    }
}
