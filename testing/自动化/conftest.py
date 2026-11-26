import os
import subprocess
import sys
import time
from pathlib import Path

import pytest
import requests

EXTERNAL_BASE_URL = os.environ.get("SUT_BASE_URL")
DEFAULT_BASE_URL = "http://127.0.0.1:18081"
BASE_URL = EXTERNAL_BASE_URL or DEFAULT_BASE_URL

ROOT = Path(__file__).resolve().parents[2]
SUT_PATH = ROOT / "testing" / "sut" / "app.py"


def _health_ready(client):
    try:
        response = client.get(f"{BASE_URL}/health", timeout=1)
        return response.status_code == 200
    except requests.RequestException:
        return False


@pytest.fixture(scope="session")
def client(sut):
    return requests.Session()


@pytest.fixture(scope="session", autouse=True)
def sut():
    external_client = requests.Session()
    already_running = _health_ready(external_client)
    external_client.close()

    if EXTERNAL_BASE_URL:
        if not already_running:
            raise RuntimeError(
                f"SUT_BASE_URL={EXTERNAL_BASE_URL} 未启动或健康检查失败，"
                "请先启动对应服务后再运行测试"
            )
        yield BASE_URL
        return

    process = None
    process = subprocess.Popen(
        [
            sys.executable,
            str(SUT_PATH),
            "--host",
            "127.0.0.1",
            "--port",
            "18081",
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )

    probe = requests.Session()
    deadline = time.time() + 8
    ready = False
    while time.time() < deadline:
        if _health_ready(probe):
            ready = True
            break
        time.sleep(0.1)
    probe.close()

    if not ready:
        if process is not None:
            process.terminate()
        raise RuntimeError("SUT did not become ready on http://127.0.0.1:18081")

    yield BASE_URL

    process.terminate()
    try:
        process.wait(timeout=3)
    except subprocess.TimeoutExpired:
        process.kill()


@pytest.fixture(scope="session")
def base_url(sut):
    return BASE_URL


@pytest.fixture
def login(client, base_url):
    def _login(username: str, password: str) -> str:
        response = client.post(
            f"{base_url}/api/auth/login",
            json={"username": username, "password": password},
        )
        assert response.status_code == 200
        body = response.json()
        assert body["code"] == 0
        token = body["data"]["token"]
        assert token
        return token

    return _login


@pytest.fixture
def alice_token(login):
    return login("alice", "Alice@123")


@pytest.fixture
def bob_token(login):
    return login("bob", "Bob@123")


@pytest.fixture
def admin_token(login):
    return login("admin", "Admin@123")
