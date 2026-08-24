import { useState } from "react";
import { ModeToggle } from "./components/ModeToggle";
import { DashboardPage } from "./pages/DashboardPage";
import { UserPage } from "./pages/UserPage";

export function App() {
  const [mode, setMode] = useState<"admin" | "user">("admin");

  return (
    <>
      <ModeToggle mode={mode} onChange={setMode} />
      {mode === "admin" ? <DashboardPage /> : <UserPage />}
    </>
  );
}
