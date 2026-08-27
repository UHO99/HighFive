import type { MouseEvent, ReactNode } from "react";

type ExpandableCardProps = {
  id: string;
  expandedId: string | null;
  onToggle: (id: string) => void;
  children: ReactNode;
};

// 카드 내부의 버튼/셀렉트/링크 등을 클릭했을 때는 확대/축소를 트리거하지 않는다 - 그렇지 않으면
// 정합성 동기화 버튼, 쿠폰 선택 드롭다운 같은 기존 인터랙션이 클릭할 때마다 카드를 확대시켜버린다.
const INTERACTIVE_SELECTOR = 'button, a, select, input, textarea, [role="button"], [data-no-expand]';

export function ExpandableCard({ id, expandedId, onToggle, children }: ExpandableCardProps) {
  const isExpanded = expandedId === id;
  const isDimmed = expandedId !== null && !isExpanded;

  const handleClick = (e: MouseEvent<HTMLDivElement>) => {
    if ((e.target as HTMLElement).closest(INTERACTIVE_SELECTOR)) return;
    onToggle(id);
  };

  return (
    <>
      {isExpanded && <div className="card-expand-backdrop" onClick={() => onToggle(id)} />}
      <div
        className={
          "card-expand-slot" +
          (isExpanded ? " card-expand-slot--expanded" : "") +
          (isDimmed ? " card-expand-slot--dimmed" : "")
        }
        onClick={handleClick}
      >
        {isExpanded && (
          <button
            type="button"
            className="card-expand-close"
            onClick={(e) => {
              e.stopPropagation();
              onToggle(id);
            }}
            aria-label="확대 닫기"
          >
            ✕
          </button>
        )}
        {children}
      </div>
    </>
  );
}
