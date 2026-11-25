def test_review_content_is_html_escaped_on_output(api):
    payload = {
        "productId": 1,
        "content": "<script>alert('campusmall-xss')</script>",
        "rating": 5,
    }
    created = api.post("/api/product-reviews", json=payload)
    assert created.status_code in (200, 201), created.text

    page = api.get("/api/product-reviews", auth=False, params={"productId": 1})
    assert page.status_code == 200, page.text
    assert "<script>alert('campusmall-xss')</script>" not in page.text
    assert "&lt;script&gt;" in page.text
