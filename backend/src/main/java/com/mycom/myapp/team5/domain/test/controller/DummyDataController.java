package com.mycom.myapp.team5.domain.test.controller;

import java.util.List;

import com.mycom.myapp.team5.domain.test.dto.DummyDataStatus;
import com.mycom.myapp.team5.domain.test.exception.DummyDataErrorCode;
import com.mycom.myapp.team5.domain.test.exception.DummyDataException;
import com.mycom.myapp.team5.domain.test.service.DummyDataLoadService;
import com.mycom.myapp.team5.global.common.util.DummyDataAll;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.domain.user.repository.UserRepository;
import com.mycom.myapp.team5.global.common.dto.ApiResponse;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;

import lombok.RequiredArgsConstructor;

/**
 * 더미데이터 재적재용 관리자 API
 *
 * <p>OPEN 쿠폰이 있으면 거부한다 — TRUNCATE 가 스케줄러(S012/S013, Stream Consumer)와
 * 같은 테이블을 동시에 건드릴 수 있는 유일한 상황이 "진행 중인 캠페인이 있을 때"이기
 * 때문. OPEN 쿠폰이 없으면 스케줄러가 그 테이블을 건드릴 일이 없어 안전하다.
 */
@RestController
@RequiredArgsConstructor
public class DummyDataController {

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final UserRepository userRepository;
    private final DummyDataLoadService dummyDataLoadService;

    @GetMapping("/api/admin/dummy-data/counts")
    public ResponseEntity<ApiResponse<DummyDataAll.Counts>> counts() {
        DummyDataAll.Counts counts = DummyDataAll.Counts.snapshot(
                userRepository.count(), couponRepository.count(), couponIssueRepository.count());
        return ResponseEntity.ok(ApiResponse.success(counts));
    }

    // 적재 진행 상태 - 새로고침해도 "적재 중"인지 알 수 있도록 폴링용으로 둔다(로컬 UI 상태가 아님).
    @GetMapping("/api/admin/dummy-data/status")
    public ResponseEntity<ApiResponse<DummyDataStatus>> status() {
        DummyDataStatus status = dummyDataLoadService.status();

        // before/lastResult 둘 다 null = 이 백엔드 프로세스가 뜬 뒤로 재적재를 한 번도 안 했다는 뜻.
        // dummyDataLoadService.current는 메모리 상태라 백엔드가 재시작되면 사라지지만, DB(MySQL
        // 볼륨)에는 이전에 적재해둔 데이터가 그대로 남아있을 수 있다 - 그 상태를 그대로 "초기 적재"
        // 값으로 보여준다. DB가 실제로 비어 있으면(한 번도 적재 안 함) 0/0/0이 그대로 나온다.
        if (status.before() == null && status.lastResult() == null) {
            DummyDataAll.Counts initial = DummyDataAll.Counts.snapshot(
                    userRepository.count(), couponRepository.count(), couponIssueRepository.count());
            status = new DummyDataStatus(
                    status.loading(), status.startedAt(), status.finishedAt(), initial, status.lastResult(), status.lastError());
        }

        return ResponseEntity.ok(ApiResponse.success(status));
    }

    // 적재는 수십 초 걸리므로 백그라운드로 시작만 시키고 바로 202를 반환한다 - 진행/완료 여부는
    // /status를 폴링해서 확인한다.
    @PostMapping("/api/admin/dummy-data/reload")
    public ResponseEntity<ApiResponse<DummyDataStatus>> reload() {
        List<Coupon> open = couponRepository.findByStatus(CouponStatus.OPEN);
        if (!open.isEmpty()) {
            throw new DummyDataException(DummyDataErrorCode.OPEN_COUPON_EXISTS);
        }

        // TRUNCATE로 지워지기 직전의 상태를 남겨서, 대시보드가 적재 전/후를 비교할 수 있게 한다.
        DummyDataAll.Counts before = DummyDataAll.Counts.snapshot(
                userRepository.count(), couponRepository.count(), couponIssueRepository.count());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(dummyDataLoadService.start(before)));
    }
}
