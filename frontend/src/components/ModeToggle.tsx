interface Props {
  mode: "admin" | "user";
  onChange: (mode: "admin" | "user") => void;
}

export function ModeToggle({ mode, onChange }: Props) {
  return (
    <div className="mode-toggle">
      <button
        type="button"
        className={`mode-toggle-btn ${mode === "admin" ? "active" : ""}`}
        onClick={() => onChange("admin")}
      >
        관리자
      </button>
      <span className="mode-toggle-sep">|</span>
      <button
        type="button"
        className={`mode-toggle-btn ${mode === "user" ? "active" : ""}`}
        onClick={() => onChange("user")}
      >
        사용자
      </button>
    </div>
  );
}
