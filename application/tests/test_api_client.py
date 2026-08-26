import io
import json
from unittest.mock import patch
from urllib.error import HTTPError

import pytest

from mimir_application.api_client import ApiRequestError, BackendUnavailableError, ImageUpload, MimirApiClient


class Response(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()


def test_reads_privacy_safe_system_status() -> None:
    response = Response(
        json.dumps(
            {
                "status": "UP",
                "privacyMode": "LOCAL_ONLY",
                "components": {"database": "UP"},
            }
        ).encode()
    )

    with patch("mimir_application.api_client.urlopen", return_value=response):
        status = MimirApiClient("http://localhost:8080/api/v1/").get_system_status()

    assert status.status == "UP"
    assert status.privacy_mode == "LOCAL_ONLY"
    assert status.database == "UP"


def test_rejects_invalid_status_payload() -> None:
    response = Response(b'{"status":"UP"}')

    with patch("mimir_application.api_client.urlopen", return_value=response):
        with pytest.raises(BackendUnavailableError):
            MimirApiClient("http://localhost:8080/api/v1").get_system_status()


def blog_response(version_number: int = 1) -> Response:
    version = {
        "id": f"version-{version_number}",
        "versionNumber": version_number,
        "source": "USER_EDIT",
        "title": "서울 산책",
        "body": "사실에 근거한 본문",
        "tags": ["서울", "산책"],
        "createdAt": "2026-08-20T10:00:00Z",
        "selected": True,
    }
    return Response(
        json.dumps(
            {
                "id": "post-1",
                "title": "서울 산책",
                "status": "DRAFT",
                "visitContext": "오후에 한강을 걸었다.",
                "currentVersionId": version["id"],
                "createdAt": "2026-08-20T10:00:00Z",
                "updatedAt": "2026-08-20T10:00:00Z",
                "currentVersion": version,
                "versions": [version],
                "assets": [],
            }
        ).encode()
    )


def test_creates_blog_post_with_structured_context() -> None:
    with patch("mimir_application.api_client.urlopen", return_value=blog_response()) as request:
        post = MimirApiClient("http://localhost:8080/api/v1/").create_blog_post(
            title="서울 산책",
            visit_context="오후에 한강을 걸었다.",
            body="사실에 근거한 본문",
            tags=["서울", "산책"],
        )

    sent = request.call_args.args[0]
    assert sent.full_url == "http://localhost:8080/api/v1/blog-posts"
    assert sent.method == "POST"
    assert json.loads(sent.data)["visitContext"] == "오후에 한강을 걸었다."
    assert post.current_version.version_number == 1
    assert post.current_version.tags == ("서울", "산책")


def test_adds_version_against_current_base_version() -> None:
    with patch("mimir_application.api_client.urlopen", return_value=blog_response(2)) as request:
        post = MimirApiClient("http://localhost:8080/api/v1").add_blog_version(
            "post-1",
            base_version_id="version-1",
            title="서울 산책",
            body="수정된 본문",
            tags=["서울"],
            visit_context="저녁에 한강을 걸었다.",
        )

    sent = request.call_args.args[0]
    assert sent.full_url.endswith("/blog-posts/post-1/versions")
    assert json.loads(sent.data)["baseVersionId"] == "version-1"
    assert json.loads(sent.data)["visitContext"] == "저녁에 한강을 걸었다."
    assert post.current_version.version_number == 2


def test_lists_recent_blog_posts() -> None:
    detail = json.load(blog_response())
    summary = {key: detail[key] for key in ("id", "title", "status", "currentVersionId", "createdAt", "updatedAt")}
    response = Response(json.dumps({"items": [summary], "page": 0, "size": 10, "totalItems": 1, "totalPages": 1}).encode())

    with patch("mimir_application.api_client.urlopen", return_value=response) as request:
        posts = MimirApiClient("http://localhost:8080/api/v1").get_blog_posts(size=10)

    assert request.call_args.args[0].full_url.endswith("/blog-posts?size=10&sort=updatedAt&direction=desc")
    assert posts[0].id == "post-1"
    assert posts[0].title == "서울 산책"


def test_reads_existing_blog_post() -> None:
    with patch("mimir_application.api_client.urlopen", return_value=blog_response()) as request:
        post = MimirApiClient("http://localhost:8080/api/v1").get_blog_post("post-1")

    assert request.call_args.args[0].full_url.endswith("/blog-posts/post-1")
    assert post.visit_context == "오후에 한강을 걸었다."


def test_uploads_images_as_multipart_assets() -> None:
    response = Response(json.dumps([{
        "id": "asset-1",
        "displayOrder": 0,
        "originalFilename": "cafe.png",
        "contentType": "image/png",
        "byteSize": 9,
        "width": 1200,
        "height": 800,
        "derivativeStatus": "READY",
        "optimizedImage": {"contentType": "image/jpeg", "byteSize": 7, "width": 1200, "height": 800},
        "analysisImage": {"contentType": "image/jpeg", "byteSize": 5, "width": 960, "height": 640},
        "createdAt": "2026-08-24T10:00:00Z",
    }]).encode())
    image = ImageUpload("cafe.png", "image/png", b"\x89PNG\r\n\x1a\n\x01")

    with patch("mimir_application.api_client.urlopen", return_value=response) as request:
        assets = MimirApiClient("http://localhost:8080/api/v1").upload_blog_assets("post-1", [image])

    sent = request.call_args.args[0]
    assert sent.full_url.endswith("/blog-posts/post-1/assets")
    assert sent.get_header("Content-type").startswith("multipart/form-data; boundary=")
    assert b'name="files"; filename="cafe.png"' in sent.data
    assert image.content in sent.data
    assert assets[0].original_filename == "cafe.png"
    assert assets[0].analysis_image is not None
    assert assets[0].analysis_image.width == 960


def test_starts_and_reads_structured_image_analysis_job() -> None:
    payload = {
        "id": "job-1",
        "blogPostId": "post-1",
        "parentJobId": None,
        "jobType": "IMAGE_ANALYSIS",
        "status": "PARTIAL_FAILED",
        "stage": "COMPLETE",
        "totalItems": 2,
        "processedItems": 1,
        "failedItems": 1,
        "progress": 100,
        "createdAt": "2026-08-26T10:00:00Z",
        "startedAt": "2026-08-26T10:00:01Z",
        "completedAt": "2026-08-26T10:00:02Z",
        "items": [{
            "assetId": "asset-1",
            "displayOrder": 0,
            "status": "SUCCEEDED",
            "errorCode": None,
            "analysis": {
                "assetId": "asset-1",
                "displayOrder": 0,
                "category": "food",
                "description": "접시 위 케이크",
                "objects": ["cake", "plate"],
                "visibleText": None,
                "analyzedAt": "2026-08-26T10:00:02Z",
            },
        }, {
            "assetId": "asset-2",
            "displayOrder": 1,
            "status": "FAILED",
            "errorCode": "VISION_PROVIDER_FAILED",
            "analysis": None,
        }],
    }
    with patch("mimir_application.api_client.urlopen", return_value=Response(json.dumps(payload).encode())) as request:
        job = MimirApiClient("http://localhost:8080/api/v1").create_image_analysis_job("post-1")

    assert request.call_args.args[0].full_url.endswith("/blog-posts/post-1/generation-jobs")
    assert job.status == "PARTIAL_FAILED"
    assert job.items[0].analysis is not None
    assert job.items[0].analysis.objects == ("cake", "plate")


def test_surfaces_backend_validation_message() -> None:
    error = HTTPError(
        "http://localhost:8080/api/v1/blog-posts",
        400,
        "Bad Request",
        {},
        Response(b'{"message":"Title is required."}'),
    )

    with patch("mimir_application.api_client.urlopen", side_effect=error):
        with pytest.raises(ApiRequestError, match="Title is required") as raised:
            MimirApiClient("http://localhost:8080/api/v1").create_blog_post(
                title="",
                visit_context="",
                body="",
                tags=[],
            )

    assert raised.value.status_code == 400
