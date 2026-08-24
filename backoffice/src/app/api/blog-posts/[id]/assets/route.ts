import { NextResponse } from "next/server";
import { getMimirApiUrl } from "@/lib/mimir-api";

export const runtime = "nodejs";

type RouteContext = {
  params: Promise<{ id: string }>;
};

export async function POST(request: Request, { params }: RouteContext) {
  const { id } = await params;
  const contentType = request.headers.get("content-type");
  const returnTo = safeReturnTo(new URL(request.url).searchParams.get("returnTo"));
  if (!contentType?.startsWith("multipart/form-data") || request.body === null) {
    return redirectToDetail(request.url, id, returnTo, "error", "asset-upload");
  }

  try {
    const init: RequestInit & { duplex: "half" } = {
      method: "POST",
      headers: { Accept: "application/json", "Content-Type": contentType },
      body: request.body,
      duplex: "half",
    };
    const response = await fetch(
      getMimirApiUrl(`/blog-posts/${encodeURIComponent(id)}/assets`),
      init,
    );
    return response.ok
      ? redirectToDetail(request.url, id, returnTo, "notice", "asset-upload")
      : redirectToDetail(request.url, id, returnTo, "error", "asset-upload");
  } catch {
    return redirectToDetail(request.url, id, returnTo, "error", "asset-upload");
  }
}

function redirectToDetail(
  requestUrl: string,
  postId: string,
  returnTo: string,
  key: string,
  value: string,
) {
  const target = new URL(`/blogs/${encodeURIComponent(postId)}`, requestUrl);
  target.searchParams.set(key, value);
  target.searchParams.set("returnTo", returnTo);
  return NextResponse.redirect(target, 303);
}

function safeReturnTo(value: string | null) {
  return value === "/blogs" || value?.startsWith("/blogs?") ? value : "/blogs";
}
