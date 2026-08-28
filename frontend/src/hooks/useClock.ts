import { useEffect, useState } from "react";

const CLOCK_TICK_MS = 1000;

/**
 * 1초마다 갱신되는 현재 시각. 대시보드 곳곳(경과시간 표시, "N초 전 갱신" 표시)에서 각자
 * 독립적인 setInterval을 두던 걸 하나로 합치기 위한 공용 훅 - 컴포넌트가 몇 개든 브라우저
 * 타이머는 훅을 호출한 곳마다 하나씩 생기므로, 진짜로 타이머 개수를 줄이려면 이 훅을
 * 최상위(DashboardPage) 한 곳에서만 부르고 값을 props로 내려줘야 한다(2단계에서 진행).
 */
export function useClock(): number {
    const [now, setNow] = useState(() => Date.now());

    useEffect(() => {
        const timer = window.setInterval(() => setNow(Date.now()), CLOCK_TICK_MS);
        return () => window.clearInterval(timer);
    }, []);

    return now;
}