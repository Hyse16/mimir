from typing import Any

from mimir_application.api_client import (
    AiJob,
    BlogPostDetail,
    DraftRevisionTurn,
    DraftRevisionTurnPage,
    DraftVersion,
)
from mimir_application.config import AppConfig
from mimir_application.main import build_app


class FakePage:
    def __init__(self) -> None:
        self.controls: list[Any] = []

    def add(self, *controls: Any) -> None:
        self.controls.extend(controls)

    def update(self) -> None:
        pass


def test_builds_blog_editor_without_contacting_backend() -> None:
    page = FakePage()

    build_app(page, AppConfig(api_base_url="http://localhost:8080/api/v1"))  # type: ignore[arg-type]

    assert page.title == "Mimir"
    assert len(page.controls) == 1


def find_control(control: Any, *, key: str) -> Any:
    if getattr(control, "key", None) == key:
        return control
    children = list(getattr(control, "controls", None) or [])
    content = getattr(control, "content", None)
    if content is not None:
        children.append(content)
    for child in children:
        found = find_control(child, key=key)
        if found is not None:
            return found
    return None


def version(number: int, *, selected: bool = True) -> DraftVersion:
    return DraftVersion(
        id=f"version-{number}",
        version_number=number,
        source="AI_GENERATED" if number > 1 else "USER_EDIT",
        title="서울 산책",
        body=f"본문 {number}",
        tags=("서울",),
        created_at="2026-08-27T10:00:00Z",
        selected=selected,
    )


def post(number: int, *, total_versions: int | None = None) -> BlogPostDetail:
    total = total_versions or number
    current = version(number)
    versions = tuple(
        version(candidate, selected=candidate == number)
        for candidate in range(total, 0, -1)
    )
    return BlogPostDetail(
        id="post-1",
        title=current.title,
        status="REVIEW_REQUIRED" if number > 1 else "DRAFT",
        visit_context="한강을 걸었다.",
        current_version_id=current.id,
        created_at="2026-08-27T10:00:00Z",
        updated_at="2026-08-27T10:00:00Z",
        current_version=current,
        versions=versions,
        assets=(),
    )


def job(status: str, stage: str, *, result_version_id: str | None = None) -> AiJob:
    return AiJob(
        id="job-1",
        blog_post_id="post-1",
        parent_job_id=None,
        job_type="BLOG_DRAFT_GENERATION",
        status=status,
        stage=stage,
        total_items=1,
        processed_items=1 if status == "COMPLETED" else 0,
        failed_items=0,
        progress=100 if status == "COMPLETED" else 0,
        base_version_id="version-1",
        result_version_id=result_version_id,
        error_code=None,
        items=(),
    )


class FakeApiClient:
    def __init__(self) -> None:
        self.created_with: tuple[str, str, str] | None = None
        self.selected_version_id: str | None = None
        self.detail = post(1)
        self.polled_job = job("COMPLETED", "COMPLETE", result_version_id="version-2")

    def get_blog_post(self, _: str) -> BlogPostDetail:
        return self.detail

    def create_blog_post(self, **_: Any) -> BlogPostDetail:
        return self.detail

    def create_draft_generation_job(
        self, post_id: str, base_version_id: str, instruction: str
    ) -> AiJob:
        self.created_with = (post_id, base_version_id, instruction)
        return job("WAITING", "QUEUED")

    def get_ai_job(self, _: str) -> AiJob:
        self.detail = post(2)
        return self.polled_job

    def cancel_ai_job(self, _: str) -> AiJob:
        return job("CANCEL_REQUESTED", "QUEUED")

    def select_blog_version(self, _: str, version_id: str) -> BlogPostDetail:
        self.selected_version_id = version_id
        self.detail = post(1, total_versions=2)
        return self.detail

    def get_draft_revision_turns(self, _: str) -> DraftRevisionTurnPage:
        return DraftRevisionTurnPage(
            items=(DraftRevisionTurn(
                id="job-1",
                status="COMPLETED",
                stage="COMPLETE",
                base_version_id="version-1",
                result_version_id="version-2",
                revision_instruction="더 간결하게",
                error_code=None,
                created_at="2026-08-27T10:00:00Z",
                started_at="2026-08-27T10:00:01Z",
                completed_at="2026-08-27T10:00:02Z",
            ),),
            page=0,
            size=20,
            total_items=1,
            total_pages=1,
        )


