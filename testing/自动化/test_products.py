import pytest


def test_product_list_default(client, base_url):
    response = client.get(f"{base_url}/api/products")
    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 0
    assert body["data"]["total"] > 0
    assert isinstance(body["data"]["items"], list)


def test_product_list_page_size_50(client, base_url):
    response = client.get(f"{base_url}/api/products", params={"page": 1, "pageSize": 50})
    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 0
    assert body["data"]["pageSize"] == 50


@pytest.mark.xfail(reason="BUG-04: pageSize is not validated", strict=True)
def test_product_page_size_51_rejected(client, base_url):
    response = client.get(f"{base_url}/api/products", params={"page": 1, "pageSize": 51})
    assert response.status_code == 400
    assert response.json()["code"] == 40001


@pytest.mark.xfail(reason="BUG-06: keyword is vulnerable to SQL injection", strict=True)
def test_product_keyword_injection_is_safe(client, base_url):
    response = client.get(
        f"{base_url}/api/products",
        params={"keyword": "' OR 1=1 --"},
    )
    assert response.status_code == 200
    assert response.json()["data"]["total"] == 0


def test_product_detail_success(client, base_url):
    response = client.get(f"{base_url}/api/products/1")
    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 0
    assert body["data"]["id"] == 1


@pytest.mark.xfail(reason="BUG-05: invalid product id returns 500", strict=True)
def test_product_detail_invalid_id(client, base_url):
    response = client.get(f"{base_url}/api/products/abc")
    assert response.status_code == 400
    assert response.json()["code"] == 40001


def test_product_detail_not_found(client, base_url):
    response = client.get(f"{base_url}/api/products/999999")
    assert response.status_code == 404
    assert response.json()["code"] == 40401


def test_create_product_success(client, base_url, alice_token):
    payload = {
        "title": "自动化测试商品",
        "price": 1990,
        "stock": 10,
        "category": "文具",
        "description": "pytest 创建",
    }
    response = client.post(
        f"{base_url}/api/products",
        json=payload,
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 201
    body = response.json()
    assert body["code"] == 0
    assert body["data"]["status"] == "on"


def test_create_product_invalid_category(client, base_url, alice_token):
    payload = {
        "title": "非法分类商品",
        "price": 1990,
        "stock": 10,
        "category": "不存在分类",
    }
    response = client.post(
        f"{base_url}/api/products",
        json=payload,
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 400
    assert response.json()["code"] == 40001


def test_update_product_status_success(client, base_url, alice_token):
    response = client.patch(
        f"{base_url}/api/products/2/status",
        json={"status": "off"},
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 200
    assert response.json()["code"] == 0


def test_update_product_status_invalid_value(client, base_url, alice_token):
    response = client.patch(
        f"{base_url}/api/products/2/status",
        json={"status": "disabled"},
        headers={"Authorization": f"Bearer {alice_token}"},
    )
    assert response.status_code == 400
    assert response.json()["code"] == 40001


@pytest.mark.xfail(reason="BUG-07: product status update has authorization flaw", strict=True)
def test_update_product_status_other_user_forbidden(client, base_url, bob_token):
    response = client.patch(
        f"{base_url}/api/products/2/status",
        json={"status": "off"},
        headers={"Authorization": f"Bearer {bob_token}"},
    )
    assert response.status_code == 403
    assert response.json()["code"] != 0
