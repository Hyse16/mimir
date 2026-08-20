from mimir_application.config import AppConfig


def test_default_backend_url(monkeypatch) -> None:
    monkeypatch.delenv("MIMIR_API_BASE_URL", raising=False)

    config = AppConfig.from_environment()

    assert config.api_base_url == "http://127.0.0.1:8080/api/v1"


def test_normalizes_configured_backend_url(monkeypatch) -> None:
    monkeypatch.setenv("MIMIR_API_BASE_URL", "http://localhost:9090/api/v1/")

    config = AppConfig.from_environment()

    assert config.api_base_url == "http://localhost:9090/api/v1"
