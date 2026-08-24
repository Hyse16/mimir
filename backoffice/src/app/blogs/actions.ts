"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import {
  archiveBlogPost,
  BLOG_POST_STATUSES,
  duplicateBlogPost,
  saveBlogVersion,
  updateBlogPostStatus,
} from "@/lib/mimir-api";

export async function saveBlogDraftAction(postId: string, returnTo: string, formData: FormData) {
  const title = text(formData, "title").trim();
  const baseVersionId = text(formData, "baseVersionId");
  if (!title || !baseVersionId) redirect(detailHref(postId, returnTo, "error=validation"));

  const post = await saveBlogVersion(postId, {
    baseVersionId,
    title,
    body: text(formData, "body"),
    visitContext: text(formData, "visitContext"),
    tags: text(formData, "tags").split(",").map((tag) => tag.trim()).filter(Boolean),
  });
  if (!post) redirect(detailHref(postId, returnTo, "error=save"));

  revalidateBlog(postId);
  redirect(detailHref(postId, returnTo, "notice=saved"));
}

export async function updateBlogStatusAction(postId: string, returnTo: string, formData: FormData) {
  const status = text(formData, "status");
  if (!BLOG_POST_STATUSES.includes(status as (typeof BLOG_POST_STATUSES)[number])) {
    redirect(detailHref(postId, returnTo, "error=validation"));
  }
  const post = await updateBlogPostStatus(postId, status);
  if (!post) redirect(detailHref(postId, returnTo, "error=status"));

  revalidateBlog(postId);
  redirect(detailHref(postId, returnTo, "notice=status"));
}

export async function archiveBlogPostAction(postId: string, returnTo: string) {
  const post = await archiveBlogPost(postId);
  if (!post) redirect(detailHref(postId, returnTo, "error=archive"));

  revalidateBlog(postId);
  redirect(listHref(returnTo, "notice", "archived"));
}

export async function duplicateBlogPostAction(postId: string, returnTo: string) {
  const duplicate = await duplicateBlogPost(postId);
  if (!duplicate) redirect(detailHref(postId, returnTo, "error=duplicate"));

  revalidateBlog(duplicate.id);
  redirect(detailHref(duplicate.id, returnTo, "notice=duplicated"));
}

function listHref(returnTo: string, key: string, value: string) {
  const url = new URL(returnTo, "http://mimir.local");
  url.searchParams.set(key, value);
  return `${url.pathname}${url.search}`;
}

function detailHref(postId: string, returnTo: string, state: string) {
  const params = new URLSearchParams(state);
  params.set("returnTo", returnTo);
  return `/blogs/${encodeURIComponent(postId)}?${params.toString()}`;
}

function revalidateBlog(postId: string) {
  revalidatePath("/");
  revalidatePath("/blogs");
  revalidatePath(`/blogs/${postId}`);
}

function text(formData: FormData, key: string) {
  const value = formData.get(key);
  return typeof value === "string" ? value : "";
}
