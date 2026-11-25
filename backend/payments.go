package main

import (
	"database/sql"
	"errors"
	"net/http"
)

func (s *server) pay(w http.ResponseWriter, r *http.Request) error {
	var input struct {
		OrderNo string `json:"orderNo"`
		Method  string `json:"method"`
	}
	if err := readJSON(w, r, &input); err != nil {
		return err
	}
	orderNo, err := textField(input.OrderNo, "orderNo", 1, 100)
	if err != nil {
		return err
	}
	if input.Method != "alipay" && input.Method != "wechat" {
		return problem(400, 40001, "method 只能是 alipay 或 wechat")
	}
	paymentID, err := reference("PAY")
	if err != nil {
		return err
	}
	ctx := r.Context()
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var amount int64
	var paidAt string
	err = tx.QueryRowContext(ctx, `UPDATE orders
		SET status = 'paid', paid_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
		WHERE order_no = ? AND status = 'created' RETURNING total_amount, paid_at`,
		orderNo).Scan(&amount, &paidAt)
	if errors.Is(err, sql.ErrNoRows) {
		var exists bool
		if err := tx.QueryRowContext(ctx, "SELECT EXISTS (SELECT 1 FROM orders WHERE order_no = ?)", orderNo).Scan(&exists); err != nil {
			return err
		}
		if !exists {
			return problem(404, 40401, "订单不存在")
		}
		return problem(409, 40903, "订单已支付")
	}
	if err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `INSERT INTO payments(payment_id, order_no, amount, method, paid_at)
		VALUES (?, ?, ?, ?, ?)`, paymentID, orderNo, amount, input.Method, paidAt); err != nil {
		return err
	}
	if err := tx.Commit(); err != nil {
		return err
	}
	return respond(w, http.StatusOK, map[string]any{
		"paymentId": paymentID, "orderNo": orderNo, "amount": amount, "status": "paid", "paidAt": paidAt,
	})
}
