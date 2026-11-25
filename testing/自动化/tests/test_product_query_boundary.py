import pytest


@pytest.mark.parametrize("page_size", [0, 51])
def test_product_page_size_out_of_range_returns_400(api, page_size):
    """商品列表 pageSize 必须限制在 1~50。"""
    response = api.get(
        "/api/products",
        auth=False,
        params={"pageSize": page_size},
    )

    assert response.status_code == 400, (
        f"pageSize={page_size} 应返回 HTTP 400，"
        f"实际为 {response.status_code}: {response.text}"
    )


def test_product_detail_non_numeric_id_returns_400(api):
    """商品详情 ID 非数字时应返回400，而不是500。"""
    response = api.get("/api/products/abc", auth=False)

    assert response.status_code == 400, (
        f"非数字商品 ID 应返回 HTTP 400，"
        f"实际为 {response.status_code}: {response.text}"
    )
