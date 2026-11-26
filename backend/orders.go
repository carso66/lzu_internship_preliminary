package main

import (
	"database/sql"
	"errors"
	"math"
	"net/http"
)

type orderRequest struct {
	ProductID  int64  `json:"productId"`
	Quantity   int64  `json:"quantity"`
	BuyerName  string `json:"buyerName"`
	BuyerPhone string `json:"buyerPhone"`
	Address    string `json:"address"`
}

func validPhone(s string) bool {
	if len(s) != 11 || s[0] != '1' {
		return false
	}
	for _, ch := range s {
		if ch < '0' || ch > '9' {
			return false
		}
	}
	return true
}

func (input *orderRequest) validate() error {
	if input.ProductID < 1 {
		return problem(400, 40001, "productId 必须是正整数")
	}
	if input.Quantity < 1 {
		return problem(400, 40001, "quantity 必须是正整数")
	}
	if !validPhone(input.BuyerPhone) {
		return problem(400, 40001, "buyerPhone 必须是 1 开头的 11 位数字")
	}
	var err error
	if input.BuyerName, err = textField(input.BuyerName, "buyerName", 1, 50); err != nil {
		return err
	}
	input.Address, err = textField(input.Address, "address", 1, 300)
	return err
}

type order struct {
	ID           int64   `json:"id"`
	OrderNo      string  `json:"orderNo"`
	ProductID    int64   `json:"productId"`
	ProductTitle string  `json:"productTitle"`
	Quantity     int64   `json:"quantity"`
	UnitPrice    int64   `json:"unitPrice"`
	TotalAmount  int64   `json:"totalAmount"`
	BuyerName    string  `json:"buyerName"`
	BuyerPhone   string  `json:"buyerPhone"`
	Address      string  `json:"address"`
	Status       string  `json:"status"`
	CreatedAt    string  `json:"createdAt"`
	PaidAt       *string `json:"paidAt"`
}

const orderColumns = `id, order_no, product_id, product_title, quantity, unit_price, total_amount,
	buyer_name, buyer_phone, address, status, created_at, paid_at`

func scanOrder(row interface{ Scan(...any) error }) (order, error) {
	var o order
	err := row.Scan(&o.ID, &o.OrderNo, &o.ProductID, &o.ProductTitle, &o.Quantity,
		&o.UnitPrice, &o.TotalAmount, &o.BuyerName, &o.BuyerPhone, &o.Address,
		&o.Status, &o.CreatedAt, &o.PaidAt)
	return o, err
}

func (s *server) createOrder(w http.ResponseWriter, r *http.Request) error {
	var input orderRequest
	if err := readJSON(w, r, &input); err != nil {
		return err
	}
	if err := input.validate(); err != nil {
		return err
	}
	orderNo, err := reference("SO")
	if err != nil {
		return err
	}
	ctx := r.Context()
	tx, err := s.begin(ctx)
	if err != nil {
		return err
	}
	defer tx.close()

	// Taking stock and reading its price in one statement keeps the order snapshot
	// tied to the product the database actually allowed this request to buy.
	var title string
	var price int64
	err = tx.QueryRowContext(ctx, `UPDATE products SET stock = stock - ?
		WHERE id = ? AND status = 'on' AND stock >= ? RETURNING title, price`,
		input.Quantity, input.ProductID, input.Quantity).Scan(&title, &price)
	if errors.Is(err, sql.ErrNoRows) {
		var status string
		err = tx.QueryRowContext(ctx, "SELECT status FROM products WHERE id = ?", input.ProductID).Scan(&status)
		if errors.Is(err, sql.ErrNoRows) {
			return problem(404, 40401, "商品不存在")
		}
		if err != nil {
			return err
		}
		if status == "off" {
			return problem(409, 40902, "商品已下架")
		}
		return problem(409, 40901, "库存不足")
	}
	if err != nil {
		return err
	}
	if price > math.MaxInt64/input.Quantity {
		return problem(400, 40001, "quantity 对应的订单金额超出范围")
	}
	result, err := scanOrder(tx.QueryRowContext(ctx, `INSERT INTO orders
		(order_no, product_id, product_title, quantity, unit_price, total_amount, buyer_name, buyer_phone, address)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING `+orderColumns,
		orderNo, input.ProductID, title, input.Quantity, price, price*input.Quantity,
		input.BuyerName, input.BuyerPhone, input.Address))
	if err != nil {
		return err
	}
	if err := tx.commit(); err != nil {
		return err
	}
	return respond(w, http.StatusCreated, result)
}

func (s *server) getOrder(w http.ResponseWriter, r *http.Request) error {
	o, err := scanOrder(s.db.QueryRowContext(r.Context(), "SELECT "+orderColumns+
		" FROM orders WHERE order_no = ?", r.PathValue("orderNo")))
	if errors.Is(err, sql.ErrNoRows) {
		return problem(404, 40401, "订单不存在")
	}
	if err != nil {
		return err
	}
	return respond(w, http.StatusOK, o)
}

func (s *server) listOrders(w http.ResponseWriter, r *http.Request) error {
	phone := r.URL.Query().Get("buyerPhone")
	if !validPhone(phone) {
		return problem(400, 40001, "buyerPhone 必须是 1 开头的 11 位数字")
	}
	page, size, offset, err := pagination(r)
	if err != nil {
		return err
	}
	ctx := r.Context()
	tx, err := s.begin(ctx)
	if err != nil {
		return err
	}
	defer tx.close()
	var total int
	if err := tx.QueryRowContext(ctx, "SELECT COUNT(*) FROM orders WHERE buyer_phone = ?", phone).Scan(&total); err != nil {
		return err
	}
	rows, err := tx.QueryContext(ctx, "SELECT "+orderColumns+` FROM orders
		WHERE buyer_phone = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?`, phone, size, offset)
	if err != nil {
		return err
	}
	defer rows.Close()
	items := make([]order, 0, size)
	for rows.Next() {
		o, err := scanOrder(rows)
		if err != nil {
			return err
		}
		items = append(items, o)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	if err := tx.commit(); err != nil {
		return err
	}
	return respond(w, http.StatusOK, map[string]any{"page": page, "pageSize": size, "total": total, "items": items})
}
