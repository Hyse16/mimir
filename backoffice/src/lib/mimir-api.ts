export type SystemStatus = {
  status: string;
  privacyMode: string;
  components: {
    database: string;
  };
};

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
  const baseUrl = process.env.MIMIR_API_BASE_URL ?? "http://127.0.0.1:8080/api/v1";

  try {
    const response = await fetch(`${baseUrl.replace(/\/$/, "")}/system/status`, {
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
