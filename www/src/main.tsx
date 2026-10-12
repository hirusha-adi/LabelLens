import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { AppShell } from "./app/AppShell";
import { Landing } from "./landing/Landing";
import { useLocation } from "./lib/router";
import "./styles.css";

const Root = () => {
  const { pathname } = useLocation();
  return pathname === "/app" || pathname.startsWith("/app/") ? <AppShell /> : <Landing />;
};

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <Root />
  </StrictMode>,
);
