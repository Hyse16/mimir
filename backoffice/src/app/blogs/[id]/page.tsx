import Link from "next/link";
import { notFound } from "next/navigation";
import { archiveBlogPostAction, duplicateBlogPostAction } from "@/app/blogs/actions";
import { AppShell } from "@/components/app-shell";
import { ConfirmActionForm } from "@/components/confirm-action-form";
import { getBlogPost } from "@/lib/mimir-api";

export const dynamic = "force-dynamic";

type DetailPageProps = {
  params: Promise<{ id: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

export default async function BlogDetailPage({ params, searchParams }: DetailPageProps) {
  const [{ id }, query] = await Promise.all([params, searchParams]);
  const post = await getBlogPost(id);
  if (!post) notFound();

  const returnTo = safeReturnTo(single(query.returnTo));
  const archiveAction = archiveBlogPostAction.bind(null, post.id, returnTo);
  const duplicateAction = duplicateBlogPostAction.bind(null, post.id, returnTo);
  const notice = single(query.notice);
  const error = single(query.error);

  return (
    <AppShell active="블로그">
      <Link className="backLink" href={returnTo}>← 게시글 목록</Link>
      <header className="pageHeader detailHeader">
        <div><p className="eyebrow">BLOG DETAIL</p><h1>{post.title}</h1><p>현재 선택된 초안과 모든 수정 이력을 확인합니다.</p></div>
        <span className={`statusBadge ${post.status.toLowerCase()}`}>{statusLabel(post.status)}</span>
      </header>

      {notice === "duplicated" && <p className="noticeBanner" role="status">독립된 복사본을 만들었습니다.</p>}
      {error && <p className="noticeBanner error" role="alert">요청을 처리하지 못했습니다. 백엔드 연결 상태를 확인해주세요.</p>}

      <section className="detailGrid">
        <article className="panel currentDraft">
          <div className="panelHeader"><div><h2>현재 초안 · v{post.currentVersion.versionNumber}</h2><p>{formatDate(post.currentVersion.createdAt)} 저장</p></div></div>
          <h3>{post.currentVersion.title}</h3>
          <div className="draftBody">{post.currentVersion.body || "본문이 비어 있습니다."}</div>
          <div className="tagList">{post.currentVersion.tags.map((tag) => <span key={tag}>#{tag}</span>)}</div>
        </article>

        <aside className="panel contextPanel">
          <h2>사실 메모</h2>
          <p>{post.visitContext || "등록된 사실 메모가 없습니다."}</p>
          <dl><div><dt>생성</dt><dd>{formatDate(post.createdAt)}</dd></div><div><dt>최근 수정</dt><dd>{formatDate(post.updatedAt)}</dd></div><div><dt>버전 수</dt><dd>{post.versions.length}개</dd></div></dl>
        </aside>
      </section>

      <section className="panel actionPanel">
        <div><h2>게시글 관리</h2><p>복제본은 현재 선택된 초안을 새 게시글의 첫 버전으로 저장합니다.</p></div>
        <div className="actionButtons">
          <ConfirmActionForm action={duplicateAction} label="복제" message="현재 초안을 독립된 새 게시글로 복제할까요?" />
          {post.status !== "ARCHIVED" && <ConfirmActionForm action={archiveAction} label="보관" message="이 게시글을 보관 상태로 변경할까요? 기존 버전은 유지됩니다." tone="danger" />}
        </div>
      </section>

      <section className="panel historyPanel">
        <div className="panelHeader"><div><h2>버전 이력</h2><p>기존 버전은 삭제하거나 덮어쓰지 않습니다.</p></div></div>
        <ol className="versionList">
          {post.versions.map((version) => (
            <li key={version.id}>
              <div><strong>v{version.versionNumber} · {version.title}</strong><small>{formatDate(version.createdAt)} · {version.source}</small></div>
              {version.selected && <span className="selectedBadge">현재 선택</span>}
            </li>
          ))}
        </ol>
      </section>
    </AppShell>
  );
}

function single(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

function safeReturnTo(value: string | undefined) {
  return value === "/blogs" || value?.startsWith("/blogs?") ? value : "/blogs";
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
