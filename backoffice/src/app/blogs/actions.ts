"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { archiveBlogPost, duplicateBlogPost } from "@/lib/mimir-api";

export async function archiveBlogPostAction(postId: string, returnTo: string) {
  const post = await archiveBlogPost(postId);
  if (!post) redirect(detailHref(postId, returnTo, "error=archive"));

  revalidatePath("/");
  revalidatePath("/blogs");
  revalidatePath(`/blogs/${postId}`);
  redirect(listHref(returnTo, "notice", "archived"));
}

export async function duplicateBlogPostAction(postId: string, returnTo: string) {
  const duplicate = await duplicateBlogPost(postId);
  if (!duplicate) redirect(detailHref(postId, returnTo, "error=duplicate"));

  revalidatePath("/");
  revalidatePath("/blogs");
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
