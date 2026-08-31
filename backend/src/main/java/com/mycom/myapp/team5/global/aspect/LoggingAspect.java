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

	// domain.monitoring(대시보드 폴링)과 domain.coupon.service.sync(S012/S013 정합성 배치, 5초/60초
	// 주기로 서버가 켜져 있는 내내 자동 실행됨)는 발급 요청과 무관하게 끊임없이 로그를 만들어내므로 제외한다.
	//
	// 아래 세 메서드는 클래스/서비스 전체가 아니라 딱 그 메서드만 뺀다 - 같은 클래스의 다른 메서드
	// (쿠폰 생성/오픈/마감, 더미데이터 재적재 트리거 등)는 사람이 클릭해서 일어나는 실제 사건이라
	// 여전히 로그가 남아야 한다. 반면 아래 셋은 "다른 사람의 변경 사항을 화면에 실시간 반영하기 위한"
	// 순수 조회 폴링(2~10초 주기)이라 로그 가치가 낮다.
	//   - AdminCouponController.listCoupons() : 상단 쿠폰 선택 드롭다운 갱신용, 10초
	//   - K6TestController.status()/K6TestServiceImpl.status() : 테스트 실행 상태 표시용, 2초
	//   - DummyDataController.status()/counts(), DummyDataLoadServiceImpl.status() : 적재 진행 상황 표시용, 2~10초
	private static final String POINTCUT = //
			"(execution(* com.mycom.myapp.team5.domain..controller..*(..)) " //
					+ "|| execution(* com.mycom.myapp.team5.domain..service..*(..))) " //
					+ "&& !within(com.mycom.myapp.team5.domain.monitoring..*) " //
					+ "&& !within(com.mycom.myapp.team5.domain.coupon.service.sync..*) " //
					+ "&& !execution(* com.mycom.myapp.team5.domain.coupon.controller.AdminCouponController.listCoupons(..)) " //
					+ "&& !execution(* com.mycom.myapp.team5.domain.coupon.service.CouponServiceImpl.listByStatus(..)) " //
					+ "&& !execution(* com.mycom.myapp.team5.domain.test.controller.K6TestController.status(..)) " //
					+ "&& !execution(* com.mycom.myapp.team5.domain.test.service.K6TestServiceImpl.status(..)) " //
					+ "&& !execution(* com.mycom.myapp.team5.domain.test.controller.DummyDataController.status(..)) " //
					+ "&& !execution(* com.mycom.myapp.team5.domain.test.controller.DummyDataController.counts(..)) " //
					+ "&& !execution(* com.mycom.myapp.team5.domain.test.service.DummyDataLoadServiceImpl.status(..))"; //

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
			}
			catch (NoSuchMethodException ignored) {
			}
		}
		return null;
	}
}