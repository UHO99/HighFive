package com.mycom.myapp.team5.global.aspect;

import java.lang.reflect.Method;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

import com.mycom.myapp.team5.global.common.util.MaskingUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

    private static final String POINTCUT =
            "execution(* com.mycom.myapp.team5.domain..controller..*(..)) "
            + "|| execution(* com.mycom.myapp.team5.domain..service..*(..))";

    @Before(POINTCUT)
    public void logBefore(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getName();
        String maskedArgs = MaskingUtils.maskForLog(joinPoint.getArgs());
        String description = describe(joinPoint);

        log.info("{}{} {} args={}", methodName, description, className, maskedArgs);
    }

    @AfterThrowing(pointcut = POINTCUT, throwing = "ex")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable ex) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getName();
        String maskedArgs = MaskingUtils.maskForLog(joinPoint.getArgs());
        String description = describe(joinPoint);

        log.error("{}{} {} args={} error={}", methodName, description, className, maskedArgs, ex.toString());
    }

    // 메서드에 @LogDescription이 있으면 "getMyCoupons(내 쿠폰 목록 조회(최근 발급 순))"처럼 이름 옆에 붙인다.
    // 구현체가 인터페이스 메서드를 오버라이드할 땐 애노테이션이 자동 상속되지 않으므로,
    // 구현체 메서드 -> (없으면) 구현한 인터페이스들 순으로 직접 찾는다.
    private String describe(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> targetClass = joinPoint.getTarget().getClass();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), targetClass);

        LogDescription description = method.getAnnotation(LogDescription.class);
        if (description == null) {
            description = findOnInterfaces(method, targetClass);
        }
        return description == null ? "" : "(" + description.value() + ")";
    }

    private LogDescription findOnInterfaces(Method method, Class<?> targetClass) {
        for (Class<?> iface : targetClass.getInterfaces()) {
            try {
                Method ifaceMethod = iface.getMethod(method.getName(), method.getParameterTypes());
                LogDescription annotation = ifaceMethod.getAnnotation(LogDescription.class);
                if (annotation != null) {
                    return annotation;
                }
            } catch (NoSuchMethodException ignored) {
                // 이 인터페이스엔 해당 메서드가 없음 - 다음 인터페이스 계속 탐색
            }
        }
        return null;
    }
}
