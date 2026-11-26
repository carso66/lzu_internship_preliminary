import pytest


def test_create_review_success(client, base_url, alice_token):
    response = client.post(
        f"{base_url}/api/product-reviews",
        json={"productId": 1, "content": "自动化评价", "rating": 5},
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 201
    body = response.json()
    assert body["code"] == 0
    assert body["data"]["rating"] == 5


def test_create_review_invalid_rating(client, base_url, alice_token):
    response = client.post(
        f"{base_url}/api/product-reviews",
        json={"productId": 1, "content": "自动化评价", "rating": 6},
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 400
    assert response.json()["code"] == 40001


def test_create_review_content_boundary(client, base_url, alice_token):
    response = client.post(
        f"{base_url}/api/product-reviews",
        json={"productId": 1, "content": "好" * 200, "rating": 5},
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 201
    assert response.json()["code"] == 0


def test_review_page_renders_html(client, base_url):
    response = client.get(
        f"{base_url}/api/product-reviews",
        params={"productId": 1},
    )
    assert response.status_code == 200
    assert "text/html" in response.headers["Content-Type"]
    assert "商品评价" in response.text


@pytest.mark.xfail(reason="BUG-12: review page is vulnerable to stored XSS", strict=True)
def test_review_page_xss_escaped(client, base_url, alice_token):
    client.post(
        f"{base_url}/api/product-reviews",
        json={"productId": 1, "content": "<img src=x onerror=alert(1)>", "rating": 5},
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    response = client.get(
        f"{base_url}/api/product-reviews",
        params={"productId": 1},
    )
    assert response.status_code == 200
    assert "&lt;img" in response.text


def test_create_review_over_length_rejected(client, base_url, alice_token):
    response = client.post(
        f"{base_url}/api/product-reviews",
        json={"productId": 1, "content": "好" * 201, "rating": 5},
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 400
    assert response.json()["code"] == 40001
