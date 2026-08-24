import type { K6ScenarioDto } from "./api";

/**
 * backend/k6/*.js 시나리오 메타데이터. 백엔드 K6Scenario enum이 유일한 소스이고(화이트리스트 역할도 겸함),
 * 프런트는 GET /api/admin/k6/scenarios로 목록을 받아와 그대로 쓴다 - 하드코딩된 목록이 실제 파일과
 * 어긋나는 걸 막기 위함.
 */
export type K6Scenario = K6ScenarioDto;
