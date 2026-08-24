package com.mycom.myapp.team5.global.config;

import com.mycom.myapp.team5.global.redis.CouponStreamKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    private final StringRedisTemplate stringRedisTemplate;

    // 컨슈머 그룹을 매 사이클 다시 만들려고 시도하지 않도록 캐싱한다. retireStream()으로 정리된 스트림은 여기서도 제거된다.
    private final Set<String> managedGroups = ConcurrentHashMap.newKeySet();

    public void ensureConsumerGroup(String streamKey) {
        if (!managedGroups.add(streamKey)) {
            return;
        }
        try {
            stringRedisTemplate
                    .opsForStream()
                    .createGroup(streamKey, ReadOffset.from("0"), CouponStreamKeys.CONSUMER_GROUP);
        } catch (RedisSystemException e) {
            log.debug("Consumer 그룹 이미 존재 : streamKey={}, group={}", streamKey, CouponStreamKeys.CONSUMER_GROUP);
        }
    }

    /**
     * S013이 어떤 쿠폰의 정합성을 확정 지은 뒤 호출한다. 발급 요청 스트림은 XACK로도 지워지지 않고
     * 계속 쌓이기만 하므로(ACK는 PEL에서만 제거), 더 이상 볼 일이 없어진 시점에 스트림/컨슈머 그룹 자체를
     * 명시적으로 삭제해야 Redis 메모리가 쿠폰 수만큼 무한정 누적되지 않는다.
     *
     * 삭제 직전에 "컨슈머 그룹이 스트림 끝까지 다 읽었는가"를 한 번 더 확인한다 - 재고 차감(Lua)과 XADD가
     * 원자적으로 묶여있지 않아서, "PEL=0으로 드레인 판정"과 "아직 XADD도 안 된 뒤늦은 요청이 뒤늦게
     * 들어오는 것" 사이에 아주 좁은 틈이 존재한다. 그 틈에 걸린 엔트리가 있는데도 그냥 지워버리면 조용히,
     * 영구히 유실된다. 아직 다 못 읽었으면 삭제를 포기하고 다음 사이클(다음 검증 주기)로 미룬다 -
     * 데이터를 지울지 확신이 없으면 지우지 않는 쪽으로 fail-safe.
     *
     * 반환값이 false면 호출자는 "정합성 확정" 자체를 보류해야 한다 - 그래야 다음 검증 주기에 이 스트림을
     * 다시 들여다본다. true를 반환했을 때만 확정 처리를 하도록 순서를 지켜야 가드가 의미가 있다.
     */
    public boolean retireStream(String streamKey) {
        if (hasUndeliveredEntries(streamKey)) {
            log.error("Redis Stream 정리 보류 - 컨슈머 그룹이 아직 못 읽은 엔트리가 남아있음(재고 차감/XADD 사이 경합 의심). streamKey={}", streamKey);
            return false;
        }

        try {
            stringRedisTemplate.opsForStream().destroyGroup(streamKey, CouponStreamKeys.CONSUMER_GROUP);
        } catch (RedisSystemException e) {
            log.debug("Consumer 그룹이 이미 없음 : streamKey={}, group={}", streamKey, CouponStreamKeys.CONSUMER_GROUP);
        }
        stringRedisTemplate.delete(streamKey);
        managedGroups.remove(streamKey);
        log.info("Redis Stream 정리 완료 - streamKey={}", streamKey);
        return true;
    }

    // XACK는 PEL(배달됐지만 처리 안 된 것)에서만 지울 뿐 스트림 본체에서는 안 지우므로, 정상적으로
    // 처리를 끝낸 스트림도 XLEN은 절대 0이 되지 않는다. "그룹이 스트림 끝까지 다 읽었는가"
    // (그룹의 lastDeliveredId == 스트림의 lastGeneratedId)를 봐야 남은 게 없다고 확신할 수 있다.
    //
    // 그룹이 아예 없는 경우(.orElse)는 "재고 차감(Lua)과 XADD 사이 경합" 레이스와는 무관하다 -
    // 그 레이스는 그룹이 이미 존재해야(=쿠폰이 한 번이라도 OPEN돼야) 성립하는데, retireStream()의
    // 대상은 전부 CLOSE 쿠폰이라 새 발급(XADD)이 들어올 수 없다. 즉 그룹이 없는 상태에서 "아직
    // 처리 중"일 가능성은 없고, 남은 설명은 ① 그룹 없이 스트림에만 메시지가 들어간 이례적 경로
    // (테스트/수동 스크립트 등), ② destroyGroup()은 성공하고 delete(streamKey) 직전에 중단된
    // retireStream() 재시도 상황 둘 중 하나뿐이며 - 둘 다 지워도 안전하다. 그래서 여기서는 false
    // (드레인된 것으로 취급)로 판단한다. 실제 레이스 보호는 위 .map() 분기(그룹이 있을 때 마지막
    // 전달 ID 비교)가 담당한다.
    private boolean hasUndeliveredEntries(String streamKey) {
        StreamInfo.XInfoStream streamInfo;
        try {
            streamInfo = stringRedisTemplate.opsForStream().info(streamKey);
        } catch (RedisSystemException e) {
            return false; // 스트림 자체가 없음 - 지울 것도 없으니 안전
        }
        if (streamInfo.streamLength() == 0) {
            return false;
        }

        return stringRedisTemplate.opsForStream().groups(streamKey).stream()
                .filter(group -> CouponStreamKeys.CONSUMER_GROUP.equals(group.groupName()))
                .findFirst()
                .map(group -> !group.lastDeliveredId().equals(streamInfo.lastGeneratedId()))
                .orElse(false); // 엔트리는 있는데 그룹이 없다 - CLOSE 쿠폰이라 더 들어올 요청도 없으므로 드레인된 것으로 취급
    }

}
