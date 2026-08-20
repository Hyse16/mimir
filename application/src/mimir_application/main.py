import flet as ft

from mimir_application.api_client import BackendUnavailableError, MimirApiClient
from mimir_application.config import AppConfig


def build_app(page: ft.Page, config: AppConfig | None = None) -> None:
    settings = config or AppConfig.from_environment()
    client = MimirApiClient(settings.api_base_url)

    page.title = "Mimir"
    page.theme_mode = ft.ThemeMode.LIGHT
    page.padding = 0
    page.bgcolor = "#F4F6F8"

    status_text = ft.Text("연결 확인 전", color="#5B6470", key="backend-status")
    status_icon = ft.Icon(ft.Icons.CIRCLE_OUTLINED, color="#6B7280", size=14)

    def refresh_status(_: ft.ControlEvent | None = None) -> None:
        try:
            status = client.get_system_status()
            status_icon.name = ft.Icons.CHECK_CIRCLE
            status_icon.color = "#16803C"
            status_text.value = (
                f"서버 {status.status} · DB {status.database} · {status.privacy_mode}"
            )
            status_text.color = "#16803C"
        except BackendUnavailableError:
            status_icon.name = ft.Icons.ERROR_OUTLINE
            status_icon.color = "#B42318"
            status_text.value = "업무 서버에 연결할 수 없습니다"
            status_text.color = "#B42318"
        page.update()

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
                    leading=ft.Icon(ft.Icons.AUTO_AWESOME, color="#A7F3D0"),
                    title=ft.Text("비서", color="#FFFFFF"),
                    selected=True,
                    selected_tile_color="#1F2937",
                ),
                ft.ListTile(
                    leading=ft.Icon(ft.Icons.ARTICLE_OUTLINED, color="#D1D5DB"),
                    title=ft.Text("블로그", color="#D1D5DB"),
                ),
                ft.ListTile(
                    leading=ft.Icon(ft.Icons.CALENDAR_MONTH_OUTLINED, color="#D1D5DB"),
                    title=ft.Text("일정", color="#D1D5DB"),
                ),
            ],
            expand=True,
        ),
    )

    content = ft.Container(
        expand=True,
        padding=ft.Padding.all(32),
        content=ft.Column(
            controls=[
                ft.Row(
                    alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
                    controls=[
                        ft.Column(
                            spacing=4,
                            controls=[
                                ft.Text("무엇을 도와드릴까요?", size=28, weight=ft.FontWeight.BOLD),
                                ft.Text(
                                    "블로그 글 생성과 실제 작업은 이 애플리케이션에서 진행합니다.",
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
                            ft.Text("빠른 시작", size=18, weight=ft.FontWeight.W_600),
                            ft.TextField(
                                hint_text="예: 이 사진들로 네이버 블로그 글을 작성해줘",
                                multiline=True,
                                min_lines=3,
                                max_lines=6,
                                key="assistant-input",
                            ),
                            ft.Row(
                                alignment=ft.MainAxisAlignment.END,
                                controls=[
                                    ft.FilledButton(
                                        "요청 시작",
                                        icon=ft.Icons.ARROW_FORWARD,
                                        disabled=True,
                                        tooltip="블로그 흐름은 STEP 2에서 연결됩니다.",
                                    )
                                ],
                            ),
                        ]
                    ),
                ),
                ft.Container(height=12),
                ft.Row(spacing=8, controls=[status_icon, status_text]),
            ]
        ),
    )

    page.add(ft.Row(spacing=0, controls=[sidebar, content], expand=True))


def run() -> None:
    ft.run(build_app)


if __name__ == "__main__":
    run()
