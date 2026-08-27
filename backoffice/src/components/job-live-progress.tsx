"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import type { AiJob } from "@/lib/mimir-api";

type ProgressSnapshot = Pick<AiJob, "status" | "totalItems" | "processedItems" | "failedItems" | "progress">;

const terminalStatuses = new Set<AiJob["status"]>([
  "COMPLETED",
  "PARTIAL_FAILED",
  "FAILED",
  "CANCELLED",
]);

export function JobLiveProgress({ initialJob, eventUrl }: { initialJob: AiJob; eventUrl: string }) {
  const router = useRouter();
  const [snapshot, setSnapshot] = useState<ProgressSnapshot>(initialJob);

  useEffect(() => {
    if (terminalStatuses.has(initialJob.status)) return;
    const source = new EventSource(eventUrl);
    source.addEventListener("job-progress", (message) => {
      const event = parseSnapshot((message as MessageEvent<string>).data);
      if (!event) return;
      setSnapshot(event);
      if (terminalStatuses.has(event.status)) {
        source.close();
        router.refresh();
      }
    });
    return () => source.close();
  }, [eventUrl, initialJob.status, router]);

  return (
    <>
      <div>
        <strong>{statusLabel(snapshot.status)}</strong>
        <small>{initialJob.jobType === "IMAGE_ANALYSIS"
          ? `${snapshot.processedItems} 성공 · ${snapshot.failedItems} 실패 · 총 ${snapshot.totalItems}장`
          : draftStageLabel(snapshot.status)}</small>
      </div>
      <progress aria-label="AI 작업 진행률" max={100} value={snapshot.progress}>{snapshot.progress}%</progress>
    </>
  );
}

function parseSnapshot(data: string): ProgressSnapshot | null {
  try {
    const value: unknown = JSON.parse(data);
    if (typeof value !== "object" || value === null) return null;
    const event = value as Record<string, unknown>;
    const statuses: AiJob["status"][] = [
      "WAITING", "RUNNING", "CANCEL_REQUESTED", "COMPLETED", "PARTIAL_FAILED", "FAILED", "CANCELLED",
    ];
    if (!statuses.includes(event.status as AiJob["status"]) ||
        typeof event.totalItems !== "number" ||
        typeof event.processedItems !== "number" ||
        typeof event.failedItems !== "number" ||
        typeof event.progress !== "number") return null;
    return event as ProgressSnapshot;
  } catch {
    return null;
  }
}

function statusLabel(status: AiJob["status"]) {
  return {
    WAITING: "작업 대기",
    RUNNING: "작업 진행 중",
    CANCEL_REQUESTED: "취소 처리 중",
    COMPLETED: "작업 완료",
    PARTIAL_FAILED: "일부 작업 실패",
    FAILED: "작업 실패",
    CANCELLED: "작업 취소됨",
  }[status];
}

function draftStageLabel(status: AiJob["status"]) {
  if (status === "COMPLETED") return "새 AI 초안 버전이 저장되었습니다.";
  if (status === "FAILED") return "초안 생성 결과를 저장하지 않았습니다.";
  if (status === "CANCELLED") return "취소되어 새 버전을 만들지 않았습니다.";
  return "Context와 이미지 분석을 바탕으로 초안을 생성합니다.";
}
