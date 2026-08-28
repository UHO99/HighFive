interface Props {
  remaining: number;
  total: number;
}

const SIZE = 240;
const STROKE = 20;
const RADIUS = (SIZE - STROKE) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

export function StockRing({ remaining, total }: Props) {
  const pct = total > 0 ? Math.max(0, Math.min(1, remaining / total)) : 0;
  const offset = CIRCUMFERENCE * (1 - pct);
  const color = pct > 0.3 ? "#16a34a" : pct > 0.1 ? "#e0821f" : "#dc2626";

  return (
    <svg width={SIZE} height={SIZE} viewBox={`0 0 ${SIZE} ${SIZE}`} className="stock-ring">
      <circle cx={SIZE / 2} cy={SIZE / 2} r={RADIUS} fill="none" stroke="#eef0f4" strokeWidth={STROKE} />
      <circle
        cx={SIZE / 2}
        cy={SIZE / 2}
        r={RADIUS}
        fill="none"
        stroke={color}
        strokeWidth={STROKE}
        strokeLinecap="round"
        strokeDasharray={CIRCUMFERENCE}
        strokeDashoffset={offset}
        transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}
        style={{ transition: "stroke-dashoffset 0.4s ease" }}
      />
      <text x="50%" y="46%" textAnchor="middle" className="stock-ring-value">
        {remaining.toLocaleString()}
      </text>
      <text x="50%" y="61%" textAnchor="middle" className="stock-ring-total">
        / {total.toLocaleString()}
      </text>
    </svg>
  );
}
