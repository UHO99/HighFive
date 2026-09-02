import { useState } from "react";
import { ModeToggle } from "./components/ModeToggle";
import { AdminTabs, type AdminTab } from "./components/AdminTabs";
import { DashboardPage } from "./pages/DashboardPage";
import { UserPage } from "./pages/UserPage";

export function App() {
  const [mode, setMode] = useState<"admin" | "user">("admin");
  const [adminTab, setAdminTab] = useState<AdminTab>("server");

  return (
    <>
      <ModeToggle mode={mode} onChange={setMode} />
      {mode === "admin" && <AdminTabs active={adminTab} onChange={setAdminTab} />}
      {mode === "admin" ? <DashboardPage activeTab={adminTab} /> : <UserPage />}
    </>
  );
}
