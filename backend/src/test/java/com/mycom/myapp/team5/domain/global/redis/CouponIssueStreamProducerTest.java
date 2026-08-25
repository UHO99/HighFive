package com.mycom.myapp.team5.domain.global.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;
import com.mycom.myapp.team5.global.redis.CouponIssueStreamProducer;
import com.mycom.myapp.team5.global.redis.CouponStockKeys;
import com.mycom.myapp.team5.global.redis.CouponStockRedisService;
import com.mycom.myapp.team5.global.redis.CouponStreamKeys;

@SpringBootTest
public class CouponIssueStreamProducerTest {
	// 운영/다른 테스트 데이터와 안 겹치도록 테스트 전용 쿠폰 id 사용
	private static final long COUPON_ID = 999L;
	private static final long USER_ID = 1L;

	@Autowired
	private CouponIssueStreamProducer producer;

	@Autowired
	private CouponStockRedisService couponStockRedisService;

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@AfterEach
	void tearDown() {
		stringRedisTemplate.delete(CouponStockKeys.stockKey(COUPON_ID));
		stringRedisTemplate.delete(CouponStockKeys.issuedSetKey(COUPON_ID));
		stringRedisTemplate.delete(CouponStreamKeys.streamKey(COUPON_ID));
	}

	@Test
	void 재고가_있으면_발급에_성공하고_스트림에_적재된다() {
		// given
		couponStockRedisService.initStock(COUPON_ID, 1);

		// when
		producer.requestIssue(COUPON_ID, USER_ID, System.currentTimeMillis());

		// then
		Long streamSize = stringRedisTemplate.opsForStream().size(CouponStreamKeys.streamKey(COUPON_ID));
		assertThat(streamSize).isEqualTo(1L);
	}

	@Test
	void 이미_발급받은_회원이_다시_요청하면_예외가_발생하고_스트림에_추가로_안_쌓인다() {
		// given
		couponStockRedisService.initStock(COUPON_ID, 10);
		producer.requestIssue(COUPON_ID, USER_ID, System.currentTimeMillis()); // 최초 1회는 성공

		// when & then
		assertThatThrownBy(() -> producer.requestIssue(COUPON_ID, USER_ID, System.currentTimeMillis())).isInstanceOf(CouponException.class).extracting(e -> ((CouponException) e).getErrorCode()).isEqualTo(CouponErrorCode.COUPON_ISSUE_DUPLICATE);

		Long streamSize = stringRedisTemplate.opsForStream().size(CouponStreamKeys.streamKey(COUPON_ID));
		assertThat(streamSize).isEqualTo(1L); // 두 번째 요청은 안 쌓여야 정상
	}

	@Test
	void 재고가_0이면_품절_예외가_발생하고_스트림에_안_쌓인다() {
		// given
		couponStockRedisService.initStock(COUPON_ID, 0);

		// when & then
		assertThatThrownBy(() -> producer.requestIssue(COUPON_ID, USER_ID, System.currentTimeMillis())).isInstanceOf(CouponException.class).extracting(e -> ((CouponException) e).getErrorCode()).isEqualTo(CouponErrorCode.COUPON_SOLD_OUT);

		Long streamSize = stringRedisTemplate.opsForStream().size(CouponStreamKeys.streamKey(COUPON_ID));
		assertThat(streamSize == null || streamSize == 0L).isTrue();
	}

	@Test
	void 오픈하지_않은_쿠폰이면_재고_미적재_예외가_발생한다() {
		// given: initStock을 호출하지 않은 상태 (쿠폰 오픈 전을 흉내냄)

		// when & then
		assertThatThrownBy(() -> producer.requestIssue(COUPON_ID, USER_ID, System.currentTimeMillis())).isInstanceOf(CouponException.class).extracting(e -> ((CouponException) e).getErrorCode()).isEqualTo(CouponErrorCode.COUPON_INVENTORY_NOT_STOCKED);
	}
}
