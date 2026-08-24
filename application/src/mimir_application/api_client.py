import json
from dataclasses import dataclass
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class SystemStatus:
    status: str
    privacy_mode: str
    database: str


@dataclass(frozen=True)
class DraftVersion:
    id: str
    version_number: int
    source: str
    title: str
    body: str
    tags: tuple[str, ...]
    created_at: str
    selected: bool


@dataclass(frozen=True)
class BlogPostSummary:
    id: str
    title: str
    status: str
    current_version_id: str
    created_at: str
    updated_at: str


@dataclass(frozen=True)
class BlogPostDetail:
    id: str
    title: str
    status: str
    visit_context: str
    current_version_id: str
    created_at: str
    updated_at: str
    current_version: DraftVersion
    versions: tuple[DraftVersion, ...]


class BackendUnavailableError(RuntimeError):
    """Raised when the business backend cannot provide a valid status."""


class ApiRequestError(RuntimeError):
    """Raised when the backend rejects a validly transported request."""

    def __init__(self, message: str, status_code: int) -> None:
        super().__init__(message)
        self.status_code = status_code


class MimirApiClient:
    def __init__(self, base_url: str, timeout_seconds: float = 3.0) -> None:
        self._base_url = base_url.rstrip("/")
        self._timeout_seconds = timeout_seconds

    def get_system_status(self) -> SystemStatus:
        try:
            payload = self._request("GET", "/system/status")
        except (ApiRequestError, BackendUnavailableError) as error:
            raise BackendUnavailableError("Mimir backend is unavailable.") from error

        try:
            return SystemStatus(
                status=str(payload["status"]),
                privacy_mode=str(payload["privacyMode"]),
                database=str(payload["components"]["database"]),
            )
        except (KeyError, TypeError) as error:
            raise BackendUnavailableError("Mimir backend returned an invalid status.") from error

    def create_blog_post(
        self,
        *,
        title: str,
        visit_context: str,
        body: str,
        tags: list[str],
    ) -> BlogPostDetail:
        payload = self._request(
            "POST",
            "/blog-posts",
            {
                "title": title,
                "visitContext": visit_context,
                "body": body,
                "tags": tags,
            },
        )
        return self._blog_post_detail(payload)

    def get_blog_posts(self, *, size: int = 20) -> tuple[BlogPostSummary, ...]:
        payload = self._request("GET", f"/blog-posts?size={size}&sort=updatedAt&direction=desc")
        try:
            if not isinstance(payload, dict) or not isinstance(payload["items"], list):
                raise TypeError
            return tuple(self._blog_post_summary(item) for item in payload["items"])
        except (KeyError, TypeError) as error:
            raise BackendUnavailableError("Mimir backend returned an invalid blog list.") from error

    def get_blog_post(self, post_id: str) -> BlogPostDetail:
        payload = self._request("GET", f"/blog-posts/{post_id}")
        return self._blog_post_detail(payload)

    def add_blog_version(
        self,
        post_id: str,
        *,
        base_version_id: str,
        title: str,
        body: str,
        tags: list[str],
        visit_context: str | None = None,
    ) -> BlogPostDetail:
        payload = self._request(
            "POST",
            f"/blog-posts/{post_id}/versions",
            {
                "baseVersionId": base_version_id,
                "title": title,
                "body": body,
                "tags": tags,
                "visitContext": visit_context,
                "source": "USER_EDIT",
            },
        )
        return self._blog_post_detail(payload)

    def _request(
        self,
        method: str,
        path: str,
        body: dict[str, Any] | None = None,
    ) -> Any:
        data = json.dumps(body).encode() if body is not None else None
        headers = {"Accept": "application/json"}
        if data is not None:
            headers["Content-Type"] = "application/json"
        request = Request(
            f"{self._base_url}{path}",
            headers=headers,
            data=data,
            method=method,
        )
        try:
            with urlopen(request, timeout=self._timeout_seconds) as response:
                return json.load(response)
        except HTTPError as error:
            message = "요청을 처리할 수 없습니다."
            try:
                payload = json.load(error)
                if isinstance(payload, dict) and isinstance(payload.get("message"), str):
                    message = payload["message"]
            except (json.JSONDecodeError, OSError):
                pass
            raise ApiRequestError(message, error.code) from error
        except (URLError, TimeoutError, json.JSONDecodeError) as error:
            raise BackendUnavailableError("Mimir backend is unavailable.") from error

    @classmethod
    def _blog_post_detail(cls, payload: Any) -> BlogPostDetail:
        try:
            if not isinstance(payload, dict):
                raise TypeError
            versions = tuple(cls._draft_version(item) for item in payload["versions"])
            return BlogPostDetail(
                id=str(payload["id"]),
                title=str(payload["title"]),
                status=str(payload["status"]),
                visit_context=str(payload["visitContext"]),
                current_version_id=str(payload["currentVersionId"]),
                created_at=str(payload["createdAt"]),
                updated_at=str(payload["updatedAt"]),
                current_version=cls._draft_version(payload["currentVersion"]),
                versions=versions,
            )
        except (KeyError, TypeError) as error:
            raise BackendUnavailableError("Mimir backend returned an invalid blog post.") from error

    @staticmethod
    def _blog_post_summary(payload: Any) -> BlogPostSummary:
        if not isinstance(payload, dict):
            raise TypeError
        return BlogPostSummary(
            id=str(payload["id"]),
            title=str(payload["title"]),
            status=str(payload["status"]),
            current_version_id=str(payload["currentVersionId"]),
            created_at=str(payload["createdAt"]),
            updated_at=str(payload["updatedAt"]),
        )

    @staticmethod
    def _draft_version(payload: Any) -> DraftVersion:
        if not isinstance(payload, dict) or not isinstance(payload.get("tags"), list):
            raise TypeError
        return DraftVersion(
            id=str(payload["id"]),
            version_number=int(payload["versionNumber"]),
            source=str(payload["source"]),
            title=str(payload["title"]),
            body=str(payload["body"]),
            tags=tuple(str(tag) for tag in payload["tags"]),
            created_at=str(payload["createdAt"]),
            selected=bool(payload["selected"]),
        )
