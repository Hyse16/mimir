import Link from "next/link";
import { notFound } from "next/navigation";
import {
  archiveBlogPostAction,
  deleteBlogAssetAction,
  duplicateBlogPostAction,
  reorderBlogAssetsAction,
  saveBlogDraftAction,
  startDraftGenerationAction,
  startImageAnalysisAction,
  cancelImageAnalysisAction,
  retryImageAnalysisAction,
  updateBlogStatusAction,
} from "@/app/blogs/actions";
import { AppShell } from "@/components/app-shell";
import { ConfirmActionForm } from "@/components/confirm-action-form";
import { JobLiveProgress } from "@/components/job-live-progress";
import { BLOG_POST_STATUSES, getAiJob, getBlogPost } from "@/lib/mimir-api";

export const dynamic = "force-dynamic";

type DetailPageProps = {
  params: Promise<{ id: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

export default async function BlogDetailPage({ params, searchParams }: DetailPageProps) {
  const [{ id }, query] = await Promise.all([params, searchParams]);
  const jobId = single(query.jobId);
  const [post, job] = await Promise.all([getBlogPost(id), jobId ? getAiJob(jobId) : null]);
  if (!post) notFound();
  const selectedJob = job?.blogPostId === post.id ? job : null;
  const selectedImageJob = selectedJob?.jobType === "IMAGE_ANALYSIS" ? selectedJob : null;
  const selectedDraftJob = selectedJob?.jobType === "BLOG_DRAFT_GENERATION" ? selectedJob : null;

  const returnTo = safeReturnTo(single(query.returnTo));
  const archiveAction = archiveBlogPostAction.bind(null, post.id, returnTo);
  const duplicateAction = duplicateBlogPostAction.bind(null, post.id, returnTo);
  const saveAction = saveBlogDraftAction.bind(null, post.id, returnTo);
  const statusAction = updateBlogStatusAction.bind(null, post.id, returnTo);
  const analysisAction = startImageAnalysisAction.bind(null, post.id, returnTo);
  const draftGenerationAction = startDraftGenerationAction.bind(null, post.id, returnTo);
  const assetIds = post.assets.map((asset) => asset.id);
  const uploadAction = `/api/blog-posts/${encodeURIComponent(post.id)}/assets?returnTo=${encodeURIComponent(returnTo)}`;
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
      {notice === "saved" && <p className="noticeBanner" role="status">수정 내용을 새 버전으로 저장했습니다.</p>}
      {notice === "status" && <p className="noticeBanner" role="status">게시글 상태를 변경했습니다.</p>}
      {notice === "asset-upload" && <p className="noticeBanner" role="status">이미지를 업로드했습니다.</p>}
      {notice === "asset-order" && <p className="noticeBanner" role="status">이미지 순서를 변경했습니다.</p>}
      {notice === "asset-delete" && <p className="noticeBanner" role="status">이미지를 삭제했습니다.</p>}
      {notice === "analysis-start" && <p className="noticeBanner" role="status">이미지 분석 작업을 시작했습니다.</p>}
      {notice === "analysis-retry" && <p className="noticeBanner" role="status">실패 이미지 재분석을 시작했습니다.</p>}
      {notice === "analysis-cancel" && <p className="noticeBanner" role="status">이미지 분석 취소를 요청했습니다.</p>}
      {notice === "draft-generation-start" && <p className="noticeBanner" role="status">로컬 AI 초안 생성 작업을 시작했습니다.</p>}
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

      <section className="panel analysisPanel">
        <div className="panelHeader">
          <div><h2>이미지 분석</h2><p>분석용 파생본을 최대 3장씩 순차 처리하고 결과를 사진별로 저장합니다.</p></div>
          <form action={analysisAction}>
            <button
              className="primaryButton"
              disabled={post.assets.length === 0 || selectedImageJob?.status === "WAITING" || selectedImageJob?.status === "RUNNING" || selectedImageJob?.status === "CANCEL_REQUESTED"}
              type="submit"
            >{selectedImageJob?.status === "WAITING" || selectedImageJob?.status === "RUNNING" || selectedImageJob?.status === "CANCEL_REQUESTED" ? "분석 진행 중" : "전체 이미지 분석"}</button>
          </form>
        </div>
        {selectedImageJob ? (
          <>
            <div className="jobSummary">
              <JobLiveProgress
                eventUrl={`/api/jobs/${encodeURIComponent(selectedImageJob.id)}/events`}
                initialJob={selectedImageJob}
                key={selectedImageJob.id}
              />
              <Link className="actionButton" href={`/blogs/${encodeURIComponent(post.id)}?returnTo=${encodeURIComponent(returnTo)}&jobId=${encodeURIComponent(selectedImageJob.id)}`}>상태 새로고침</Link>
              {(selectedImageJob.status === "WAITING" || selectedImageJob.status === "RUNNING" || selectedImageJob.status === "CANCEL_REQUESTED") && (
                <form action={cancelImageAnalysisAction.bind(null, post.id, returnTo, selectedImageJob.id)}>
                  <button className="actionButton" type="submit">분석 취소</button>
                </form>
              )}
              {(selectedImageJob.status === "PARTIAL_FAILED" || selectedImageJob.status === "FAILED") && (
                <form action={retryImageAnalysisAction.bind(null, post.id, returnTo, selectedImageJob.id)}>
                  <button className="actionButton" type="submit">실패 이미지 재분석</button>
                </form>
              )}
            </div>
            <ol className="analysisList">
              {selectedImageJob.items.map((item) => (
                <li key={item.assetId}>
                  <span className="assetOrder">{item.displayOrder + 1}</span>
                  <span>
                    <strong>{analysisItemLabel(item.status, item.analysis?.category)}</strong>
                    <small>{item.analysis?.description ?? item.errorCode ?? "분석 대기 중"}</small>
                    {item.analysis && item.analysis.objects.length > 0 && <small>{item.analysis.objects.join(", ")}</small>}
                  </span>
                </li>
              ))}
            </ol>
          </>
        ) : (
          <div className="emptyState"><h3>선택된 분석 작업이 없습니다</h3><p>이미지를 분석하면 진행 상태와 구조화 결과가 여기에 표시됩니다.</p></div>
        )}
      </section>

      <section className="panel editPanel">
        <div className="panelHeader">
          <div><h2>로컬 AI 초안 수정</h2><p>현재 선택 버전과 사실 메모, 완료된 이미지 분석만 사용해 새 버전을 생성합니다.</p></div>
        </div>
        <form action={draftGenerationAction} className="draftForm">
          <input name="baseVersionId" type="hidden" value={post.currentVersionId} />
          <label>
            <span>수정 지시</span>
            <textarea
              defaultValue="사실은 유지하고 편안한 존댓말의 네이버 블로그 글로 다듬어줘."
              maxLength={10000}
              name="revisionInstruction"
              required
              rows={3}
            />
          </label>
          <div className="formActions">
            <button
              className="primaryButton"
              disabled={selectedDraftJob?.status === "WAITING" || selectedDraftJob?.status === "RUNNING" || selectedDraftJob?.status === "CANCEL_REQUESTED"}
              type="submit"
            >{selectedDraftJob?.status === "WAITING" || selectedDraftJob?.status === "RUNNING" || selectedDraftJob?.status === "CANCEL_REQUESTED" ? "초안 생성 중" : "AI 새 버전 생성"}</button>
          </div>
        </form>
        {selectedDraftJob && (
          <div className="jobSummary">
            <JobLiveProgress
              eventUrl={`/api/jobs/${encodeURIComponent(selectedDraftJob.id)}/events`}
              initialJob={selectedDraftJob}
              key={selectedDraftJob.id}
            />
            <Link className="actionButton" href={`/blogs/${encodeURIComponent(post.id)}?returnTo=${encodeURIComponent(returnTo)}&jobId=${encodeURIComponent(selectedDraftJob.id)}`}>상태 새로고침</Link>
            {(selectedDraftJob.status === "WAITING" || selectedDraftJob.status === "RUNNING" || selectedDraftJob.status === "CANCEL_REQUESTED") && (
              <form action={cancelImageAnalysisAction.bind(null, post.id, returnTo, selectedDraftJob.id)}>
                <button className="actionButton" type="submit">생성 취소</button>
              </form>
            )}
            {selectedDraftJob.errorCode && <small>오류 코드: {selectedDraftJob.errorCode}</small>}
          </div>
        )}
      </section>

      <section className="panel editPanel">
        <div className="panelHeader"><div><h2>현재 초안 편집</h2><p>저장할 때마다 기존 내용을 덮어쓰지 않고 새 버전을 생성합니다.</p></div></div>
        <form action={saveAction} className="draftForm">
          <input name="baseVersionId" type="hidden" value={post.currentVersionId} />
          <label><span>제목</span><input defaultValue={post.currentVersion.title} maxLength={200} name="title" required /></label>
          <label><span>사실 메모</span><textarea defaultValue={post.visitContext} maxLength={10000} name="visitContext" rows={4} /></label>
          <label><span>본문</span><textarea defaultValue={post.currentVersion.body} maxLength={100000} name="body" rows={14} /></label>
          <label><span>태그</span><input defaultValue={post.currentVersion.tags.join(", ")} name="tags" placeholder="쉼표로 구분" /></label>
          <div className="formActions"><button className="primaryButton" type="submit">새 버전 저장</button></div>
        </form>
      </section>

      <section className="panel assetPanel">
        <div className="panelHeader"><div><h2>이미지 자산</h2><p>원본 저장 순서와 검증된 파일 메타데이터입니다.</p></div><span className="countBadge">{post.assets.length} / 20</span></div>
        <form action={uploadAction} className="assetUploadForm" encType="multipart/form-data" method="post">
          <label>
            <span>이미지 선택</span>
            <input
              accept="image/jpeg,image/png,image/webp"
              disabled={post.assets.length >= 20}
              multiple
              name="files"
              required
              type="file"
            />
          </label>
          <button className="primaryButton" disabled={post.assets.length >= 20} type="submit">업로드</button>
        </form>
        <p className="assetHint">JPEG, PNG, WebP · 이미지당 최대 15 MiB · 남은 수량 {20 - post.assets.length}장</p>
        {post.assets.length === 0 ? (
          <div className="emptyState"><h3>등록된 이미지가 없습니다</h3><p>위에서 이미지를 선택해 원본 자산을 추가하세요.</p></div>
        ) : (
          <ol className="assetList">
            {post.assets.map((asset, index) => (
              <li key={asset.id}>
                <span className="assetOrder">{asset.displayOrder + 1}</span>
                <span className="assetMetadata">
                  <strong>{asset.originalFilename}</strong>
                  <small>{asset.contentType} · {formatBytes(asset.byteSize)} · {formatDimensions(asset.width, asset.height)}</small>
                  <small className={asset.derivativeStatus === "READY" ? "derivativeReady" : "derivativePending"}>
                    {asset.derivativeStatus === "READY"
                      ? `최적화 ${formatVariant(asset.optimizedImage)} · 분석 ${formatVariant(asset.analysisImage)}`
                      : "원본만 저장됨 · WebP 파생 처리는 디코더 도입 후 지원"}
                  </small>
                </span>
                <div className="assetActions">
                  <form action={reorderBlogAssetsAction.bind(null, post.id, returnTo, moveAsset(assetIds, index, -1))}>
                    <button aria-label={`${asset.originalFilename} 위로 이동`} className="orderButton" disabled={index === 0} type="submit">↑</button>
                  </form>
                  <form action={reorderBlogAssetsAction.bind(null, post.id, returnTo, moveAsset(assetIds, index, 1))}>
                    <button aria-label={`${asset.originalFilename} 아래로 이동`} className="orderButton" disabled={index === post.assets.length - 1} type="submit">↓</button>
                  </form>
                  <ConfirmActionForm
                    action={deleteBlogAssetAction.bind(null, post.id, returnTo, asset.id)}
                    label="삭제"
                    message={`${asset.originalFilename} 이미지를 삭제할까요? 저장된 원본도 함께 제거됩니다.`}
                    tone="danger"
                  />
                </div>
              </li>
            ))}
          </ol>
        )}
      </section>

      <section className="panel actionPanel">
        <div><h2>게시글 관리</h2><p>상태 변경, 복제, 보관은 본문 버전과 독립적으로 관리됩니다.</p></div>
        <div className="actionButtons">
          <form action={statusAction} className="statusForm">
            <label><span>상태</span><select defaultValue={post.status} name="status">{BLOG_POST_STATUSES.map((status) => <option key={status} value={status}>{statusLabel(status)}</option>)}</select></label>
            <button className="actionButton" type="submit">상태 저장</button>
          </form>
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

function analysisItemLabel(status: string, category: string | undefined) {
  if (status === "SUCCEEDED") return category ? `분석 완료 · ${category}` : "분석 완료";
  if (status === "FAILED") return "분석 실패";
  if (status === "CANCELLED") return "분석 취소됨";
  return "분석 대기 중";
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDimensions(width: number | null, height: number | null) {
  return width && height ? `${width}×${height}` : "해상도 미확인";
}

function formatVariant(variant: { width: number; height: number; byteSize: number } | null) {
  return variant ? `${variant.width}×${variant.height} · ${formatBytes(variant.byteSize)}` : "없음";
}

function moveAsset(assetIds: string[], index: number, offset: -1 | 1) {
  const target = index + offset;
  if (target < 0 || target >= assetIds.length) return assetIds;
  const reordered = [...assetIds];
  [reordered[index], reordered[target]] = [reordered[target], reordered[index]];
  return reordered;
}
