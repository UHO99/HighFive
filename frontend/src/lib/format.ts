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

/** epoch ms를 밀리초까지 보이는 로컬 시각 문자열로 바꾼다 (예: "10:54:20.123"). */
export function formatTimeMs(epochMs: number): string {
  const d = new Date(epochMs);
  const hh = String(d.getHours()).padStart(2, "0");
  const mm = String(d.getMinutes()).padStart(2, "0");
  const ss = String(d.getSeconds()).padStart(2, "0");
  const sss = String(d.getMilliseconds()).padStart(3, "0");
  return `${hh}:${mm}:${ss}.${sss}`;
}
