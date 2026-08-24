import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { getBlogPosts, getSystemStatus } from "@/lib/mimir-api";

export const dynamic = "force-dynamic";

export default async function Home() {
  const [systemStatus, blogPage, archivedPage] = await Promise.all([
    getSystemStatus(),
    getBlogPosts({ size: 5 }),
    getBlogPosts({ status: "ARCHIVED", size: 1 }),
  ]);
  const recent = blogPage?.items ?? [];
  const archived = archivedPage?.totalItems ?? 0;

  const summary = [
    { label: "전체 게시글", value: blogPage ? String(blogPage.totalItems) : "—", tone: "neutral" },
    { label: "작성 중", value: blogPage ? String(blogPage.totalItems - archived) : "—", tone: "warning" },
    { label: "보관됨", value: blogPage ? String(archived) : "—", tone: "active" },
    { label: "서버 상태", value: systemStatus ? "정상" : "대기", tone: systemStatus ? "neutral" : "danger" },
  ];

  return (
    <AppShell active="대시보드">
      <header className="pageHeader">
        <div>
          <p className="eyebrow">OVERVIEW</p>
          <h1>운영 대시보드</h1>
          <p>애플리케이션에서 실행된 작업과 저장된 결과를 관리합니다.</p>
        </div>
        <div className="connection" role="status">
          <span aria-hidden="true" className={systemStatus ? "statusDot" : "statusDot muted"} />
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
            <Link href="/blogs">목록 보기 →</Link>
          </article>
        ))}
      </section>

      <section className="panel">
        <div className="panelHeader">
          <div>
            <h2>최근 블로그</h2>
            <p>최근 수정된 게시글을 최대 5개 표시합니다.</p>
          </div>
          <Link className="secondaryButton" href="/blogs">전체 보기</Link>
        </div>
        {blogPage === null ? (
          <div className="emptyState"><h3>서버에 연결할 수 없습니다</h3><p>백엔드 실행 상태를 확인해주세요.</p></div>
        ) : recent.length === 0 ? (
          <div className="emptyState"><div aria-hidden="true" className="emptyIcon">◎</div><h3>표시할 게시글이 없습니다</h3><p>Flet 애플리케이션에서 첫 초안을 저장해보세요.</p></div>
        ) : (
          <div className="compactList">
            {recent.map((post) => (
              <Link href={`/blogs/${post.id}`} key={post.id}>
                <span><strong>{post.title}</strong><small>{formatDate(post.updatedAt)}</small></span>
                <span className={`statusBadge ${post.status.toLowerCase()}`}>{statusLabel(post.status)}</span>
              </Link>
            ))}
          </div>
        )}
      </section>
    </AppShell>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Seoul" }).format(new Date(value));
}

function statusLabel(status: string) {
  return ({
    DRAFT: "초안",
    GENERATING: "생성 중",
    REVIEW_REQUIRED: "검토 필요",
    READY: "게시 준비",
    PUBLISHED: "게시 완료",
    ARCHIVED: "보관됨",
  } as Record<string, string>)[status] ?? status;
}
