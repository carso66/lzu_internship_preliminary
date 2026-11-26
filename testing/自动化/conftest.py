import importlib.util
import sqlite3
import threading
import time
from pathlib import Path

import pytest
import requests

PROJECT_ROOT = Path(__file__).resolve().parents[2]
SUT_PATH = PROJECT_ROOT / "testing" / "sut" / "app.py"


@pytest.fixture
def sut(tmp_path):
    """Load the original SUT with an isolated temporary SQLite database."""
    if not SUT_PATH.is_file():
        pytest.fail(f"找不到原始 SUT：{SUT_PATH}")

    spec = importlib.util.spec_from_file_location("campusmall_sut_under_test", SUT_PATH)
    if spec is None or spec.loader is None:
        pytest.fail(f"无法加载 SUT：{SUT_PATH}")
    app = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(app)

    # Do not touch testing/sut/sut.db: redirect before init_database().
    app.DB_PATH = str(tmp_path / "isolated_sut.db")
    app.init_database()

    server = app.MallServer(("127.0.0.1", 0), app.ApiHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    base_url = f"http://127.0.0.1:{server.server_address[1]}"

    last_error = None
    for _ in range(50):
        try:
            response = requests.get(base_url + "/health", timeout=0.5)
            if response.status_code == 200:
                break
        except requests.RequestException as exc:
            last_error = exc
        time.sleep(0.05)
    else:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
        pytest.fail(f"测试服务器启动失败：{last_error!r}")

    yield {"app": app, "base_url": base_url, "db_path": app.DB_PATH, "token": "tk_2"}

    server.shutdown()
    server.server_close()
    thread.join(timeout=3)


@pytest.fixture
def api(sut):
    class Client:
        def __init__(self, base_url, token):
            self.base_url, self.token = base_url, token

        def request(self, method, path, *, auth=True, **kwargs):
            headers = dict(kwargs.pop("headers", {}) or {})
            if auth:
                headers.setdefault("Authorization", f"Bearer {self.token}")
            return requests.request(method, self.base_url + path, headers=headers,
                                    timeout=10, **kwargs)

        def get(self, path, **kwargs):
            return self.request("GET", path, **kwargs)

        def post(self, path, **kwargs):
            return self.request("POST", path, **kwargs)

    return Client(sut["base_url"], sut["token"])


@pytest.fixture
def db(sut):
    def query(sql, params=()):
        conn = sqlite3.connect(sut["db_path"])
        conn.row_factory = sqlite3.Row
        try:
            return [dict(row) for row in conn.execute(sql, params).fetchall()]
        finally:
            conn.close()
    return query

@pytest.fixture
def sut_server(sut):
    """兼容旧版 test_security.py 的测试夹具。"""
    return sut
