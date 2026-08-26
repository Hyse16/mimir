import { getMimirApiUrl } from "@/lib/mimir-api";

type RouteContext = { params: Promise<{ id: string }> };

export async function GET(request: Request, { params }: RouteContext) {
  const { id } = await params;
  const lastEventId = request.headers.get("Last-Event-ID");
  try {
    const response = await fetch(getMimirApiUrl(`/jobs/${encodeURIComponent(id)}/events`), {
      cache: "no-store",
      headers: {
        Accept: "text/event-stream",
        ...(lastEventId ? { "Last-Event-ID": lastEventId } : {}),
      },
      signal: request.signal,
    });
    if (!response.ok || !response.body) {
      return new Response(null, { status: response.status });
    }
    return new Response(response.body, {
      headers: {
        "Cache-Control": "no-cache, no-transform",
        "Content-Type": "text/event-stream",
        Connection: "keep-alive",
      },
    });
  } catch {
    return new Response(null, { status: 502 });
  }
}
