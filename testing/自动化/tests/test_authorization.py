import pytest


@pytest.mark.parametrize("path", [
    "/api/admin/orders",
    "/api/users/3",
    "/api/orders/2",
])
def test_user_cannot_read_admin_or_other_users_data(api, path):
    response = api.get(path)
    assert response.status_code in (401, 403), (
        f"{path} 应拒绝普通用户访问，实际 HTTP {response.status_code}: {response.text}"
    )
