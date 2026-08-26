import type { KeyboardEvent } from "react";

interface Props {
  /** 더미데이터가 한 번도 적재되지 않았으면 발급 이력 자체가 의미 없다 - FAB의 다른 기능들과 같은 기준. */
  couponHistoryDisabled: boolean;
  onOpenCouponHistory: () => void;
}

export function Sidebar({ couponHistoryDisabled, onOpenCouponHistory }: Props) {
  const handleCouponHistoryKeyDown = (e: KeyboardEvent) => {
    if (couponHistoryDisabled) return;
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      onOpenCouponHistory();
    }
  };

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <div className="sidebar-logo" />
        <span className="sidebar-brand-name">HighFive</span>
      </div>

      <span className="sidebar-section-label">모니터링</span>
      <nav className="sidebar-nav">
        <div className="sidebar-nav-item active">대시보드</div>
        <div
          className={`sidebar-nav-item${couponHistoryDisabled ? " sidebar-nav-item-disabled" : ""}`}
          role="button"
          tabIndex={couponHistoryDisabled ? -1 : 0}
          onClick={couponHistoryDisabled ? undefined : onOpenCouponHistory}
          onKeyDown={handleCouponHistoryKeyDown}
          title={couponHistoryDisabled ? "먼저 데이터 적재를 완료하세요" : undefined}
        >
          전체 쿠폰 발급 이력
        </div>
      </nav>

      <div className="sidebar-profile">
        <div className="sidebar-avatar">관</div>
        <div className="sidebar-profile-info">
          <span className="sidebar-profile-name">운영 관리자</span>
          <span className="sidebar-profile-role">Backend Team</span>
        </div>
      </div>
    </aside>
  );
}
