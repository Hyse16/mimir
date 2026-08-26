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
        <small>{snapshot.processedItems} 성공 · {snapshot.failedItems} 실패 · 총 {snapshot.totalItems}장</small>
      </div>
      <progress aria-label="이미지 분석 진행률" max={100} value={snapshot.progress}>{snapshot.progress}%</progress>
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
    WAITING: "분석 대기",
    RUNNING: "이미지 분석 중",
    CANCEL_REQUESTED: "취소 처리 중",
    COMPLETED: "분석 완료",
    PARTIAL_FAILED: "일부 이미지 분석 실패",
    FAILED: "이미지 분석 실패",
    CANCELLED: "분석 취소됨",
  }[status];
}
