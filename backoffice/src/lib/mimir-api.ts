export type SystemStatus = {
  status: string;
  privacyMode: string;
  components: {
    database: string;
  };
};

export type BlogPostSummary = {
  id: string;
  title: string;
  status: string;
  currentVersionId: string;
  createdAt: string;
  updatedAt: string;
};

export type DraftVersion = {
  id: string;
  versionNumber: number;
  source: string;
  title: string;
  body: string;
  tags: string[];
  createdAt: string;
  selected: boolean;
};

export type BlogAsset = {
  id: string;
  displayOrder: number;
  originalFilename: string;
  contentType: string;
  byteSize: number;
  width: number | null;
  height: number | null;
  derivativeStatus: "READY" | "ORIGINAL_ONLY";
  optimizedImage: ImageVariant | null;
  analysisImage: ImageVariant | null;
  createdAt: string;
};

export type ImageVariant = {
  contentType: string;
  byteSize: number;
  width: number;
  height: number;
};

export type ImageAnalysis = {
  assetId: string;
  displayOrder: number;
  category: string;
  description: string;
  objects: string[];
  visibleText: string | null;
  analyzedAt: string;
};

export type ImageAnalysisItem = {
  assetId: string;
  displayOrder: number;
  status: "WAITING" | "SUCCEEDED" | "FAILED" | "CANCELLED";
  errorCode: string | null;
  analysis: ImageAnalysis | null;
};

export type AiJob = {
  id: string;
  blogPostId: string;
  parentJobId: string | null;
  jobType: "IMAGE_ANALYSIS" | "BLOG_DRAFT_GENERATION";
  status: "WAITING" | "RUNNING" | "CANCEL_REQUESTED" | "COMPLETED" | "PARTIAL_FAILED" | "FAILED" | "CANCELLED";
  stage: "QUEUED" | "IMAGE_ANALYSIS" | "CONTEXT_ASSEMBLY" | "DRAFT_GENERATION" | "COMPLETE";
  totalItems: number;
  processedItems: number;
  failedItems: number;
  progress: number;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
  cancelRequestedAt: string | null;
  baseVersionId: string | null;
  resultVersionId: string | null;
  errorCode: string | null;
  items: ImageAnalysisItem[];
};

export type DraftRevisionTurn = {
  id: string;
  previousTurnId?: string | null;
  status: AiJob["status"];
  stage: AiJob["stage"];
  baseVersionId: string;
  resultVersionId: string | null;
  revisionInstruction: string;
  target: DraftGenerationTarget;
  errorCode: string | null;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
};

export const DRAFT_GENERATION_TARGETS = ["FULL", "TITLE", "BODY", "TAGS"] as const;
export type DraftGenerationTarget = (typeof DRAFT_GENERATION_TARGETS)[number];

