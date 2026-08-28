export type AdminTab = "server" | "coupon" | "pipeline" | "history";

interface Props {
  active: AdminTab;
  onChange: (tab: AdminTab) => void;
}

const TABS: { id: AdminTab; label: string }[] = [
  { id: "server", label: "서버 리소스" },
  { id: "coupon", label: "쿠폰 로그" },
  { id: "pipeline", label: "발급 파이프라인" },
  { id: "history", label: "전체 쿠폰 발급 이력" },
];

/**
 * 관리자 모드일 때 상단 ModeToggle 바로 아래에 항상 떠 있는 페이지 전환 캡슐.
 * ModeToggle과 같은 디자인(흰 캡슐 · 구분선 · 선택 시 짙은 남색 채움)을 그대로 이어 쓴다.
 */
export function AdminTabs({ active, onChange }: Props) {
  return (
    <nav className="admin-tabs" aria-label="관리자 페이지 전환">
      {TABS.map((tab, i) => (
        <div key={tab.id} style={{ display: "contents" }}>
          {i > 0 && <span className="admin-tabs-sep">|</span>}
          <button
            type="button"
            className={`admin-tabs-item ${active === tab.id ? "active" : ""}`}
            onClick={() => onChange(tab.id)}
          >
            {tab.label}
          </button>
        </div>
      ))}
    </nav>
  );
}
