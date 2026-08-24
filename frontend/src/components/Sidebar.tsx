export function Sidebar() {
  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <div className="sidebar-logo" />
        <span className="sidebar-brand-name">HighFive</span>
      </div>

      <span className="sidebar-section-label">모니터링</span>
      <nav className="sidebar-nav">
        <div className="sidebar-nav-item active">대시보드</div>
        <div className="sidebar-nav-item">쿠폰 발급 이력 · 선착순</div>
        <div className="sidebar-nav-item">시스템 상태</div>
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
