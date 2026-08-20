import io
import json
from unittest.mock import patch

import pytest

from mimir_application.api_client import BackendUnavailableError, MimirApiClient


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
