package com.mycom.myapp.team5.domain.coupon.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.coupon.service.CouponStatusService;
import com.mycom.myapp.team5.domain.user.entity.User;
import com.mycom.myapp.team5.domain.user.repository.UserRepository;
import com.mycom.myapp.team5.global.redis.CouponStockKeys;
import com.mycom.myapp.team5.global.redis.CouponStockRedisService;
import com.mycom.myapp.team5.global.redis.CouponStreamKeys;

@SpringBootTest
@AutoConfigureMockMvc
public class CouponControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CouponStockRedisService couponStockRedisService;

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@Autowired
	private CouponRepository couponRepository;

	@Autowired
	private CouponStatusService couponStatusService;

	@Autowired
	private UserRepository userRepository;

	private final List<Long> createdCouponIds = new ArrayList<>();
	private final List<Long> createdUserIds = new ArrayList<>();

	@AfterEach
	void tearDown() {
		createdCouponIds.forEach(id -> {
			stringRedisTemplate.delete(CouponStockKeys.stockKey(id));
			stringRedisTemplate.delete(CouponStockKeys.issuedSetKey(id));
			stringRedisTemplate.delete(CouponStreamKeys.streamKey(id));
			couponRepository.deleteById(id);
		});
		createdCouponIds.clear();
		userRepository.deleteAllById(createdUserIds);
		createdUserIds.clear();
	}

	private long createUser() {
		User user = User.builder()
				.email("ctrl-test-" + UUID.randomUUID() + "@example.com")
				.name("테스터")
				.phone("01012345678")
				.build();
		User saved = userRepository.save(user);
		createdUserIds.add(saved.getId());
		return saved.getId();
	}

	private long createOpenCoupon(int stock) {
		Coupon coupon = Coupon.builder()
				.name("ctrl-test")
				.totalQuantity(stock)
				.startAt(LocalDateTime.now().minusMinutes(1))
				.endAt(LocalDateTime.now().plusDays(1))
				.build();
		Coupon saved = couponRepository.save(coupon);
		createdCouponIds.add(saved.getId());
		couponStatusService.openCoupon(saved.getId());
		return saved.getId();
	}

	@Test
	void 발급_성공하면_202와_ApiResponse_형식으로_응답한다() throws Exception {
		long userId = createUser();
		long couponId = createOpenCoupon(1);

		mockMvc.perform(post("/coupons/{couponId}/issue", couponId).param("userId", String.valueOf(userId)))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").doesNotExist())
				.andExpect(jsonPath("$.message").doesNotExist());
	}

	@Test
	void 중복_발급이면_409와_ErrorResponse_형식으로_응답한다() throws Exception {
		long userId = createUser();
		long couponId = createOpenCoupon(10);
		mockMvc.perform(post("/coupons/{couponId}/issue", couponId).param("userId", String.valueOf(userId)));

		mockMvc.perform(post("/coupons/{couponId}/issue", couponId).param("userId", String.valueOf(userId)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CI001"))
				.andExpect(jsonPath("$.message").value("쿠폰 중복 발급"));
	}

	@Test
	void 품절이면_204와_ErrorResponse_형식으로_응답한다() throws Exception {
		long userId = createUser();
		long couponId = createOpenCoupon(1);
		stringRedisTemplate.opsForValue().set(CouponStockKeys.stockKey(couponId), "0");

		mockMvc.perform(post("/coupons/{couponId}/issue", couponId).param("userId", String.valueOf(userId)))
				.andExpect(status().isNoContent());
	}

	@Test
	void 재고_미적재면_204를_응답한다() throws Exception {
		long userId = createUser();
		long couponId = createOpenCoupon(1);
		stringRedisTemplate.delete(CouponStockKeys.stockKey(couponId));

		mockMvc.perform(post("/coupons/{couponId}/issue", couponId).param("userId", String.valueOf(userId)))
				.andExpect(status().isNoContent());
	}
}
