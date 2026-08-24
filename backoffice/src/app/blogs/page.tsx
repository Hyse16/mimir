import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { BLOG_POST_STATUSES, getBlogPosts } from "@/lib/mimir-api";

export const dynamic = "force-dynamic";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function BlogsPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams;
  const query = single(params.query)?.trim() ?? "";
  const statusCandidate = single(params.status) ?? "";
  const status = BLOG_POST_STATUSES.includes(statusCandidate as (typeof BLOG_POST_STATUSES)[number])
    ? statusCandidate
    : "";
  const sort = ["updatedAt", "createdAt", "title", "status"].includes(single(params.sort) ?? "")
    ? single(params.sort)!
    : "updatedAt";
  const direction = single(params.direction) === "asc" ? "asc" : "desc";
  const page = positivePage(single(params.page));
  const notice = single(params.notice);
  const result = await getBlogPosts({ query, status, page: page - 1, size: 10, sort, direction });

  const pageHref = (targetPage: number) => {
    const target = new URLSearchParams();
    if (query) target.set("query", query);
    if (status) target.set("status", status);
    if (sort !== "updatedAt") target.set("sort", sort);
    if (direction !== "desc") target.set("direction", direction);
    if (targetPage > 1) target.set("page", String(targetPage));
    const value = target.toString();
    return value ? `/blogs?${value}` : "/blogs";
  };
  const currentListHref = pageHref(page);

  return (
    <AppShell active="블로그">
      <header className="pageHeader">
        <div><p className="eyebrow">BLOG CONTENT</p><h1>블로그 게시글</h1><p>저장된 초안을 검색하고 상태별로 관리합니다.</p></div>
        <div className="countBadge">{result ? `총 ${result.totalItems}개` : "연결 대기"}</div>
      </header>

      {notice === "archived" && <p className="noticeBanner" role="status">게시글을 보관했습니다.</p>}

      <form className="filterBar" method="get">
        <label>
          <span>제목 검색</span>
          <input defaultValue={query} name="query" placeholder="검색어 입력" type="search" />
        </label>
        <label>
          <span>상태</span>
          <select defaultValue={status} name="status">
            <option value="">전체 상태</option>
            {BLOG_POST_STATUSES.map((item) => <option key={item} value={item}>{statusLabel(item)}</option>)}
          </select>
        </label>
        <label>
          <span>정렬 기준</span>
          <select defaultValue={sort} name="sort">
            <option value="updatedAt">최근 수정</option>
            <option value="createdAt">생성일</option>
            <option value="title">제목</option>
            <option value="status">상태</option>
          </select>
        </label>
        <label>
          <span>정렬 방향</span>
          <select defaultValue={direction} name="direction">
            <option value="desc">내림차순</option>
            <option value="asc">오름차순</option>
          </select>
        </label>
        <div className="filterActions">
          <button className="primaryButton" type="submit">적용</button>
          <Link className="secondaryButton" href="/blogs">초기화</Link>
        </div>
      </form>

      <section className="panel listPanel">
        {result === null ? (
          <div className="emptyState"><h3>게시글을 불러오지 못했습니다</h3><p>백엔드 연결 후 페이지를 새로고침해주세요.</p></div>
        ) : result.items.length === 0 ? (
          <div className="emptyState"><h3>{query || status ? "조건에 맞는 게시글이 없습니다" : "아직 게시글이 없습니다"}</h3><p>{query || status ? "검색어나 상태 조건을 변경해주세요." : "Flet 애플리케이션에서 실제 작성 작업을 시작해주세요."}</p></div>
        ) : (
          <>
            <div className="postTable" role="table" aria-label="블로그 게시글 목록">
              <div className="postRow header" role="row"><span>제목</span><span>상태</span><span>최근 수정</span><span>상세</span></div>
              {result.items.map((post) => (
                <div className="postRow" role="row" key={post.id}>
                  <span><strong>{post.title}</strong><small>{post.id}</small></span>
                  <span><span className={`statusBadge ${post.status.toLowerCase()}`}>{statusLabel(post.status)}</span></span>
                  <span>{formatDate(post.updatedAt)}</span>
                  <span><Link href={`/blogs/${post.id}?returnTo=${encodeURIComponent(currentListHref)}`}>이력 보기 →</Link></span>
                </div>
              ))}
            </div>
            {result.totalPages > 1 && (
              <nav aria-label="게시글 페이지" className="pagination">
                {result.page > 0 ? <Link href={pageHref(result.page)}>← 이전</Link> : <span>← 이전</span>}
                <strong>{result.page + 1} / {result.totalPages}</strong>
                {result.page + 1 < result.totalPages ? <Link href={pageHref(result.page + 2)}>다음 →</Link> : <span>다음 →</span>}
              </nav>
            )}
          </>
        )}
      </section>
    </AppShell>
  );
}

function single(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

function positivePage(value: string | undefined) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 1;
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
