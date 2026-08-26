package com.mycom.myapp.team5.global.redis;

import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.mycom.myapp.team5.domain.coupon.dto.CouponFairnessReport;
import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CouponStockRedisService {

	private final StringRedisTemplate redisTemplate;

	// 반환값: 1 = 발급 성공, 0 = 품절, -1 = 이미 발급됨(중복), -2 = 재고 미적재(쿠폰 오픈 안 됨)
	// 기록 형식: "{순번}:{userId}:{결과}:{Redis처리시각ms}:{게이트진입시각ms}:{컨트롤러진입시각ms}"
	private static final String ISSUE_SCRIPT = "local function record(result) " + //
			"  local seq = redis.call('incr', KEYS[3]) " + //
			"  local t = redis.call('TIME') " + //
			"  local redisTimeMs = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000) " + //
			"  redis.call('zadd', KEYS[4], seq, seq .. ':' .. ARGV[1] .. ':' .. result .. ':' .. redisTimeMs .. ':' .. ARGV[2] .. ':' .. ARGV[3]) " + //
			"end " + //
			"if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then " + //
			"  record('DUPLICATE') " + //
			"  return -1 " + //
			"end " + //
			"local stock = tonumber(redis.call('get', KEYS[1])) " + //
			"if not stock then " + //
			"  return -2 " + //
			"end " + //
			"if stock <= 0 then " + //
			"  record('SOLDOUT') " + //
			"  return 0 " + //
			"end " + //
			"redis.call('decr', KEYS[1]) " + //
			"redis.call('sadd', KEYS[2], ARGV[1]) " + //
			"record('SUCCESS') " + //
			"return 1";

	private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>(ISSUE_SCRIPT, Long.class);

	public void initStock(long couponId, int quantity) {
		redisTemplate.opsForValue().set(CouponStockKeys.stockKey(couponId), String.valueOf(quantity));
	}

	/**
	 * Redis 잔여 재고. 키가 없으면 null (미오픈/미적재).
	 */
	public Integer getStock(long couponId) {
		String value = redisTemplate.opsForValue().get(CouponStockKeys.stockKey(couponId));
		if (value == null) {
			return null;
		}
		return Integer.valueOf(value);
	}

	/**
	 * 발급을 시도한다. 성공하면 조용히 반환하고, 실패 사유별로 다른 CouponException을 던진다.
	 *
	 * @param gateEnteredAtMs
	 *     이 요청이 Redis 게이트(Producer)에 진입한 시각
	 * @param controllerEnteredAtMs
	 *     이 요청이 컨트롤러에 최초 도달한 시각. gateEnteredAtMs와의 차이가 곧 validateIssueable() 등 게이트 진입 전 단계의 소요 시간이다.
	 */
	public void issue(long couponId, long userId, long gateEnteredAtMs, long controllerEnteredAtMs) {
		List<String> keys = List.of( //
				CouponStockKeys.stockKey(couponId), //
				CouponStockKeys.issuedSetKey(couponId), //
				CouponStockKeys.fairnessSeqKey(couponId), //
				CouponStockKeys.fairnessLogKey(couponId));

		Long result = redisTemplate.execute(ISSUE, keys, String.valueOf(userId), String.valueOf(gateEnteredAtMs), String.valueOf(controllerEnteredAtMs));
		long code = result == null ? -2 : result;

		if (code == 1) {
			return;
		}
		if (code == -1) {
			throw new CouponException(CouponErrorCode.COUPON_ISSUE_DUPLICATE);
		}
		if (code == -2) {
			throw new CouponException(CouponErrorCode.COUPON_INVENTORY_NOT_STOCKED);
		}
		throw new CouponException(CouponErrorCode.COUPON_SOLD_OUT);
	}

	/**
	 * 발급 순서 기록을 초기화한다. 반드시 "진짜 오픈" 시점에만 호출해야 하며, replenishMissingStock()에서는 호출하면 안 된다.
	 */
	public void resetFairnessLog(long couponId) {
		redisTemplate.delete(CouponStockKeys.fairnessSeqKey(couponId));
		redisTemplate.delete(CouponStockKeys.fairnessLogKey(couponId));
	}

	/**
	 * fairness-log 한 줄을 파싱한 값. redisTimeMs/gateEnteredAtMs/controllerEnteredAtMs는 시각 기록이
	 * 추가되기 전(레거시 3필드 "{순번}:{userId}:{결과}") 항목이면 null이다.
	 */
	public record FairnessLogEntry(long rank, long userId, String outcome, Long redisTimeMs, Long gateEnteredAtMs, Long controllerEnteredAtMs) {
	}

	public List<FairnessLogEntry> fairnessLog(long couponId, long afterRank, int limit) {
		Set<String> raw = redisTemplate.opsForZSet().rangeByScore(CouponStockKeys.fairnessLogKey(couponId), afterRank + 1, Double.POSITIVE_INFINITY, 0, limit + 1);
		if (raw == null) {
			return List.of();
		}
		return raw.stream().map(entry -> {
			String[] parts = entry.split(":", 6);
			boolean hasTimings = parts.length >= 6; // 시각 기록 추가 전에 쌓인 레거시 항목은 3필드뿐이다
			return new FairnessLogEntry(
					Long.parseLong(parts[0]), Long.parseLong(parts[1]), parts[2],
					hasTimings ? Long.parseLong(parts[3]) : null,
					hasTimings ? Long.parseLong(parts[4]) : null,
					hasTimings ? Long.parseLong(parts[5]) : null
			);
		}).toList();
	}

	public CouponFairnessReport analyzeFairness(long couponId) {
		Set<String> entries = redisTemplate.opsForZSet().range(CouponStockKeys.fairnessLogKey(couponId), 0, -1);

		boolean sawFailureBoundary = false;
		long inversions = 0;
		long total = 0;

		if (entries != null) {
			for (String entry : entries) {
				String[] parts = entry.split(":", 6); // 3 → 6으로 변경
				String outcome = parts[2];

				if ("DUPLICATE".equals(outcome))
					continue;

				total++;
				if ("SOLDOUT".equals(outcome)) {
					sawFailureBoundary = true;
				}
				else if ("SUCCESS".equals(outcome) && sawFailureBoundary) {
					inversions++;
				}
			}
		}

		return new CouponFairnessReport(couponId, total, inversions);
	}
}