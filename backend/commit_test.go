package main

import "testing"

func TestOrderRollsBackWhenCommitFails(t *testing.T) {
	m := newTestMarket(t)
	p := m.product(t, "练习本", "文具", 1)
	// The INSERT succeeds. Only the outer COMMIT checks the missing reference.
	_, err := m.db.Exec(`
		CREATE TABLE commit_check (
			product_id INTEGER REFERENCES products(id) DEFERRABLE INITIALLY DEFERRED
		);
		CREATE TRIGGER fail_first_buyer AFTER INSERT ON orders
		WHEN NEW.buyer_phone = '10000000000'
		BEGIN
			INSERT INTO commit_check VALUES (-1);
		END;`)
	if err != nil {
		t.Fatal(err)
	}
	checkResponse(t, m.request("POST", "/api/orders", orderBody(p.ID, "1", testPhone)), 500, 50000)
	stock := m.quantity(t, "SELECT stock FROM products WHERE id = ?", p.ID)
	orders := m.quantity(t, "SELECT count(*) FROM orders")
	if stock != 1 || orders != 0 {
		t.Errorf("after failed COMMIT: stock=%d orders=%d; want 1, 0", stock, orders)
	}
	checkResponse(t, m.request("POST", "/api/orders", orderBody(p.ID, "1", "10000000001")), 201, 0)
	if got := m.quantity(t, "SELECT count(*) FROM orders"); got != 1 {
		t.Fatalf("after next request: orders=%d, want 1", got)
	}
}
