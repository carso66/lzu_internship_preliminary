import pytest


def test_get_own_profile(client, base_url, alice_token):
    response = client.get(
        f"{base_url}/api/users/2",
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 0
    assert body["data"]["id"] == 2


@pytest.mark.xfail(reason="BUG-02: user profile has horizontal authorization flaw", strict=True)
def test_get_other_profile_forbidden(client, base_url, alice_token):
    response = client.get(
        f"{base_url}/api/users/3",
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 403
    assert response.json()["code"] != 0


def test_get_missing_profile_not_found(client, base_url, alice_token):
    response = client.get(
        f"{base_url}/api/users/999999",
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 404
    assert response.json()["code"] == 40401


def test_admin_orders_success(client, base_url, admin_token):
    response = client.get(
        f"{base_url}/api/admin/orders",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 0
    assert isinstance(body["data"]["items"], list)


def test_admin_orders_requires_token(client, base_url):
    response = client.get(f"{base_url}/api/admin/orders")
    assert response.status_code == 401
    assert response.json()["code"] == 40101


@pytest.mark.xfail(reason="BUG-03: normal user can access admin orders", strict=True)
def test_admin_orders_reject_normal_user(client, base_url, alice_token):
    response = client.get(
        f"{base_url}/api/admin/orders",
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 403
    assert response.json()["code"] != 0
