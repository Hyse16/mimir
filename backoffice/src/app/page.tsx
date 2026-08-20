import { getSystemStatus } from "@/lib/mimir-api";

export const dynamic = "force-dynamic";

const navigation = ["대시보드", "블로그", "AI 작업", "일정", "연동 상태"];

const summary = [
  { label: "전체 게시글", value: "0", tone: "neutral" },
  { label: "검토 필요", value: "0", tone: "warning" },
  { label: "실행 중 작업", value: "0", tone: "active" },
  { label: "실패한 작업", value: "0", tone: "danger" },
];

export default async function Home() {
  const systemStatus = await getSystemStatus();

  return (
    <main className="shell">
      <aside className="sidebar">
        <div>
          <p className="brand">MIMIR</p>
          <p className="brandCaption">Operations</p>
        </div>
        <nav aria-label="주요 메뉴">
          {navigation.map((item, index) => (
            <a className={index === 0 ? "navItem active" : "navItem"} href="#" key={item}>
              {item}
            </a>
          ))}
        </nav>
        <div className="privacyBadge">
          <span aria-hidden="true" className="statusDot" />
          Local Only
        </div>
      </aside>

      <section className="content">
        <header className="pageHeader">
          <div>
            <p className="eyebrow">OVERVIEW</p>
            <h1>운영 대시보드</h1>
            <p>애플리케이션에서 실행된 작업과 저장된 결과를 관리합니다.</p>
          </div>
          <div className="connection" role="status">
            <span
              aria-hidden="true"
              className={systemStatus ? "statusDot" : "statusDot muted"}
            />
            {systemStatus
              ? `서버 ${systemStatus.status} · DB ${systemStatus.components.database}`
              : "서버 연결 대기"}
          </div>
        </header>

        <section aria-label="업무 요약" className="summaryGrid">
          {summary.map((item) => (
            <article className={`summaryCard ${item.tone}`} key={item.label}>
              <p>{item.label}</p>
              <strong>{item.value}</strong>
              <span>목록 보기 →</span>
            </article>
          ))}
        </section>

        <section className="panel">
          <div className="panelHeader">
            <div>
              <h2>최근 활동</h2>
              <p>아직 실행된 작업이 없습니다.</p>
            </div>
            <button disabled type="button">전체 보기</button>
          </div>
          <div className="emptyState">
            <div aria-hidden="true" className="emptyIcon">◎</div>
            <h3>표시할 활동이 없습니다</h3>
            <p>Flet 애플리케이션에서 블로그 작업을 시작하면 여기에 기록됩니다.</p>
          </div>
        </section>
      </section>
    </main>
  );
}
