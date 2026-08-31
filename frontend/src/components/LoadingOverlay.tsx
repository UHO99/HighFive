interface Props {
  message: string;
  startedAt?: string | null;
  now?: number;
}

export function LoadingOverlay({ message, startedAt, now }: Props) {
  const elapsedText = (() => {
    if (!startedAt || !now) return null;
    const elapsedMs = now - Date.parse(startedAt);
    if (elapsedMs < 0) return null;
    const totalSeconds = Math.floor(elapsedMs / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${String(seconds).padStart(2, "0")}`;
  })();

  return (
    <div className="loading-overlay">
      <div className="loading-overlay-box">
        <div className="loading-overlay-spinner" />
        <span className="loading-overlay-message">{message}</span>
        {elapsedText && <span className="loading-overlay-timer">{elapsedText}</span>}
      </div>
    </div>
  );
}