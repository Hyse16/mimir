from typing import Any

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
