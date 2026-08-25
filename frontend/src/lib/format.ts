export function fmt(n: number): string {
  return Math.round(n).toLocaleString("ko-KR");
}

export function lerp(cur: number, target: number, factor: number): number {
  return cur + (target - cur) * factor;
}

export function jitter(v: number, pct: number): number {
  return v * (1 + (Math.random() - 0.5) * pct);
}

export function colorFor(v: number, warn: number, danger: number): string {
  return v >= danger ? "#dc2626" : v >= warn ? "#e0821f" : "#16a34a";
}

export function formatMs(ms: number): string {
  return ms < 1000 ? `${Math.round(ms)}ms` : `${(ms / 1000).toFixed(1)}s`;
}

/**
 * epoch 밀리초를 밀리초 단위까지 보이는 시각 문자열로 변환한다. toLocaleTimeString()은
 * 초 단위까지만 보여주므로, rank(처리 순번)와 나란히 비교할 절대 시각을 눈으로 확인하려면
 * 밀리초 자체가 보여야 한다.
 */
export function formatTimestampMs(ms: number): string {
  const date = new Date(ms);
  const time = date.toLocaleTimeString("ko-KR", { hour12: false });
  const millis = String(date.getMilliseconds()).padStart(3, "0");
  return `${time}.${millis}`;
}

/**
 * epoch 마이크로초(Redis TIME 명령)를 밀리초+마이크로초까지 보이는 시각 문자열로 변환한다.
 * Date는 밀리초 단위까지만 다루므로, 마이크로초 나머지는 별도로 떼어 붙인다.
 */
export function formatTimestampMicros(micros: number): string {
  const ms = Math.floor(micros / 1000);
  const remainderMicros = micros % 1000;
  const date = new Date(ms);
  const time = date.toLocaleTimeString("ko-KR", { hour12: false });
  const millis = String(date.getMilliseconds()).padStart(3, "0");
  const microsStr = String(remainderMicros).padStart(3, "0");
  return `${time}.${millis}${microsStr}`;   // 예: "11:54:14.821487"
}
