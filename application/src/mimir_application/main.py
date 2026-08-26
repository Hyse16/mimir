import mimetypes

import flet as ft

from mimir_application.api_client import (
    AiJob,
    ApiRequestError,
    BackendUnavailableError,
    BlogPostDetail,
    ImageUpload,
    MimirApiClient,
)
from mimir_application.config import AppConfig


def build_app(page: ft.Page, config: AppConfig | None = None) -> None:
    settings = config or AppConfig.from_environment()
    client = MimirApiClient(settings.api_base_url)
    current_post: BlogPostDetail | None = None
    current_job: AiJob | None = None
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
    save_button = ft.FilledButton("첫 초안 저장", icon=ft.Icons.SAVE_OUTLINED)
    upload_button = ft.FilledButton("선택 이미지 업로드", icon=ft.Icons.UPLOAD, disabled=True)
    analysis_button = ft.FilledButton("이미지 분석 시작", icon=ft.Icons.AUTO_AWESOME, disabled=True)
    analysis_refresh_button = ft.OutlinedButton("분석 상태 새로고침", icon=ft.Icons.REFRESH, disabled=True)
    analysis_cancel_button = ft.OutlinedButton("분석 취소", icon=ft.Icons.CANCEL_OUTLINED, disabled=True)

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
        nonlocal current_post, current_job
        current_post = post
        post_selector.value = post.id
        title_field.value = post.current_version.title
        context_field.value = post.visit_context
        body_field.value = post.current_version.body
        tags_field.value = ", ".join(post.current_version.tags)
        save_button.text = "새 버전 저장"
        selector_text.value = f"{post.title} · 현재 버전 {post.current_version.version_number} · {post.status}"
        selector_text.color = "#16803C"
        result_text.value = "기존 초안을 불러왔습니다. 저장하면 새 버전으로 보관됩니다."
        result_text.color = "#5B6470"
        selected_images.clear()
        upload_button.disabled = True
        asset_text.value = f"저장된 이미지 {len(post.assets)} / 20장"
        asset_text.color = "#5B6470"
        current_job = None
        analysis_button.text = "이미지 분석 시작"
        analysis_button.disabled = len(post.assets) == 0
        analysis_refresh_button.disabled = True
        analysis_cancel_button.disabled = True
        analysis_text.value = "이미지 업로드 후 구조화 분석을 시작할 수 있습니다."
        analysis_text.color = "#5B6470"

    def show_job(job: AiJob) -> None:
        nonlocal current_job
        current_job = job
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
        nonlocal current_post, current_job
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
        current_job = None
        analysis_button.text = "이미지 분석 시작"
        analysis_button.disabled = True
        analysis_refresh_button.disabled = True
        analysis_cancel_button.disabled = True
        analysis_text.value = "이미지 업로드 후 구조화 분석을 시작할 수 있습니다."
        analysis_text.color = "#5B6470"
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
                client.retry_failed_image_analysis(current_job.id)
                if current_job is not None and current_job.status in {"PARTIAL_FAILED", "FAILED"}
                else client.create_image_analysis_job(current_post.id)
            )
            show_job(job)
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
        if current_job is None:
            return
        try:
            show_job(client.get_ai_job(current_job.id))
        except (ApiRequestError, BackendUnavailableError):
            analysis_text.value = "이미지 분석 상태를 불러오지 못했습니다."
            analysis_text.color = "#B42318"
        page.update()

    def cancel_analysis(_: ft.ControlEvent) -> None:
        if current_job is None:
            return
        analysis_cancel_button.disabled = True
        analysis_text.value = "이미지 분석 취소 요청 중…"
        analysis_text.color = "#5B6470"
        page.update()
        try:
            show_job(client.cancel_image_analysis_job(current_job.id))
        except (ApiRequestError, BackendUnavailableError):
            analysis_text.value = "이미지 분석을 취소하지 못했습니다."
            analysis_text.color = "#B42318"
            analysis_cancel_button.disabled = False
        page.update()

    analysis_button.on_click = start_analysis
    analysis_refresh_button.on_click = refresh_analysis
    analysis_cancel_button.on_click = cancel_analysis

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
            if current_post is None:
                current_post = client.create_blog_post(
                    title=title,
                    visit_context=context_field.value or "",
                    body=body_field.value or "",
                    tags=parse_tags(),
                )
                asset_text.value = "게시글이 저장되었습니다. 이제 이미지를 선택할 수 있습니다."
                asset_text.color = "#16803C"
            else:
                current_post = client.add_blog_version(
                    current_post.id,
                    base_version_id=current_post.current_version_id,
                    title=title,
                    body=body_field.value or "",
                    tags=parse_tags(),
                    visit_context=context_field.value or "",
                )
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
                                    ft.OutlinedButton("불러오기", icon=ft.Icons.DOWNLOAD_OUTLINED, on_click=load_selected),
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
                ft.Row(spacing=8, controls=[status_icon, status_text]),
            ],
        ),
    )

    page.add(ft.Row(spacing=0, controls=[sidebar, content], expand=True))


def run() -> None:
    ft.run(build_app)


if __name__ == "__main__":
    run()
