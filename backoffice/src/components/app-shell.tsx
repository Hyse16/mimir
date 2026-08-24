import Link from "next/link";
import type { ReactNode } from "react";

const navigation = [
  { label: "대시보드", href: "/" },
  { label: "블로그", href: "/blogs" },
  { label: "AI 작업", href: "#" },
  { label: "일정", href: "#" },
  { label: "연동 상태", href: "#" },
];

export function AppShell({ active, children }: { active: string; children: ReactNode }) {
  return (
    <main className="shell">
      <aside className="sidebar">
        <div>
          <p className="brand">MIMIR</p>
          <p className="brandCaption">Operations</p>
        </div>
        <nav aria-label="주요 메뉴">
          {navigation.map((item) =>
            item.href === "#" ? (
              <span className="navItem disabled" key={item.label}>{item.label}</span>
            ) : (
              <Link
                className={active === item.label ? "navItem active" : "navItem"}
                href={item.href}
                key={item.label}
              >
                {item.label}
              </Link>
            ),
          )}
        </nav>
        <div className="privacyBadge">
          <span aria-hidden="true" className="statusDot" />
          Local Only
        </div>
      </aside>
      <section className="content">{children}</section>
    </main>
  );
}
