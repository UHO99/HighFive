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
