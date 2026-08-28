interface Props {
  /** 표의 실제 컬럼 수 - 각 스켈레톤 행이 진짜 데이터 행과 같은 칸 수를 갖도록 맞춘다. */
  columns: number;
  rows?: number;
}

/**
 * 데이터를 기다리는 동안 표 틀(헤더)은 그대로 두고 본문에만 채워 넣는 반짝이는 자리표시자.
 * "불러오는 중…" 한 줄 문구 대신 실제 표처럼 보이는 스켈레톤으로, 로딩이 더 눈에 띄고
 * 표 구조가 갑자기 사라졌다 나타나는 깜빡임도 없앤다.
 */
export function HistorySkeletonRows({ columns, rows = 6 }: Props) {
  return (
    <>
      {Array.from({ length: rows }).map((_, i) => (
        <tr key={i} aria-hidden="true">
          {Array.from({ length: columns }).map((_, j) => (
            <td key={j}>
              <span
                className="history-skeleton-bar"
                style={{ width: `${50 + ((i * 17 + j * 11) % 40)}%`, animationDelay: `${(i * 60) % 400}ms` }}
              />
            </td>
          ))}
        </tr>
      ))}
    </>
  );
}
