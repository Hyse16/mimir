import mimetypes
from difflib import unified_diff

import flet as ft

from mimir_application.api_client import (
    AiJob,
    ApiRequestError,
    BackendUnavailableError,
    BlogPostDetail,
    DraftVersion,
    DraftRevisionTurn,
    ImageUpload,
    MimirApiClient,
)
from mimir_application.config import AppConfig


def build_app(
    page: ft.Page,
    config: AppConfig | None = None,
    api_client: MimirApiClient | None = None,
) -> None:
    settings = config or AppConfig.from_environment()
    client = api_client or MimirApiClient(settings.api_base_url)
    current_post: BlogPostDetail | None = None
    current_analysis_job: AiJob | None = None
    current_draft_job: AiJob | None = None
    selected_images: list[ImageUpload] = []

    page.title = "Mimir"
    page.theme_mode = ft.ThemeMode.LIGHT
    page.padding = 0
    page.bgcolor = "#F4F6F8"
    file_picker = ft.FilePicker()

    status_text = ft.Text("연결 확인 전", color="#5B6470", key="backend-status")
    status_icon = ft.Icon(ft.Icons.CIRCLE_OUTLINED, color="#6B7280", size=14)
    title_field = ft.TextField(label="제목", max_length=200, key="blog-title")
    context_field = ft.TextField(
        label="사실 메모",
        hint_text="방문 날짜, 장소, 주문한 메뉴처럼 글에 반드시 반영할 사실만 적어주세요.",
        multiline=True,
        min_lines=2,
        max_lines=4,
        key="blog-context",
    )
    body_field = ft.TextField(
        label="초안 본문",
        hint_text="직접 작성하거나 다음 STEP의 로컬 AI가 만든 초안을 검토합니다.",
        multiline=True,
        min_lines=8,
        max_lines=14,
        key="blog-body",
    )
    tags_field = ft.TextField(
        label="태그",
        hint_text="쉼표로 구분 (예: 서울, 카페, 산책)",
        key="blog-tags",
    )
    post_selector = ft.Dropdown(
        label="기존 게시글",
        hint_text="목록을 새로고침한 뒤 수정할 게시글을 선택하세요.",
        expand=True,
        key="blog-selector",
    )
    selector_text = ft.Text("새 초안을 작성하거나 기존 게시글을 불러올 수 있습니다.", color="#5B6470")
    result_text = ft.Text("아직 저장된 초안이 없습니다.", color="#5B6470")
    asset_text = ft.Text("게시글을 저장한 뒤 이미지를 최대 20장까지 추가할 수 있습니다.", color="#5B6470")
    analysis_text = ft.Text("이미지 업로드 후 구조화 분석을 시작할 수 있습니다.", color="#5B6470")
    save_button = ft.FilledButton(
        "첫 초안 저장", icon=ft.Icons.SAVE_OUTLINED, key="save-draft"
    )
    upload_button = ft.FilledButton("선택 이미지 업로드", icon=ft.Icons.UPLOAD, disabled=True)
    analysis_button = ft.FilledButton("이미지 분석 시작", icon=ft.Icons.AUTO_AWESOME, disabled=True)
    analysis_refresh_button = ft.OutlinedButton("분석 상태 새로고침", icon=ft.Icons.REFRESH, disabled=True)
    analysis_cancel_button = ft.OutlinedButton("분석 취소", icon=ft.Icons.CANCEL_OUTLINED, disabled=True)
    revision_field = ft.TextField(
        label="AI 수정 요청",
        hint_text="예: 문장을 더 간결하게 다듬되 사진 순서와 사실은 유지해줘.",
        multiline=True,
        min_lines=2,
        max_lines=4,
        max_length=10_000,
        disabled=True,
        key="revision-instruction",
    )
    revision_target = ft.Dropdown(
        label="재생성 대상",
        value="FULL",
        options=[
            ft.DropdownOption(key="FULL", text="전체 초안"),
            ft.DropdownOption(key="TITLE", text="제목만"),
            ft.DropdownOption(key="BODY", text="본문만"),
            ft.DropdownOption(key="TAGS", text="태그만"),
        ],
        disabled=True,
        key="revision-target",
    )
    revision_text = ft.Text(
        "게시글과 이미지 분석을 준비한 뒤 로컬 AI에 수정을 요청할 수 있습니다.",
        color="#5B6470",
        key="revision-status",
    )
    revision_button = ft.FilledButton(
        "AI 수정 시작", icon=ft.Icons.AUTO_FIX_HIGH, disabled=True, key="start-revision"
    )
    revision_refresh_button = ft.OutlinedButton(
        "진행 상태 새로고침", icon=ft.Icons.REFRESH, disabled=True, key="refresh-revision"
    )
    revision_cancel_button = ft.OutlinedButton(
        "수정 취소", icon=ft.Icons.CANCEL_OUTLINED, disabled=True, key="cancel-revision"
    )
    revision_history_text = ft.TextField(
        label="최근 수정 turn",
        value="저장된 AI 수정 이력이 없습니다.",
        multiline=True,
        min_lines=4,
        max_lines=10,
        read_only=True,
        key="revision-history",
    )
    revision_history_refresh_button = ft.OutlinedButton(
        "수정 이력 새로고침",
        icon=ft.Icons.HISTORY,
        disabled=True,
        key="refresh-revision-history",
    )
    version_before_selector = ft.Dropdown(
        label="복원 후보 / 이전 버전",
        disabled=True,
        expand=True,
        key="compare-version-before",
    )
    version_after_selector = ft.Dropdown(
        label="이후 버전",
        disabled=True,
        expand=True,
        key="compare-version-after",
    )
    compare_button = ft.OutlinedButton(
        "두 버전 비교", icon=ft.Icons.COMPARE_ARROWS, disabled=True, key="compare-versions"
    )
    comparison_text = ft.TextField(
        label="버전 차이",
        value="비교할 버전이 아직 없습니다.",
        multiline=True,
        min_lines=5,
        max_lines=14,
        read_only=True,
        key="version-comparison",
    )
    restore_confirmation = ft.Checkbox(
        label="선택 상태가 변경되는 것을 확인했습니다.",
        disabled=True,
        key="confirm-version-restore",
    )
    restore_button = ft.FilledButton(
        "선택 버전을 현재로 복원",
        icon=ft.Icons.RESTORE,
        disabled=True,
        key="restore-version",
    )
    version_text = ft.Text(
        "비교는 조회만 수행하며 복원 버튼만 현재 버전을 변경합니다.",
        color="#5B6470",
        key="version-status",
    )

    def refresh_status(_: ft.ControlEvent | None = None) -> None:
        try:
            status = client.get_system_status()
            status_icon.name = ft.Icons.CHECK_CIRCLE
            status_icon.color = "#16803C"
            status_text.value = f"서버 {status.status} · DB {status.database} · {status.privacy_mode}"
            status_text.color = "#16803C"
        except BackendUnavailableError:
            status_icon.name = ft.Icons.ERROR_OUTLINE
            status_icon.color = "#B42318"
            status_text.value = "업무 서버에 연결할 수 없습니다"
            status_text.color = "#B42318"
        page.update()

    def parse_tags() -> list[str]:
        return [tag.strip() for tag in (tags_field.value or "").split(",") if tag.strip()]

    def show_post(post: BlogPostDetail) -> None:
        nonlocal current_post, current_analysis_job, current_draft_job
        current_post = post
        post_selector.value = post.id
        title_field.value = post.current_version.title
        context_field.value = post.visit_context
        body_field.value = post.current_version.body
        tags_field.value = ", ".join(post.current_version.tags)
        save_button.text = "새 버전 저장"
        save_button.disabled = False
        selector_text.value = f"{post.title} · 현재 버전 {post.current_version.version_number} · {post.status}"
        selector_text.color = "#16803C"
        result_text.value = "기존 초안을 불러왔습니다. 저장하면 새 버전으로 보관됩니다."
        result_text.color = "#5B6470"
        selected_images.clear()
        upload_button.disabled = True
        asset_text.value = f"저장된 이미지 {len(post.assets)} / 20장"
        asset_text.color = "#5B6470"
        current_analysis_job = None
        analysis_button.text = "이미지 분석 시작"
        analysis_button.disabled = len(post.assets) == 0
        analysis_refresh_button.disabled = True
        analysis_cancel_button.disabled = True
        analysis_text.value = "이미지 업로드 후 구조화 분석을 시작할 수 있습니다."
        analysis_text.color = "#5B6470"
        current_draft_job = None
        revision_field.disabled = False
        revision_target.disabled = False
        revision_button.disabled = False
        revision_refresh_button.disabled = True
        revision_cancel_button.disabled = True
        revision_text.value = "현재 선택된 버전을 기준으로 새 AI 버전을 생성합니다. 저장하지 않은 편집은 먼저 저장해주세요."
        revision_text.color = "#5B6470"
        def version_options():
            return [
                ft.DropdownOption(
                    key=version.id,
                    text=f"v{version.version_number} · {version.title} · {version.source}",
                )
                for version in post.versions
            ]

        version_before_selector.options = version_options()
        version_after_selector.options = version_options()
        comparison_version = next(
            (version for version in post.versions if not version.selected),
            post.current_version,
        )
        version_before_selector.value = comparison_version.id
        version_after_selector.value = post.current_version_id
        has_history = len(post.versions) > 1
        version_before_selector.disabled = not has_history
        version_after_selector.disabled = not has_history
        compare_button.disabled = not has_history
        comparison_text.value = (
            "두 버전을 선택하고 비교해주세요."
            if has_history
            else "비교할 이전 버전이 없습니다."
        )
        restore_confirmation.value = False
        restore_confirmation.disabled = not has_history
        restore_button.disabled = True
        version_text.value = "비교는 조회만 수행하며 복원 버튼만 현재 버전을 변경합니다."
        version_text.color = "#5B6470"
        load_revision_history(post)

    def show_analysis_job(job: AiJob) -> None:
        nonlocal current_analysis_job
        current_analysis_job = job
        active = job.status in {"WAITING", "RUNNING", "CANCEL_REQUESTED"}
        analysis_refresh_button.disabled = not active
        analysis_cancel_button.disabled = job.status not in {"WAITING", "RUNNING"}
        analysis_button.disabled = active
        analysis_button.text = "분석 진행 중" if active else (
            "실패 이미지 재분석" if job.status in {"PARTIAL_FAILED", "FAILED"} else "전체 이미지 다시 분석"
        )
        analysis_text.value = (
            f"{job.status} · 성공 {job.processed_items} · 실패 {job.failed_items} · "
            f"전체 {job.total_items} · {job.progress}%"
        )
        analysis_text.color = "#B42318" if job.failed_items else "#16803C"

    def show_draft_job(job: AiJob) -> None:
        nonlocal current_draft_job
        current_draft_job = job
        active = job.status in {"WAITING", "RUNNING", "CANCEL_REQUESTED"}
        revision_field.disabled = active
        revision_target.disabled = active
        revision_button.disabled = active
        revision_refresh_button.disabled = not active
        revision_cancel_button.disabled = job.status not in {"WAITING", "RUNNING"}
        details = f"{job.status} · {job.stage} · {job.progress}%"
        if job.error_code:
            details += f" · 오류 {job.error_code}"
        revision_text.value = details
        revision_text.color = "#B42318" if job.status == "FAILED" else "#16803C"

    def refresh_posts(_: ft.ControlEvent | None = None) -> None:
        try:
            posts = client.get_blog_posts()
            post_selector.options = [
                ft.DropdownOption(key=post.id, text=f"{post.title} · {post.status}")
                for post in posts
            ]
            selector_text.value = f"최근 게시글 {len(posts)}개를 불러왔습니다."
            selector_text.color = "#16803C"
        except (ApiRequestError, BackendUnavailableError):
            selector_text.value = "게시글 목록을 불러오지 못했습니다."
            selector_text.color = "#B42318"
        page.update()

    def load_selected(_: ft.ControlEvent) -> None:
        if not post_selector.value:
            post_selector.error_text = "불러올 게시글을 선택해주세요."
            page.update()
            return
        post_selector.error_text = None
        try:
            show_post(client.get_blog_post(post_selector.value))
        except ApiRequestError as error:
            selector_text.value = f"게시글을 불러오지 못했습니다: {error}"
            selector_text.color = "#B42318"
        except BackendUnavailableError:
            selector_text.value = "게시글을 불러오지 못했습니다: 업무 서버에 연결할 수 없습니다."
            selector_text.color = "#B42318"
        page.update()

    def start_new(_: ft.ControlEvent) -> None:
        nonlocal current_post, current_analysis_job, current_draft_job
        current_post = None
        post_selector.value = None
        post_selector.error_text = None
        title_field.value = ""
        context_field.value = ""
        body_field.value = ""
        tags_field.value = ""
        save_button.text = "첫 초안 저장"
        selector_text.value = "새 게시글 작성 모드입니다."
        selector_text.color = "#5B6470"
        result_text.value = "아직 저장된 초안이 없습니다."
        result_text.color = "#5B6470"
        selected_images.clear()
        upload_button.disabled = True
        asset_text.value = "게시글을 저장한 뒤 이미지를 최대 20장까지 추가할 수 있습니다."
        asset_text.color = "#5B6470"
        current_analysis_job = None
        analysis_button.text = "이미지 분석 시작"
        analysis_button.disabled = True
        analysis_refresh_button.disabled = True
        analysis_cancel_button.disabled = True
        analysis_text.value = "이미지 업로드 후 구조화 분석을 시작할 수 있습니다."
        analysis_text.color = "#5B6470"
        current_draft_job = None
        revision_field.value = ""
        revision_field.disabled = True
        revision_target.value = "FULL"
        revision_target.disabled = True
        revision_field.error_text = None
        revision_button.disabled = True
        revision_refresh_button.disabled = True
        revision_cancel_button.disabled = True
        revision_text.value = "게시글과 이미지 분석을 준비한 뒤 로컬 AI에 수정을 요청할 수 있습니다."
        revision_text.color = "#5B6470"
        version_before_selector.options = []
        version_after_selector.options = []
        version_before_selector.value = None
        version_after_selector.value = None
        version_before_selector.disabled = True
        version_after_selector.disabled = True
        compare_button.disabled = True
        comparison_text.value = "비교할 버전이 아직 없습니다."
        restore_confirmation.value = False
        restore_confirmation.disabled = True
        restore_button.disabled = True
        version_text.value = "비교는 조회만 수행하며 복원 버튼만 현재 버전을 변경합니다."
        version_text.color = "#5B6470"
        revision_history_text.value = "저장된 AI 수정 이력이 없습니다."
        revision_history_refresh_button.disabled = True
        page.update()

    async def pick_images(_: ft.ControlEvent) -> None:
        if current_post is None:
            asset_text.value = "이미지를 추가하기 전에 게시글을 먼저 저장해주세요."
            asset_text.color = "#B42318"
            page.update()
            return
        remaining = 20 - len(current_post.assets)
        if remaining <= 0:
            asset_text.value = "이미지는 게시글당 최대 20장까지 저장할 수 있습니다."
            asset_text.color = "#B42318"
            page.update()
            return
        files = await file_picker.pick_files(
            dialog_title="블로그 이미지 선택",
            file_type=ft.FilePickerFileType.IMAGE,
            allow_multiple=True,
            with_data=True,
        )
        if not files:
            return
        if len(files) > remaining:
            asset_text.value = f"현재 {remaining}장만 더 추가할 수 있습니다."
            asset_text.color = "#B42318"
            page.update()
            return

        uploads: list[ImageUpload] = []
        for file in files:
            content_type = mimetypes.guess_type(file.name)[0]
            if file.bytes is None or content_type not in {"image/jpeg", "image/png", "image/webp"}:
                asset_text.value = "JPEG, PNG, WebP 파일만 선택할 수 있습니다."
                asset_text.color = "#B42318"
                page.update()
                return
            uploads.append(ImageUpload(file.name, content_type, file.bytes))
        selected_images[:] = uploads
        upload_button.disabled = False
        asset_text.value = f"{len(uploads)}장 선택됨 · 업로드 후 총 {len(current_post.assets) + len(uploads)}장"
        asset_text.color = "#16803C"
        page.update()

    def upload_images(_: ft.ControlEvent) -> None:
        nonlocal current_post
        if current_post is None or not selected_images:
            return
        upload_button.disabled = True
        asset_text.value = "원본 이미지 업로드 중…"
        asset_text.color = "#5B6470"
        page.update()
        try:
            client.upload_blog_assets(current_post.id, selected_images)
            show_post(client.get_blog_post(current_post.id))
            asset_text.value = f"업로드 완료 · 저장된 이미지 {len(current_post.assets)} / 20장"
            asset_text.color = "#16803C"
        except ApiRequestError as error:
            asset_text.value = f"이미지 업로드 실패: {error}"
            asset_text.color = "#B42318"
        except BackendUnavailableError:
            asset_text.value = "이미지 업로드 실패: 업무 서버에 연결할 수 없습니다."
            asset_text.color = "#B42318"
        finally:
            upload_button.disabled = not selected_images
            page.update()

    upload_button.on_click = upload_images

    def start_analysis(_: ft.ControlEvent) -> None:
        if current_post is None or not current_post.assets:
            return
        analysis_button.disabled = True
        analysis_text.value = "이미지 분석 작업 요청 중…"
        analysis_text.color = "#5B6470"
        page.update()
        try:
            job = (
                client.retry_failed_image_analysis(current_analysis_job.id)
                if current_analysis_job is not None and current_analysis_job.status in {"PARTIAL_FAILED", "FAILED"}
                else client.create_image_analysis_job(current_post.id)
            )
            show_analysis_job(job)
        except ApiRequestError as error:
            analysis_text.value = f"이미지 분석 요청 실패: {error}"
            analysis_text.color = "#B42318"
            analysis_button.disabled = False
        except BackendUnavailableError:
            analysis_text.value = "이미지 분석 요청 실패: 업무 서버에 연결할 수 없습니다."
            analysis_text.color = "#B42318"
            analysis_button.disabled = False
        page.update()

    def refresh_analysis(_: ft.ControlEvent) -> None:
        if current_analysis_job is None:
            return
        try:
            show_analysis_job(client.get_ai_job(current_analysis_job.id))
        except (ApiRequestError, BackendUnavailableError):
            analysis_text.value = "이미지 분석 상태를 불러오지 못했습니다."
            analysis_text.color = "#B42318"
        page.update()

    def cancel_analysis(_: ft.ControlEvent) -> None:
        if current_analysis_job is None:
            return
        analysis_cancel_button.disabled = True
        analysis_text.value = "이미지 분석 취소 요청 중…"
        analysis_text.color = "#5B6470"
        page.update()
        try:
            show_analysis_job(client.cancel_ai_job(current_analysis_job.id))
        except (ApiRequestError, BackendUnavailableError):
            analysis_text.value = "이미지 분석을 취소하지 못했습니다."
            analysis_text.color = "#B42318"
            analysis_cancel_button.disabled = False
        page.update()

    analysis_button.on_click = start_analysis
    analysis_refresh_button.on_click = refresh_analysis
    analysis_cancel_button.on_click = cancel_analysis

    def start_revision(_: ft.ControlEvent) -> None:
        if current_post is None:
            return
        instruction = (revision_field.value or "").strip()
        if not instruction:
            revision_field.error_text = "수정 요청을 입력해주세요."
            page.update()
            return
        if (
            (title_field.value or "") != current_post.current_version.title
            or (body_field.value or "") != current_post.current_version.body
            or tuple(parse_tags()) != current_post.current_version.tags
            or (context_field.value or "") != current_post.visit_context
        ):
            revision_text.value = "저장하지 않은 편집이 있습니다. 새 버전으로 저장한 뒤 AI 수정을 시작해주세요."
            revision_text.color = "#B42318"
            page.update()
            return

        revision_field.error_text = None
        revision_field.disabled = True
        revision_target.disabled = True
        revision_button.disabled = True
        revision_text.value = "로컬 AI 수정 작업 요청 중…"
        revision_text.color = "#5B6470"
        page.update()
        try:
            show_draft_job(
                client.create_draft_generation_job(
                    current_post.id,
                    current_post.current_version_id,
                    instruction,
                    revision_target.value or "FULL",
                )
            )
            load_revision_history(current_post)
        except ApiRequestError as error:
            revision_text.value = f"AI 수정 요청 실패: {error}"
            revision_text.color = "#B42318"
            revision_field.disabled = False
            revision_target.disabled = False
            revision_button.disabled = False
        except BackendUnavailableError:
            revision_text.value = "AI 수정 요청 실패: 업무 서버에 연결할 수 없습니다."
            revision_text.color = "#B42318"
            revision_field.disabled = False
            revision_target.disabled = False
            revision_button.disabled = False
        page.update()

    def refresh_revision(_: ft.ControlEvent) -> None:
        nonlocal current_post
        if current_draft_job is None:
            return
        try:
            job = client.get_ai_job(current_draft_job.id)
            if job.status == "COMPLETED" and current_post is not None:
                current_post = client.get_blog_post(current_post.id)
                show_post(current_post)
                revision_text.value = (
                    f"수정 완료 · 버전 {current_post.current_version.version_number}를 불러왔습니다."
                )
                revision_text.color = "#16803C"
            else:
                show_draft_job(job)
        except (ApiRequestError, BackendUnavailableError):
            revision_text.value = "AI 수정 상태를 불러오지 못했습니다."
            revision_text.color = "#B42318"
        page.update()

    def cancel_revision(_: ft.ControlEvent) -> None:
        if current_draft_job is None:
            return
        revision_cancel_button.disabled = True
        revision_text.value = "AI 수정 취소 요청 중…"
        revision_text.color = "#5B6470"
        page.update()
        try:
            show_draft_job(client.cancel_ai_job(current_draft_job.id))
            if current_post is not None:
                load_revision_history(current_post)
        except (ApiRequestError, BackendUnavailableError):
            revision_text.value = "AI 수정을 취소하지 못했습니다."
            revision_text.color = "#B42318"
            revision_cancel_button.disabled = False
        page.update()

    revision_button.on_click = start_revision
    revision_refresh_button.on_click = refresh_revision
    revision_cancel_button.on_click = cancel_revision

    def load_revision_history(post: BlogPostDetail) -> None:
        try:
            history = client.get_draft_revision_turns(post.id)
            revision_history_text.value = revision_history_document(post, history.items)
            revision_history_refresh_button.disabled = False
        except (ApiRequestError, BackendUnavailableError):
            revision_history_text.value = "AI 수정 이력을 불러오지 못했습니다."
            revision_history_refresh_button.disabled = False

    def refresh_revision_history(_: ft.ControlEvent) -> None:
        if current_post is None:
            return
        load_revision_history(current_post)
        page.update()

    revision_history_refresh_button.on_click = refresh_revision_history

    def selected_version(version_id: str | None) -> DraftVersion | None:
        if current_post is None or version_id is None:
            return None
        return next((version for version in current_post.versions if version.id == version_id), None)

    def compare_versions(_: ft.ControlEvent) -> None:
        before = selected_version(version_before_selector.value)
        after = selected_version(version_after_selector.value)
        if before is None or after is None:
            version_text.value = "비교할 두 버전을 선택해주세요."
            version_text.color = "#B42318"
            page.update()
            return
        before_lines = version_document(before)
        after_lines = version_document(after)
        differences = list(
            unified_diff(
                before_lines,
                after_lines,
                fromfile=f"v{before.version_number}",
                tofile=f"v{after.version_number}",
                lineterm="",
            )
        )
        comparison_text.value = "\n".join(differences) if differences else "선택한 두 버전의 내용이 같습니다."
        version_text.value = f"v{before.version_number}과 v{after.version_number} 비교 결과입니다. 현재 선택 버전은 변경되지 않았습니다."
        version_text.color = "#16803C"
        restore_confirmation.value = False
        restore_button.disabled = True
        page.update()

    def confirm_restore(_: ft.ControlEvent) -> None:
        candidate = selected_version(version_before_selector.value)
        restore_button.disabled = not (
            restore_confirmation.value
            and candidate is not None
            and current_post is not None
            and candidate.id != current_post.current_version_id
        )
        page.update()

    def restore_version(_: ft.ControlEvent) -> None:
        nonlocal current_post
        candidate = selected_version(version_before_selector.value)
        if (
            current_post is None
            or candidate is None
            or candidate.id == current_post.current_version_id
            or not restore_confirmation.value
        ):
            version_text.value = "이전 버전을 선택하고 복원 확인을 체크해주세요."
            version_text.color = "#B42318"
            page.update()
            return
        restore_button.disabled = True
        version_text.value = f"v{candidate.version_number} 복원 중…"
        version_text.color = "#5B6470"
        page.update()
        try:
            current_post = client.select_blog_version(current_post.id, candidate.id)
            show_post(current_post)
            version_text.value = f"v{current_post.current_version.version_number}을 현재 초안으로 복원했습니다."
            version_text.color = "#16803C"
        except ApiRequestError as error:
            version_text.value = f"버전 복원 실패: {error}"
            version_text.color = "#B42318"
        except BackendUnavailableError:
            version_text.value = "버전 복원 실패: 업무 서버에 연결할 수 없습니다."
            version_text.color = "#B42318"
        page.update()

    compare_button.on_click = compare_versions
    restore_confirmation.on_change = confirm_restore
    restore_button.on_click = restore_version

    def save_draft(_: ft.ControlEvent) -> None:
        nonlocal current_post
        title = (title_field.value or "").strip()
        if not title:
            title_field.error_text = "제목을 입력해주세요."
            page.update()
            return

        title_field.error_text = None
        save_button.disabled = True
        result_text.value = "저장 중…"
        result_text.color = "#5B6470"
        page.update()
        try:
            was_new = current_post is None
            if current_post is None:
                current_post = client.create_blog_post(
                    title=title,
                    visit_context=context_field.value or "",
                    body=body_field.value or "",
                    tags=parse_tags(),
                )
            else:
                current_post = client.add_blog_version(
                    current_post.id,
                    base_version_id=current_post.current_version_id,
                    title=title,
                    body=body_field.value or "",
                    tags=parse_tags(),
                    visit_context=context_field.value or "",
                )
            show_post(current_post)
            if was_new:
                asset_text.value = "게시글이 저장되었습니다. 이제 이미지를 선택할 수 있습니다."
                asset_text.color = "#16803C"
            version = current_post.current_version
            result_text.value = (
                f"저장 완료 · 버전 {version.version_number} · {current_post.status} · "
                f"백오피스에서 상세 이력을 확인할 수 있습니다."
            )
            result_text.color = "#16803C"
            save_button.text = "새 버전 저장"
        except ApiRequestError as error:
            result_text.value = f"저장 실패: {error}"
            result_text.color = "#B42318"
        except BackendUnavailableError:
            result_text.value = "저장 실패: 업무 서버에 연결할 수 없습니다."
            result_text.color = "#B42318"
        finally:
            save_button.disabled = False
            page.update()

    save_button.on_click = save_draft

    sidebar = ft.Container(
        width=236,
        bgcolor="#111827",
        padding=ft.Padding.symmetric(horizontal=18, vertical=24),
        content=ft.Column(
            controls=[
                ft.Text("MIMIR", size=22, weight=ft.FontWeight.BOLD, color="#FFFFFF"),
                ft.Text("Personal AI Workspace", size=12, color="#9CA3AF"),
                ft.Divider(height=32, color="#374151"),
                ft.ListTile(
                    leading=ft.Icon(ft.Icons.ARTICLE_OUTLINED, color="#A7F3D0"),
                    title=ft.Text("블로그 작성", color="#FFFFFF"),
                    selected=True,
                    selected_tile_color="#1F2937",
                ),
                ft.ListTile(
                    leading=ft.Icon(ft.Icons.CALENDAR_MONTH_OUTLINED, color="#D1D5DB"),
                    title=ft.Text("일정 (예정)", color="#D1D5DB"),
                ),
            ],
            expand=True,
        ),
    )

    content = ft.Container(
        expand=True,
        padding=ft.Padding.all(32),
        content=ft.Column(
            scroll=ft.ScrollMode.AUTO,
            controls=[
                ft.Row(
                    alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
                    controls=[
                        ft.Column(
                            spacing=4,
                            controls=[
                                ft.Text("블로그 초안 작성", size=28, weight=ft.FontWeight.BOLD),
                                ft.Text(
                                    "실제 작성과 수정은 여기서 진행하고, 전체 이력은 웹 백오피스에서 봅니다.",
                                    color="#5B6470",
                                ),
                            ],
                        ),
                        ft.OutlinedButton(
                            "연결 확인",
                            icon=ft.Icons.REFRESH,
                            on_click=refresh_status,
                            key="refresh-status",
                        ),
                    ],
                ),
                ft.Container(height=18),
                ft.Container(
                    bgcolor="#FFFFFF",
                    border_radius=16,
                    padding=24,
                    content=ft.Column(
                        controls=[
                            ft.Text("작성 내용", size=18, weight=ft.FontWeight.W_600),
                            ft.Text(
                                "저장할 때마다 이전 내용을 덮어쓰지 않고 새 버전으로 보관합니다.",
                                color="#5B6470",
                            ),
                            ft.Row(
                                controls=[
                                    post_selector,
                                    ft.OutlinedButton("목록 새로고침", icon=ft.Icons.REFRESH, on_click=refresh_posts),
                                    ft.OutlinedButton(
                                        "불러오기",
                                        icon=ft.Icons.DOWNLOAD_OUTLINED,
                                        on_click=load_selected,
                                        key="load-blog-post",
                                    ),
                                    ft.TextButton("새 글", icon=ft.Icons.ADD, on_click=start_new),
                                ]
                            ),
                            selector_text,
                            ft.Divider(height=20, color="#E4E7EC"),
                            title_field,
                            context_field,
                            body_field,
                            tags_field,
                            ft.Row(
                                alignment=ft.MainAxisAlignment.END,
                                controls=[save_button],
                            ),
                            ft.Divider(height=24, color="#E4E7EC"),
                            result_text,
                        ]
                    ),
                ),
                ft.Container(height=12),
                ft.Container(
                    bgcolor="#FFFFFF",
                    border_radius=16,
                    padding=24,
                    content=ft.Column(
                        controls=[
                            ft.Text("이미지", size=18, weight=ft.FontWeight.W_600),
                            ft.Text("JPEG, PNG, WebP · 이미지당 최대 15 MiB · 게시글당 최대 20장", color="#5B6470"),
                            asset_text,
                            ft.Row(
                                alignment=ft.MainAxisAlignment.END,
                                controls=[
                                    ft.OutlinedButton("이미지 선택", icon=ft.Icons.IMAGE_OUTLINED, on_click=pick_images),
                                    upload_button,
                                ],
                            ),
                            ft.Divider(height=20, color="#E4E7EC"),
                            analysis_text,
                            ft.Row(
                                alignment=ft.MainAxisAlignment.END,
                                controls=[analysis_refresh_button, analysis_cancel_button, analysis_button],
                            ),
                        ]
                    ),
                ),
                ft.Container(height=12),
                ft.Container(
                    bgcolor="#FFFFFF",
                    border_radius=16,
                    padding=24,
                    content=ft.Column(
                        controls=[
                            ft.Text("로컬 AI 수정", size=18, weight=ft.FontWeight.W_600),
                            ft.Text(
                                "현재 버전과 사실 메모, 이미지 분석 결과를 근거로 새 버전을 만듭니다.",
                                color="#5B6470",
                            ),
                            revision_field,
                            revision_target,
                            revision_text,
                            revision_history_text,
                            ft.Row(
                                alignment=ft.MainAxisAlignment.END,
                                controls=[
                                    revision_history_refresh_button,
                                    revision_refresh_button,
                                    revision_cancel_button,
                                    revision_button,
                                ],
                            ),
                        ]
                    ),
                ),
                ft.Container(height=12),
                ft.Container(
                    bgcolor="#FFFFFF",
                    border_radius=16,
                    padding=24,
                    content=ft.Column(
                        controls=[
                            ft.Text("버전 비교와 복원", size=18, weight=ft.FontWeight.W_600),
                            ft.Text(
                                "비교 결과를 확인한 뒤에만 이전 버전을 현재 초안으로 복원할 수 있습니다.",
                                color="#5B6470",
                            ),
                            ft.Row(controls=[version_before_selector, version_after_selector, compare_button]),
                            comparison_text,
                            version_text,
                            ft.Row(
                                alignment=ft.MainAxisAlignment.END,
                                controls=[restore_confirmation, restore_button],
                            ),
                        ]
                    ),
                ),
                ft.Container(height=12),
                ft.Row(spacing=8, controls=[status_icon, status_text]),
            ],
        ),
    )

    page.add(ft.Row(spacing=0, controls=[sidebar, content], expand=True))


