from dataclasses import dataclass
from os import environ


@dataclass(frozen=True)
class AppConfig:
    api_base_url: str

    @classmethod
    def from_environment(cls) -> "AppConfig":
        value = environ.get("MIMIR_API_BASE_URL", "http://127.0.0.1:8080/api/v1")
        return cls(api_base_url=value.rstrip("/"))
