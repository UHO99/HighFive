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

/**
 * Redis Lua 스크립트로 "1인 1매 확인"과 "재고 원자적 차감"을 하나의 원자적 연산으로 묶는다. 두 검사를 별도 호출로 나누면 그 사이에 실패 시 수동으로 되돌리는 보상 로직이 필요한데, Lua 스크립트는 Redis 안에서 끊기지 않고 통째로 실행되므로 그 문제가 아예 발생하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class CouponStockRedisService {

	private final StringRedisTemplate redisTemplate;

	// 반환값: 1 = 발급 성공, 0 = 품절, -1 = 이미 발급됨(중복), -2 = 재고 미적재(쿠폰 오픈 안 됨)
	private static final String ISSUE_SCRIPT = "local function record(result) " + //
			"  local seq = redis.call('incr', KEYS[3]) " + //
			"  redis.call('zadd', KEYS[4], seq, seq .. ':' .. ARGV[1] .. ':' .. result) " + //
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
	 * 발급을 시도한다. 성공하면 조용히 반환하고(Redis 안에서 재고 차감+1인1매 기록이 끝난 상태), 실패 사유별로 다른 CouponException을 던진다.
	 */
	public void issue(long couponId, long userId) {
		List<String> keys = List.of( //
				CouponStockKeys.stockKey(couponId), //
				CouponStockKeys.issuedSetKey(couponId), //
				CouponStockKeys.fairnessSeqKey(couponId), //
				CouponStockKeys.fairnessLogKey(couponId));

		Long result = redisTemplate.execute(ISSUE, keys, String.valueOf(userId));
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
		// code == 0
		throw new CouponException(CouponErrorCode.COUPON_SOLD_OUT);
	}

	/**
	 * 발급 순서 기록을 초기화한다. 반드시 "진짜 오픈" 시점에만 호출해야 하며, replenishMissingStock()(Redis 재시작 복구)에서는 호출하면 안 된다. (진행 중인 테스트의 기록이 유실되기 때문)
	 */
	public void resetFairnessLog(long couponId) {
		redisTemplate.delete(CouponStockKeys.fairnessSeqKey(couponId));
		redisTemplate.delete(CouponStockKeys.fairnessLogKey(couponId));
	}

	/** fairness-log 한 줄("순번:userId:결과")을 파싱한 값. */
	public record FairnessLogEntry(long rank, long userId, String outcome) { }

	/**
	 * 대시보드 "쿠폰 발급 이력 · 선착순" 카드용 - 최근 순번(seq) 역순으로 최대 limit건. 이 seq는
	 * Lua 스크립트 안에서 재고 차감/1인1매 검사와 같은 원자적 연산으로 매겨지므로, DB의 issued_at(비동기
	 * Stream 배치 반영이라 순서가 흔들릴 수 있음)보다 실제 처리 순서를 더 정확히 보여준다.
	 */
	public List<FairnessLogEntry> recentFairnessLog(long couponId, int limit) {
		Set<String> raw = redisTemplate.opsForZSet().range(CouponStockKeys.fairnessLogKey(couponId), 0, limit - 1);
		if (raw == null) {
			return List.of();
		}
		return raw.stream()
				.map(entry -> {
					String[] parts = entry.split(":", 3);
					return new FairnessLogEntry(Long.parseLong(parts[0]), Long.parseLong(parts[1]), parts[2]);
				})
				.toList();
	}

	public CouponFairnessReport analyzeFairness(long couponId) {
		Set<String> entries = redisTemplate.opsForZSet().range(CouponStockKeys.fairnessLogKey(couponId), 0, -1); // 순번(score) 오름차순

		boolean sawFailureBoundary = false;
		long inversions = 0;
		long total = 0;

		if (entries != null) {
			for (String entry : entries) {
				String[] parts = entry.split(":", 3);
				String outcome = parts[2];

				if ("DUPLICATE".equals(outcome))
					continue; // 1인 1매 위반은 공정성 판단과 무관

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