def version_document(version: DraftVersion) -> list[str]:
    return [
        f"제목: {version.title}",
        f"태그: {', '.join(version.tags)}",
        "본문:",
        *version.body.splitlines(),
    ]


def revision_history_document(
    post: BlogPostDetail,
    turns: tuple[DraftRevisionTurn, ...],
) -> str:
    if not turns:
        return "저장된 AI 수정 이력이 없습니다."
    version_labels = {
        version.id: f"v{version.version_number}"
        for version in post.versions
    }
    lines: list[str] = []
    for turn in turns:
        base = version_labels.get(turn.base_version_id, "기준 버전 없음")
        result = (
            version_labels.get(turn.result_version_id, "결과 버전 없음")
            if turn.result_version_id is not None
            else "결과 버전 없음"
        )
        outcome = f"{turn.status} · {revision_target_label(turn.target)} · {base} → {result}"
        if turn.error_code:
            outcome += f" · {turn.error_code}"
        lines.extend((outcome, turn.revision_instruction, turn.created_at, ""))
    return "\n".join(lines).rstrip()


def revision_target_label(target: str) -> str:
    return {
        "FULL": "전체",
        "TITLE": "제목",
        "BODY": "본문",
        "TAGS": "태그",
    }.get(target, target)


def run() -> None:
    ft.run(build_app)


if __name__ == "__main__":
    run()
