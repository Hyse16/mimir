import json
from dataclasses import dataclass
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class SystemStatus:
    status: str
    privacy_mode: str
    database: str


class BackendUnavailableError(RuntimeError):
    """Raised when the business backend cannot provide a valid status."""


class MimirApiClient:
    def __init__(self, base_url: str, timeout_seconds: float = 3.0) -> None:
        self._base_url = base_url.rstrip("/")
        self._timeout_seconds = timeout_seconds

    def get_system_status(self) -> SystemStatus:
        request = Request(
            f"{self._base_url}/system/status",
            headers={"Accept": "application/json"},
            method="GET",
        )
        try:
            with urlopen(request, timeout=self._timeout_seconds) as response:
                payload = json.load(response)
        except (HTTPError, URLError, TimeoutError, json.JSONDecodeError) as error:
            raise BackendUnavailableError("Mimir backend is unavailable.") from error

        try:
            return SystemStatus(
                status=str(payload["status"]),
                privacy_mode=str(payload["privacyMode"]),
                database=str(payload["components"]["database"]),
            )
        except (KeyError, TypeError) as error:
            raise BackendUnavailableError("Mimir backend returned an invalid status.") from error