def load_post(page: FakePage, client: FakeApiClient) -> Any:
    build_app(
        page,
        AppConfig(api_base_url="http://localhost:8080/api/v1"),
        api_client=client,  # type: ignore[arg-type]
    )  # type: ignore[arg-type]
    root = page.controls[0]
    selector = find_control(root, key="blog-selector")
    selector.value = "post-1"
    find_control(root, key="load-blog-post").on_click(None)
    return root


def test_starts_revision_and_loads_completed_version_on_refresh() -> None:
    page = FakePage()
    client = FakeApiClient()
    root = load_post(page, client)
    instruction = find_control(root, key="revision-instruction")
    instruction.value = "  더 간결하게 다듬어줘  "
    find_control(root, key="start-revision").on_click(None)

    assert client.created_with == ("post-1", "version-1", "더 간결하게 다듬어줘")
    assert find_control(root, key="refresh-revision").disabled is False

    find_control(root, key="refresh-revision").on_click(None)

    assert find_control(root, key="blog-body").value == "본문 2"
    assert find_control(root, key="revision-status").value == "수정 완료 · 버전 2를 불러왔습니다."


def test_blocks_revision_when_editor_has_unsaved_changes() -> None:
    page = FakePage()
    client = FakeApiClient()
    root = load_post(page, client)
    find_control(root, key="blog-body").value = "저장하지 않은 본문"
    find_control(root, key="revision-instruction").value = "더 짧게"

    find_control(root, key="start-revision").on_click(None)

    assert client.created_with is None
    assert "저장하지 않은 편집" in find_control(root, key="revision-status").value


def test_requests_revision_cancellation() -> None:
    page = FakePage()
    client = FakeApiClient()
    root = load_post(page, client)
    find_control(root, key="revision-instruction").value = "더 짧게"
    find_control(root, key="start-revision").on_click(None)

    find_control(root, key="cancel-revision").on_click(None)

    assert find_control(root, key="revision-status").value.startswith("CANCEL_REQUESTED")
    assert find_control(root, key="cancel-revision").disabled is True


def test_enables_revision_after_first_draft_is_saved() -> None:
    page = FakePage()
    client = FakeApiClient()
    build_app(
        page,
        AppConfig(api_base_url="http://localhost:8080/api/v1"),
        api_client=client,  # type: ignore[arg-type]
    )  # type: ignore[arg-type]
    root = page.controls[0]
    find_control(root, key="blog-title").value = "서울 산책"
    find_control(root, key="blog-context").value = "한강을 걸었다."
    find_control(root, key="blog-body").value = "본문 1"
    find_control(root, key="blog-tags").value = "서울"

    find_control(root, key="save-draft").on_click(None)

    assert find_control(root, key="revision-instruction").disabled is False
    assert find_control(root, key="start-revision").disabled is False


def test_compares_versions_without_changing_the_selected_version() -> None:
    page = FakePage()
    client = FakeApiClient()
    client.detail = post(2)
    root = load_post(page, client)

    find_control(root, key="compare-versions").on_click(None)

    comparison = find_control(root, key="version-comparison").value
    assert "--- v1" in comparison
    assert "+++ v2" in comparison
    assert client.selected_version_id is None


def test_restores_version_only_after_explicit_confirmation() -> None:
    page = FakePage()
    client = FakeApiClient()
    client.detail = post(2)
    root = load_post(page, client)
    restore_button = find_control(root, key="restore-version")

    restore_button.on_click(None)
    assert client.selected_version_id is None

    confirmation = find_control(root, key="confirm-version-restore")
    confirmation.value = True
    confirmation.on_change(None)
    restore_button.on_click(None)

    assert client.selected_version_id == "version-1"
    assert find_control(root, key="blog-body").value == "본문 1"
    assert "현재 초안으로 복원" in find_control(root, key="version-status").value


def test_shows_persisted_revision_turn_without_exposing_job_id() -> None:
    page = FakePage()
    client = FakeApiClient()
    client.detail = post(2)

    root = load_post(page, client)

    history = find_control(root, key="revision-history").value
    assert "COMPLETED · v1 → v2" in history
    assert "더 간결하게" in history
    assert "job-1" not in history
