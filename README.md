# HighFive
[LG U+] 유레카 SW 백엔드 과정 종합 프로젝트 — 선착 쿠폰 발급 시스템
[![Notion](https://img.shields.io/badge/Notion-000000?style=for-the-badge&logo=notion&logoColor=white)](https://app.notion.com/p/5-3c0f18873f92803d9c1bc93c920ea6fe?source=copy_link)

## 목차
1. [실행 방법](#1-실행-방법)
2. [멘토링 질문 리스트](#멘토링-질문-리스트)

## 1. 실행 방법

### 요구사항
- Docker / Docker Compose

### 클론
```bash
git clone https://github.com/UHO99/HighFive.git
cd HighFive
```

### 백엔드 + MySQL + Redis + K6 이미지
```bash
cd backend
docker compose build k6 && docker compose up -d --build
```
대시보드의 "테스트 시작" 버튼은 백엔드가 호스트 도커 소켓으로 k6 컨테이너를 띄워서 동작하는데, `k6` 서비스는
`docker compose up`만으로는 안 만들어진다.
그래서 `build k6`를 먼저 실행해 이미지를 준비한 다음 나머지를 띄운다.

확인:
```bash
curl http://localhost:8080/1
```

기본 계정은 `ureca`/`ureca`입니다. 바꾸고 싶으면 실행 시 환경변수로 넘기면 됩니다:
```bash
DB_USER=myuser DB_PASSWORD=mypass docker compose up -d
```

종료:
```bash
docker compose down       # 컨테이너만 정지 (데이터 유지)
docker compose down -v    # DB 데이터까지 초기화
```

### K6 시나리오 추가
시나리오 목록은 `backend/k6/` 디렉터리를 스캔하는 게 아니라, 백엔드의 `K6Scenario` enum
(`global/common/enums/K6Scenario.java`)에 정의된 것만 대시보드에 뜨고 실행도 된다 - 클라이언트가 보낸
시나리오 id로 임의의 파일을 실행시키지 못하게 막는 화이트리스트다. 그래서 새 시나리오를 추가하려면:

1. `backend/k6/내스크립트.js` 작성. `BASE_URL`/`COUPON_ID`를 기존 스크립트들과 같은 방식으로 읽어야 한다:
   ```js
   const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
   const COUPON_ID = __ENV.COUPON_ID || '1';
   ```
2. `K6Scenario` enum에 상수 추가 (`id, file, name, description, rampUp, hold, targetVus`) - 대시보드
   시나리오 다이얼로그에 그대로 노출되는 값들이다.
3. 백엔드 재빌드 + k6 이미지 재빌드 (둘 다 해야 목록/실행이 새 시나리오를 반영한다):
   ```bash
   docker compose build k6 && docker compose up -d --build
   ```
   enum엔 추가했는데 이미지 빌드를 깜빡하면, 실행 시 "아직 이미지에 반영되지 않았습니다" 에러로 바로
   알려준다.

### 프론트엔드 (대시보드)
```bash
https://uho99.github.io/HighFive/
```
상단 URL에서 대시보드를 확인합니다. 

## 2. 멘토링 질문 리스트 (08.25 화요일)

