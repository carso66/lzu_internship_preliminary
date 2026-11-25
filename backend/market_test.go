package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
)

const testPhone = "10000000000"

type testMarket struct {
	*server
	handler http.Handler
}

func newTestMarket(t *testing.T) testMarket {
	t.Helper()
	db, err := openStore(filepath.Join(t.TempDir(), "market.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { db.Close() })
	s := &server{db}
	return testMarket{s, s.routes()}
}

func (m testMarket) request(method, path, body string) *httptest.ResponseRecorder {
	req := httptest.NewRequest(method, path, strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	m.handler.ServeHTTP(rec, req)
	return rec
}

func checkResponse(t *testing.T, rec *httptest.ResponseRecorder, status, code int) json.RawMessage {
	t.Helper()
	var envelope struct {
		Code    *int            `json:"code"`
		Message string          `json:"message"`
		Data    json.RawMessage `json:"data"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &envelope); err != nil {
		t.Fatalf("invalid JSON response: %v: %s", err, rec.Body)
	}
	if rec.Code != status || envelope.Code == nil || *envelope.Code != code ||
		envelope.Message == "" || envelope.Data == nil {
		t.Fatalf("want HTTP %d / code %d, got HTTP %d %s", status, code, rec.Code, rec.Body)
	}
	return envelope.Data
}

func decodeData[T any](t *testing.T, data json.RawMessage) T {
	t.Helper()
	var result T
	if err := json.Unmarshal(data, &result); err != nil {
		t.Fatal(err)
	}
	return result
}

func (m testMarket) product(t *testing.T, title, category string, stock int) product {
	t.Helper()
	body := fmt.Sprintf(`{"title":%q,"price":1990,"stock":%d,"category":%q,"sellerName":"测试店"}`, title, stock, category)
	p := decodeData[product](t, checkResponse(t, m.request("POST", "/api/products", body), 201, 0))
	if p.ID <= 0 || p.Stock != int64(stock) || p.Price != 1990 || p.Status != "on" {
		t.Fatalf("published product: %+v", p)
	}
	return p
}

func orderBody(id int64, quantity, phone string) string {
	return fmt.Sprintf(`{"productId":%d,"quantity":%s,"buyerName":"测试买家","buyerPhone":%q,"address":"测试自提点"}`, id, quantity, phone)
}

func (m testMarket) quantity(t *testing.T, query string, args ...any) int {
	t.Helper()
	var n int
	if err := m.db.QueryRow(query, args...).Scan(&n); err != nil {
		t.Fatal(err)
	}
	return n
}

func TestProductFiltersAndPagination(t *testing.T) {
	m := newTestMarket(t)
	older := m.product(t, "Green pencil", "stationery", 5)
	newer := m.product(t, "Blue pencil", "stationery", 5)
	m.product(t, "Pencil holder", "home", 5)
	m.product(t, "Notebook", "stationery", 5)
	off := m.product(t, "Red pencil", "stationery", 5)
	itemPath := fmt.Sprintf("/api/products/%d", off.ID)
	checkResponse(t, m.request("PATCH", itemPath+"/status", `{"status":"off"}`), 200, 0)
	detail := decodeData[product](t, checkResponse(t, m.request("GET", itemPath, ""), 200, 0))
	if detail.Status != "off" {
		t.Fatalf("detail status = %s", detail.Status)
	}
	type page struct {
		Total    int
		Page     int
		PageSize int
		Items    []product
	}
	for i, want := range []int64{newer.ID, older.ID, 0} {
		path := fmt.Sprintf("/api/products?keyword=pencil&status=on&category=stationery&pageSize=1&page=%d", i+1)
		p := decodeData[page](t, checkResponse(t, m.request("GET", path, ""), 200, 0))
		if p.Total != 2 || p.Page != i+1 || p.PageSize != 1 || p.Items == nil {
			t.Fatalf("page %d: %+v", i+1, p)
		}
		if want == 0 && len(p.Items) != 0 || want != 0 && (len(p.Items) != 1 || p.Items[0].ID != want) {
			t.Fatalf("page %d wanted id %d, got %+v", i+1, want, p.Items)
		}
	}
	checkResponse(t, m.request("GET", "/api/products?pageSize=51", ""), 400, 40001)
	checkResponse(t, m.request("GET", "/api/products/99999", ""), 404, 40401)
	checkResponse(t, m.request("PATCH", itemPath+"/status", `{"status":"sold"}`), 400, 40001)
	checkResponse(t, m.request("POST", "/api/products", `{"title":"x","price":0.5,"stock":1,"category":"y","sellerName":"z"}`), 400, 40001)
}

func TestOrderPriceAndLookup(t *testing.T) {
	m := newTestMarket(t)
	p := m.product(t, "画册", "图书", 8)
	for _, quantity := range []string{"0", "-1", `"2"`, "1.5"} {
		checkResponse(t, m.request("POST", "/api/orders", orderBody(p.ID, quantity, testPhone)), 400, 40001)
	}
	body := orderBody(p.ID, "2", testPhone)
	clientPrice := strings.TrimSuffix(body, "}") + `,"unitPrice":1}`
	checkResponse(t, m.request("POST", "/api/orders", clientPrice), 400, 40001)
	checkResponse(t, m.request("POST", "/api/orders", orderBody(99999, "1", testPhone)), 404, 40401)
	first := decodeData[order](t, checkResponse(t, m.request("POST", "/api/orders", body), 201, 0))
	detail := decodeData[order](t, checkResponse(t, m.request("GET", "/api/orders/"+first.OrderNo, ""), 200, 0))
	if first.OrderNo == "" || detail != first || detail.Quantity != 2 || detail.UnitPrice != 1990 || detail.TotalAmount != 3980 {
		t.Fatalf("order detail: %+v", detail)
	}
	if got := m.quantity(t, "SELECT stock FROM products WHERE id = ?", p.ID); got != 6 {
		t.Fatalf("stock = %d, want 6", got)
	}
	second := decodeData[order](t, checkResponse(t, m.request("POST", "/api/orders", body), 201, 0))
	checkResponse(t, m.request("POST", "/api/orders", orderBody(p.ID, "1", "10000000001")), 201, 0)
	page := decodeData[struct {
		Total int
		Items []order
	}](t, checkResponse(t, m.request("GET",
		"/api/orders?buyerPhone="+testPhone+"&pageSize=1&page=1", ""), 200, 0))
	if page.Total != 2 || len(page.Items) != 1 || page.Items[0].OrderNo != second.OrderNo {
		t.Fatalf("buyer page: %+v", page)
	}
	checkResponse(t, m.request("GET", "/api/orders/missing", ""), 404, 40401)
	checkResponse(t, m.request("GET", "/api/orders?buyerPhone=invalid", ""), 400, 40001)
	checkResponse(t, m.request("POST", "/api/orders", orderBody(p.ID, "8", testPhone)), 409, 40901)
	checkResponse(t, m.request("PATCH", fmt.Sprintf("/api/products/%d/status", p.ID), `{"status":"off"}`), 200, 0)
	checkResponse(t, m.request("POST", "/api/orders", body), 409, 40902)
}

func TestOrderRollsBackWhenInsertFails(t *testing.T) {
	m := newTestMarket(t)
	p := m.product(t, "文具盒", "文具", 1)
	_, err := m.db.Exec(`CREATE TRIGGER fail_order BEFORE INSERT ON orders
		BEGIN SELECT RAISE(ABORT, 'forced insertion failure'); END`)
	if err != nil {
		t.Fatal(err)
	}
	rec := m.request("POST", "/api/orders", orderBody(p.ID, "1", testPhone))
	checkResponse(t, rec, 500, 50000)
	if strings.Contains(rec.Body.String(), "forced insertion failure") {
		t.Fatal("database error was exposed to client")
	}
	stock := m.quantity(t, "SELECT stock FROM products WHERE id = ?", p.ID)
	orders := m.quantity(t, "SELECT count(*) FROM orders")
	if stock != 1 || orders != 0 {
		t.Fatalf("after forced failure: stock=%d orders=%d", stock, orders)
	}
}

// Each goroutine constructs an independent HTTP request. The shared start channel
// releases the whole group before the caller reads the first response.
func (m testMarket) simultaneous(n int, path, body string) <-chan *httptest.ResponseRecorder {
	start := make(chan struct{})
	results := make(chan *httptest.ResponseRecorder, n)
	for range n {
		go func() {
			<-start
			results <- m.request("POST", path, body)
		}()
	}
	close(start)
	return results
}

func TestConcurrentOrders(t *testing.T) {
	m := newTestMarket(t)
	p := m.product(t, "最后一件", "文具", 1)
	responses := m.simultaneous(10, "/api/orders", orderBody(p.ID, "1", testPhone))
	var successes, conflicts int
	for range 10 {
		rec := <-responses
		if rec.Code == http.StatusCreated {
			checkResponse(t, rec, 201, 0)
			successes++
		} else {
			checkResponse(t, rec, 409, 40901)
			conflicts++
		}
	}
	stock := m.quantity(t, "SELECT stock FROM products WHERE id = ?", p.ID)
	orders := m.quantity(t, "SELECT COUNT(*) FROM orders WHERE product_id = ?", p.ID)
	t.Logf("201=%d 40901=%d stock=%d orders=%d", successes, conflicts, stock, orders)
	if successes != 1 || conflicts != 9 || stock != 0 || orders != 1 {
		t.Fatal("ten requests must reserve exactly one unit and create one order")
	}
}

func TestConcurrentPayments(t *testing.T) {
	m := newTestMarket(t)
	p := m.product(t, "笔记本", "文具", 3)
	o := decodeData[order](t, checkResponse(t, m.request("POST", "/api/orders", orderBody(p.ID, "2", testPhone)), 201, 0))
	checkResponse(t, m.request("POST", "/api/payments", `{"orderNo":"missing","method":"wechat"}`), 404, 40401)
	checkResponse(t, m.request("POST", "/api/payments", fmt.Sprintf(`{"orderNo":%q,"method":"cash"}`, o.OrderNo)), 400, 40001)
	body := fmt.Sprintf(`{"orderNo":%q,"method":"wechat"}`, o.OrderNo)
	results := m.simultaneous(10, "/api/payments", body)
	var successes, conflicts int
	var paidAt string
	for range 10 {
		rec := <-results
		if rec.Code == http.StatusOK {
			pay := decodeData[struct {
				PaymentID string
				OrderNo   string
				Amount    int64
				Status    string
				PaidAt    string
			}](t, checkResponse(t, rec, 200, 0))
			if pay.PaymentID == "" || pay.OrderNo != o.OrderNo || pay.Amount != 3980 || pay.Status != "paid" || pay.PaidAt == "" {
				t.Fatalf("payment receipt: %+v", pay)
			}
			paidAt = pay.PaidAt
			successes++
		} else {
			checkResponse(t, rec, 409, 40903)
			conflicts++
		}
	}
	checkResponse(t, m.request("POST", "/api/payments", body), 409, 40903)
	detail := decodeData[order](t, checkResponse(t, m.request("GET", "/api/orders/"+o.OrderNo, ""), 200, 0))
	receipts := m.quantity(t, "SELECT COUNT(*) FROM payments WHERE order_no = ?", o.OrderNo)
	t.Logf("200=%d 40903=%d receipts=%d", successes, conflicts, receipts)
	if successes != 1 || conflicts != 9 || receipts != 1 || detail.Status != "paid" || detail.PaidAt == nil || *detail.PaidAt != paidAt {
		t.Fatalf("unexpected paid order: %+v", detail)
	}
}