export type DraftRevisionTurnPage = {
  items: DraftRevisionTurn[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

export type BlogPostDetail = BlogPostSummary & {
  visitContext: string;
  currentVersion: DraftVersion;
  versions: DraftVersion[];
  assets: BlogAsset[];
};

export type BlogPostPage = {
  items: BlogPostSummary[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

export type BlogPostQuery = {
  query?: string;
  status?: string;
  page?: number;
  size?: number;
  sort?: string;
  direction?: string;
};

export const BLOG_POST_STATUSES = [
  "DRAFT",
  "GENERATING",
  "REVIEW_REQUIRED",
  "READY",
  "PUBLISHED",
  "ARCHIVED",
] as const;

const baseUrl = () =>
  (process.env.MIMIR_API_BASE_URL ?? "http://127.0.0.1:8080/api/v1").replace(/\/$/, "");

export function getMimirApiUrl(path: string) {
  return `${baseUrl()}${path}`;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isSystemStatus(value: unknown): value is SystemStatus {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const candidate = value as Record<string, unknown>;
  const components = candidate.components;
  return (
    typeof candidate.status === "string" &&
    typeof candidate.privacyMode === "string" &&
    typeof components === "object" &&
    components !== null &&
    typeof (components as Record<string, unknown>).database === "string"
  );
}

export async function getSystemStatus(): Promise<SystemStatus | null> {
  try {
    const response = await fetch(`${baseUrl()}/system/status`, {
      cache: "no-store",
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(3000),
    });
    if (!response.ok) {
      return null;
    }

    const payload: unknown = await response.json();
    return isSystemStatus(payload) ? payload : null;
  } catch {
    return null;
  }
}

function isBlogPostSummary(value: unknown): value is BlogPostSummary {
  if (!isRecord(value)) return false;
  return (
    typeof value.id === "string" &&
    typeof value.title === "string" &&
    typeof value.status === "string" &&
    typeof value.currentVersionId === "string" &&
    typeof value.createdAt === "string" &&
    typeof value.updatedAt === "string"
  );
}

function isDraftVersion(value: unknown): value is DraftVersion {
  if (!isRecord(value)) return false;
  return (
    typeof value.id === "string" &&
    typeof value.versionNumber === "number" &&
    typeof value.source === "string" &&
    typeof value.title === "string" &&
    typeof value.body === "string" &&
    Array.isArray(value.tags) &&
    value.tags.every((tag) => typeof tag === "string") &&
    typeof value.createdAt === "string" &&
    typeof value.selected === "boolean"
  );
}

function isBlogAsset(value: unknown): value is BlogAsset {
  if (!isRecord(value)) return false;
  return (
    typeof value.id === "string" &&
    typeof value.displayOrder === "number" &&
    typeof value.originalFilename === "string" &&
    typeof value.contentType === "string" &&
    typeof value.byteSize === "number" &&
    (typeof value.width === "number" || value.width === null) &&
    (typeof value.height === "number" || value.height === null) &&
    (value.derivativeStatus === "READY" || value.derivativeStatus === "ORIGINAL_ONLY") &&
    (value.optimizedImage === null || isImageVariant(value.optimizedImage)) &&
    (value.analysisImage === null || isImageVariant(value.analysisImage)) &&
    typeof value.createdAt === "string"
  );
}

function isImageVariant(value: unknown): value is ImageVariant {
  return isRecord(value) &&
    typeof value.contentType === "string" &&
    typeof value.byteSize === "number" &&
    typeof value.width === "number" &&
    typeof value.height === "number";
}

function isImageAnalysis(value: unknown): value is ImageAnalysis {
  return isRecord(value) &&
    typeof value.assetId === "string" &&
    typeof value.displayOrder === "number" &&
    typeof value.category === "string" &&
    typeof value.description === "string" &&
    Array.isArray(value.objects) && value.objects.every((item) => typeof item === "string") &&
    (typeof value.visibleText === "string" || value.visibleText === null) &&
    typeof value.analyzedAt === "string";
}

function isAiJob(value: unknown): value is AiJob {
  if (!isRecord(value)) return false;
  return typeof value.id === "string" &&
    typeof value.blogPostId === "string" &&
    (typeof value.parentJobId === "string" || value.parentJobId === null) &&
    ["IMAGE_ANALYSIS", "BLOG_DRAFT_GENERATION"].includes(String(value.jobType)) &&
    ["WAITING", "RUNNING", "CANCEL_REQUESTED", "COMPLETED", "PARTIAL_FAILED", "FAILED", "CANCELLED"].includes(String(value.status)) &&
    ["QUEUED", "IMAGE_ANALYSIS", "CONTEXT_ASSEMBLY", "DRAFT_GENERATION", "COMPLETE"].includes(String(value.stage)) &&
    typeof value.totalItems === "number" &&
    typeof value.processedItems === "number" &&
    typeof value.failedItems === "number" &&
    typeof value.progress === "number" &&
    typeof value.createdAt === "string" &&
    (typeof value.startedAt === "string" || value.startedAt === null) &&
    (typeof value.completedAt === "string" || value.completedAt === null) &&
    (typeof value.cancelRequestedAt === "string" || value.cancelRequestedAt === null) &&
    (typeof value.baseVersionId === "string" || value.baseVersionId === null) &&
    (typeof value.resultVersionId === "string" || value.resultVersionId === null) &&
    (typeof value.errorCode === "string" || value.errorCode === null) &&
    Array.isArray(value.items) && value.items.every((item) => isRecord(item) &&
      typeof item.assetId === "string" &&
      typeof item.displayOrder === "number" &&
      ["WAITING", "SUCCEEDED", "FAILED", "CANCELLED"].includes(String(item.status)) &&
      (typeof item.errorCode === "string" || item.errorCode === null) &&
      (item.analysis === null || isImageAnalysis(item.analysis)));
}

function isDraftRevisionTurn(value: unknown): value is DraftRevisionTurn {
  return isRecord(value) &&
    typeof value.id === "string" &&
    (value.previousTurnId === undefined || typeof value.previousTurnId === "string" || value.previousTurnId === null) &&
    ["WAITING", "RUNNING", "CANCEL_REQUESTED", "COMPLETED", "PARTIAL_FAILED", "FAILED", "CANCELLED"].includes(String(value.status)) &&
    ["QUEUED", "IMAGE_ANALYSIS", "CONTEXT_ASSEMBLY", "DRAFT_GENERATION", "COMPLETE"].includes(String(value.stage)) &&
    typeof value.baseVersionId === "string" &&
    (typeof value.resultVersionId === "string" || value.resultVersionId === null) &&
    typeof value.revisionInstruction === "string" &&
    DRAFT_GENERATION_TARGETS.includes(value.target as DraftGenerationTarget) &&
    (typeof value.errorCode === "string" || value.errorCode === null) &&
    typeof value.createdAt === "string" &&
    (typeof value.startedAt === "string" || value.startedAt === null) &&
    (typeof value.completedAt === "string" || value.completedAt === null);
}

function isDraftRevisionTurnPage(value: unknown): value is DraftRevisionTurnPage {
  return isRecord(value) &&
    Array.isArray(value.items) &&
    value.items.every(isDraftRevisionTurn) &&
    typeof value.page === "number" &&
    typeof value.size === "number" &&
    typeof value.totalItems === "number" &&
    typeof value.totalPages === "number";
}

function isBlogPostPage(value: unknown): value is BlogPostPage {
  if (!isRecord(value)) return false;
  return (
    Array.isArray(value.items) &&
    value.items.every(isBlogPostSummary) &&
    typeof value.page === "number" &&
    typeof value.size === "number" &&
    typeof value.totalItems === "number" &&
    typeof value.totalPages === "number"
  );
}

function isBlogPostDetail(value: unknown): value is BlogPostDetail {
  if (!isRecord(value) || !isBlogPostSummary(value)) return false;
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.visitContext === "string" &&
    isDraftVersion(candidate.currentVersion) &&
    Array.isArray(candidate.versions) &&
    candidate.versions.every(isDraftVersion) &&
    Array.isArray(candidate.assets) &&
    candidate.assets.every(isBlogAsset)
  );
}

async function getJson(path: string): Promise<unknown | null> {
  try {
    const response = await fetch(`${baseUrl()}${path}`, {
      cache: "no-store",
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(3000),
    });
    return response.ok ? await response.json() : null;
  } catch {
    return null;
  }
}

async function sendJson(
  path: string,
  method: "POST" | "PATCH" | "PUT" | "DELETE",
  body?: Record<string, unknown>,
): Promise<unknown | null> {
  try {
    const response = await fetch(`${baseUrl()}${path}`, {
      method,
      cache: "no-store",
      headers: {
        Accept: "application/json",
        ...(body ? { "Content-Type": "application/json" } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(5000),
    });
    return response.ok ? await response.json() : null;
  } catch {
    return null;
  }
}

export async function getBlogPosts(query: BlogPostQuery = {}): Promise<BlogPostPage | null> {
  const params = new URLSearchParams({
    page: String(query.page ?? 0),
    size: String(query.size ?? 20),
    sort: query.sort ?? "updatedAt",
    direction: query.direction ?? "desc",
  });
  if (query.query) params.set("query", query.query);
  if (query.status) params.set("status", query.status);

  const payload = await getJson(`/blog-posts?${params.toString()}`);
  return isBlogPostPage(payload) ? payload : null;
}

export async function getBlogPost(id: string): Promise<BlogPostDetail | null> {
  const payload = await getJson(`/blog-posts/${encodeURIComponent(id)}`);
  return isBlogPostDetail(payload) ? payload : null;
}

export async function getDraftRevisionTurns(
  postId: string,
  page = 0,
  size = 20,
): Promise<DraftRevisionTurnPage | null> {
  const payload = await getJson(
    `/blog-posts/${encodeURIComponent(postId)}/draft-generation-jobs?page=${page}&size=${size}`,
  );
  return isDraftRevisionTurnPage(payload) ? payload : null;
}

export async function archiveBlogPost(id: string): Promise<BlogPostDetail | null> {
  const payload = await sendJson(`/blog-posts/${encodeURIComponent(id)}/archive`, "POST");
  return isBlogPostDetail(payload) ? payload : null;
}

export async function duplicateBlogPost(id: string): Promise<BlogPostDetail | null> {
  const payload = await sendJson(`/blog-posts/${encodeURIComponent(id)}/duplicate`, "POST");
  return isBlogPostDetail(payload) ? payload : null;
}

export async function saveBlogVersion(
  id: string,
  input: {
    baseVersionId: string;
    title: string;
    body: string;
    tags: string[];
    visitContext: string;
  },
): Promise<BlogPostDetail | null> {
  const payload = await sendJson(`/blog-posts/${encodeURIComponent(id)}/versions`, "POST", {
    ...input,
  });
  return isBlogPostDetail(payload) ? payload : null;
}

export async function selectBlogVersion(id: string, versionId: string): Promise<BlogPostDetail | null> {
  const payload = await sendJson(
    `/blog-posts/${encodeURIComponent(id)}/versions/${encodeURIComponent(versionId)}/select`,
    "POST",
  );
  return isBlogPostDetail(payload) ? payload : null;
}

export async function updateBlogPostStatus(id: string, status: string): Promise<BlogPostDetail | null> {
  const payload = await sendJson(`/blog-posts/${encodeURIComponent(id)}`, "PATCH", { status });
  return isBlogPostDetail(payload) ? payload : null;
}

export async function reorderBlogAssets(id: string, assetIds: string[]): Promise<BlogAsset[] | null> {
  const payload = await sendJson(`/blog-posts/${encodeURIComponent(id)}/assets/order`, "PUT", { assetIds });
  return Array.isArray(payload) && payload.every(isBlogAsset) ? payload : null;
}

export async function deleteBlogAsset(id: string, assetId: string): Promise<BlogAsset[] | null> {
  const payload = await sendJson(
    `/blog-posts/${encodeURIComponent(id)}/assets/${encodeURIComponent(assetId)}`,
    "DELETE",
  );
  return Array.isArray(payload) && payload.every(isBlogAsset) ? payload : null;
}

export async function createImageAnalysisJob(postId: string): Promise<AiJob | null> {
  const payload = await sendJson(`/blog-posts/${encodeURIComponent(postId)}/generation-jobs`, "POST");
  return isAiJob(payload) ? payload : null;
}

export async function createDraftGenerationJob(
  postId: string,
  baseVersionId: string,
  revisionInstruction: string,
  target: DraftGenerationTarget,
  previousTurnId?: string,
): Promise<AiJob | null> {
  const payload = await sendJson(`/blog-posts/${encodeURIComponent(postId)}/draft-generation-jobs`, "POST", {
    baseVersionId,
    revisionInstruction,
    target,
    ...(previousTurnId ? { previousTurnId } : {}),
  });
  return isAiJob(payload) ? payload : null;
}

export function previousRevisionTurnId(
  turns: DraftRevisionTurn[],
  baseVersionId: string,
): string | undefined {
  return turns.find(
    (turn) => turn.status === "COMPLETED" && turn.resultVersionId === baseVersionId,
  )?.id;
}

export async function getAiJob(jobId: string): Promise<AiJob | null> {
  const payload = await getJson(`/jobs/${encodeURIComponent(jobId)}`);
  return isAiJob(payload) ? payload : null;
}

export async function retryFailedImageAnalysis(jobId: string): Promise<AiJob | null> {
  const payload = await sendJson(`/jobs/${encodeURIComponent(jobId)}/retry-failed`, "POST");
  return isAiJob(payload) ? payload : null;
}

export async function cancelImageAnalysisJob(jobId: string): Promise<AiJob | null> {
  const payload = await sendJson(`/jobs/${encodeURIComponent(jobId)}/cancel`, "POST");
  return isAiJob(payload) ? payload : null;
}
