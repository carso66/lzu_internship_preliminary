import pytest


def test_health_ok(client, base_url):
    response = client.get(f"{base_url}/health")
    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 0
    assert body["data"]["status"] == "up"


def test_health_post_not_allowed(client, base_url):
    response = client.post(f"{base_url}/health")
    assert response.status_code == 404
    assert response.json()["code"] == 40401


def test_login_success(client, base_url, login):
    token = login("alice", "Alice@123")
    assert token.startswith("tk_2")


def test_login_invalid_password(client, base_url):
    response = client.post(
        f"{base_url}/api/auth/login",
        json={"username": "alice", "password": "wrong"},
    )
    assert response.status_code == 401
    assert response.json()["code"] == 40101


def test_login_empty_payload(client, base_url):
    response = client.post(f"{base_url}/api/auth/login", json={})
    assert response.status_code == 401
    assert response.json()["code"] == 40101


def test_logout_success(client, base_url, alice_token):
    response = client.post(
        f"{base_url}/api/auth/logout",
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 200
    assert response.json()["code"] == 0


def test_logout_requires_token(client, base_url):
    response = client.post(f"{base_url}/api/auth/logout")
    assert response.status_code == 401
    assert response.json()["code"] == 40101


@pytest.mark.xfail(reason="BUG-01: token remains valid after logout", strict=True)
def test_logout_and_reuse_token(client, base_url, alice_token):
    client.post(
        f"{base_url}/api/auth/logout",
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    response = client.get(
        f"{base_url}/api/users/2",
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 401
    assert response.json()["code"] == 40101


@pytest.mark.xfail(reason="BUG-01: predictable token is accepted", strict=True)
def test_predictable_token_rejected(client, base_url):
    response = client.get(
        f"{base_url}/api/users/2",
        headers={"Authorization": "Bearer tk_2"},
    )
    assert response.status_code == 401
    assert response.json()["code"] == 40101
