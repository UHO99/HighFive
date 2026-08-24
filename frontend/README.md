# HighFive · 부하 테스트 모니터링 대시보드

대규모 트래픽 선착순 쿠폰 발급 시스템의 운영 모니터링 대시보드. React + Vite + TypeScript.

## 현재 상태

**목데이터로만 동작합니다.** 백엔드 API 연동은 아직 하지 않았고, `src/hooks/useDashboardSimulation.ts`가 IDLE/LOAD 두 기준값 사이를 매초 보간(lerp)하며 그럴듯한 수치를 만들어낸다. 화면 하단의 "테스트 시작" 버튼은 실제 k6를 실행하지 않고 LOAD 기준값으로 전환하는 시뮬레이션 스위치일 뿐이다.

추후 실제 API를 연동할 때는 `useDashboardSimulation`을 폴링 기반 fetch 훅으로 교체하면 된다 — 각 카드 컴포넌트(`src/components/*.tsx`)는 `DashboardVals` 형태의 객체만 소비하므로, 그 형태(필드명)를 유지한 채 값을 만드는 방식만 실제 API 응답 매핑으로 바꾸면 컴포넌트 쪽은 손댈 필요가 없다.

## 개발

```bash
npm install
npm run dev       # http://localhost:5173
npm run build      # dist/ 로 빌드 (타입체크 포함)
npm run preview    # 빌드 결과 로컬 확인
```

## 배포

`main` 브랜치의 `frontend/**` 변경 시 `.github/workflows/deploy-frontend.yml`이 GitHub Pages로 자동 배포한다. 저장소 Settings → Pages → Source를 "GitHub Actions"로 설정해야 동작한다.
