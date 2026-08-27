import json
from dataclasses import dataclass
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from uuid import uuid4


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
class ImageVariant:
    content_type: str
    byte_size: int
    width: int
    height: int


@dataclass(frozen=True)
class ImageAnalysis:
    asset_id: str
    display_order: int
    category: str
    description: str
    objects: tuple[str, ...]
    visible_text: str | None
    analyzed_at: str


@dataclass(frozen=True)
class ImageAnalysisItem:
    asset_id: str
    display_order: int
    status: str
    error_code: str | None
    analysis: ImageAnalysis | None


@dataclass(frozen=True)
class AiJob:
    id: str
    blog_post_id: str
    parent_job_id: str | None
    job_type: str
    status: str
    stage: str
    total_items: int
    processed_items: int
    failed_items: int
    progress: int
    base_version_id: str | None
    result_version_id: str | None
    error_code: str | None
    items: tuple[ImageAnalysisItem, ...]


@dataclass(frozen=True)
class BlogAsset:
    id: str
    display_order: int
    original_filename: str
    content_type: str
    byte_size: int
    width: int | None
    height: int | None
    derivative_status: str
    optimized_image: ImageVariant | None
    analysis_image: ImageVariant | None
    created_at: str


@dataclass(frozen=True)
class ImageUpload:
    filename: str
    content_type: str
    content: bytes


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
    assets: tuple[BlogAsset, ...]


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
            },
        )
        return self._blog_post_detail(payload)

    def select_blog_version(self, post_id: str, version_id: str) -> BlogPostDetail:
        payload = self._request(
            "POST",
            f"/blog-posts/{post_id}/versions/{version_id}/select",
        )
        return self._blog_post_detail(payload)

    def upload_blog_assets(
        self,
        post_id: str,
        images: list[ImageUpload],
    ) -> tuple[BlogAsset, ...]:
        if not images:
            raise ValueError("At least one image is required.")
        boundary = f"mimir-{uuid4().hex}"
        body = bytearray()
        for image in images:
            filename = self._multipart_filename(image.filename)
            body.extend(f"--{boundary}\r\n".encode())
            body.extend(
                f'Content-Disposition: form-data; name="files"; filename="{filename}"\r\n'.encode("utf-8")
            )
            body.extend(f"Content-Type: {image.content_type}\r\n\r\n".encode())
            body.extend(image.content)
            body.extend(b"\r\n")
        body.extend(f"--{boundary}--\r\n".encode())
        payload = self._send_request(
            "POST",
            f"/blog-posts/{post_id}/assets",
            bytes(body),
            {
                "Accept": "application/json",
                "Content-Type": f"multipart/form-data; boundary={boundary}",
            },
            timeout_seconds=max(self._timeout_seconds, 60.0),
        )
        if not isinstance(payload, list):
            raise BackendUnavailableError("Mimir backend returned invalid image assets.")
        try:
            return tuple(self._blog_asset(item) for item in payload)
        except (KeyError, TypeError, ValueError) as error:
            raise BackendUnavailableError("Mimir backend returned invalid image assets.") from error

    def create_image_analysis_job(self, post_id: str) -> AiJob:
        return self._ai_job(self._request("POST", f"/blog-posts/{post_id}/generation-jobs"))

    def create_draft_generation_job(
        self,
        post_id: str,
        base_version_id: str,
        revision_instruction: str,
    ) -> AiJob:
        return self._ai_job(self._request(
            "POST",
            f"/blog-posts/{post_id}/draft-generation-jobs",
            {
                "baseVersionId": base_version_id,
                "revisionInstruction": revision_instruction,
            },
        ))

    def get_ai_job(self, job_id: str) -> AiJob:
        return self._ai_job(self._request("GET", f"/jobs/{job_id}"))

    def retry_failed_image_analysis(self, job_id: str) -> AiJob:
        return self._ai_job(self._request("POST", f"/jobs/{job_id}/retry-failed"))

    def cancel_ai_job(self, job_id: str) -> AiJob:
        return self._ai_job(self._request("POST", f"/jobs/{job_id}/cancel"))

    def cancel_image_analysis_job(self, job_id: str) -> AiJob:
        return self.cancel_ai_job(job_id)

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
        return self._send_request(method, path, data, headers)

    def _send_request(
        self,
        method: str,
        path: str,
        data: bytes | None,
        headers: dict[str, str],
        *,
        timeout_seconds: float | None = None,
    ) -> Any:
        request = Request(
            f"{self._base_url}{path}",
            headers=headers,
            data=data,
            method=method,
        )
        try:
            with urlopen(request, timeout=timeout_seconds or self._timeout_seconds) as response:
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

    @staticmethod
    def _multipart_filename(filename: str) -> str:
        return filename.replace("\\", "_").replace('"', "_").replace("\r", "").replace("\n", "") or "image"

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
                assets=tuple(cls._blog_asset(item) for item in payload["assets"]),
            )
        except (KeyError, TypeError, ValueError) as error:
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
    def _blog_asset(payload: Any) -> BlogAsset:
        if not isinstance(payload, dict):
            raise TypeError
        return BlogAsset(
            id=str(payload["id"]),
            display_order=int(payload["displayOrder"]),
            original_filename=str(payload["originalFilename"]),
            content_type=str(payload["contentType"]),
            byte_size=int(payload["byteSize"]),
            width=int(payload["width"]) if payload["width"] is not None else None,
            height=int(payload["height"]) if payload["height"] is not None else None,
            derivative_status=str(payload["derivativeStatus"]),
            optimized_image=MimirApiClient._image_variant(payload["optimizedImage"]),
            analysis_image=MimirApiClient._image_variant(payload["analysisImage"]),
            created_at=str(payload["createdAt"]),
        )

    @staticmethod
    def _image_variant(payload: Any) -> ImageVariant | None:
        if payload is None:
            return None
        if not isinstance(payload, dict):
            raise TypeError
        return ImageVariant(
            content_type=str(payload["contentType"]),
            byte_size=int(payload["byteSize"]),
            width=int(payload["width"]),
            height=int(payload["height"]),
        )

    @staticmethod
    def _ai_job(payload: Any) -> AiJob:
        if not isinstance(payload, dict) or not isinstance(payload.get("items"), list):
            raise BackendUnavailableError("Mimir backend returned an invalid AI job.")
        try:
            return AiJob(
                id=str(payload["id"]),
                blog_post_id=str(payload["blogPostId"]),
                parent_job_id=str(payload["parentJobId"]) if payload["parentJobId"] is not None else None,
                job_type=str(payload["jobType"]),
                status=str(payload["status"]),
                stage=str(payload["stage"]),
                total_items=int(payload["totalItems"]),
                processed_items=int(payload["processedItems"]),
                failed_items=int(payload["failedItems"]),
                progress=int(payload["progress"]),
                base_version_id=str(payload["baseVersionId"]) if payload.get("baseVersionId") is not None else None,
                result_version_id=str(payload["resultVersionId"]) if payload.get("resultVersionId") is not None else None,
                error_code=str(payload["errorCode"]) if payload.get("errorCode") is not None else None,
                items=tuple(MimirApiClient._analysis_item(item) for item in payload["items"]),
            )
        except (KeyError, TypeError, ValueError) as error:
            raise BackendUnavailableError("Mimir backend returned an invalid AI job.") from error

    @staticmethod
    def _analysis_item(payload: Any) -> ImageAnalysisItem:
        if not isinstance(payload, dict):
            raise TypeError
        analysis = payload["analysis"]
        parsed = None
        if analysis is not None:
            if not isinstance(analysis, dict) or not isinstance(analysis.get("objects"), list):
                raise TypeError
            parsed = ImageAnalysis(
                asset_id=str(analysis["assetId"]),
                display_order=int(analysis["displayOrder"]),
                category=str(analysis["category"]),
                description=str(analysis["description"]),
                objects=tuple(str(value) for value in analysis["objects"]),
                visible_text=str(analysis["visibleText"]) if analysis["visibleText"] is not None else None,
                analyzed_at=str(analysis["analyzedAt"]),
            )
        return ImageAnalysisItem(
            asset_id=str(payload["assetId"]),
            display_order=int(payload["displayOrder"]),
            status=str(payload["status"]),
            error_code=str(payload["errorCode"]) if payload["errorCode"] is not None else None,
            analysis=parsed,
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
