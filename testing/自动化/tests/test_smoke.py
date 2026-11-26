def test_health_endpoint_is_available(api):
    response = api.get("/health", auth=False)
    assert response.status_code == 200
    assert response.json()["code"] == 0
