import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { getBlogPosts } from "@/lib/mimir-api";

export const dynamic = "force-dynamic";

export default async function BlogsPage() {
  const result = await getBlogPosts();

  return (
    <AppShell active="블로그">
      <header className="pageHeader">
        <div><p className="eyebrow">BLOG CONTENT</p><h1>블로그 게시글</h1><p>애플리케이션에서 만든 초안과 상태를 한눈에 확인합니다.</p></div>
        <div className="countBadge">{result ? `총 ${result.totalItems}개` : "연결 대기"}</div>
      </header>

      <section className="panel listPanel">
        {result === null ? (
          <div className="emptyState"><h3>게시글을 불러오지 못했습니다</h3><p>백엔드 연결 후 페이지를 새로고침해주세요.</p></div>
        ) : result.items.length === 0 ? (
          <div className="emptyState"><h3>아직 게시글이 없습니다</h3><p>Flet 애플리케이션에서 실제 작성 작업을 시작해주세요.</p></div>
        ) : (
          <div className="postTable" role="table" aria-label="블로그 게시글 목록">
            <div className="postRow header" role="row"><span>제목</span><span>상태</span><span>최근 수정</span><span>상세</span></div>
            {result.items.map((post) => (
              <div className="postRow" role="row" key={post.id}>
                <span><strong>{post.title}</strong><small>{post.id}</small></span>
                <span><span className={`statusBadge ${post.status.toLowerCase()}`}>{post.status === "ARCHIVED" ? "보관됨" : "작성 중"}</span></span>
                <span>{formatDate(post.updatedAt)}</span>
                <span><Link href={`/blogs/${post.id}`}>이력 보기 →</Link></span>
              </div>
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
