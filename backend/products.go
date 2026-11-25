package main

import (
	"database/sql"
	"errors"
	"math"
	"net/http"
	"strconv"
	"strings"
)

type product struct {
	ID          int64  `json:"id"`
	Title       string `json:"title"`
	Price       int64  `json:"price"`
	Stock       int64  `json:"stock"`
	Category    string `json:"category"`
	Description string `json:"description"`
	Status      string `json:"status"`
	SellerName  string `json:"sellerName"`
	CreatedAt   string `json:"createdAt"`
}

const productColumns = "id,title,price,stock,category,description,status,seller_name,created_at"

type rowScanner interface{ Scan(...any) error }

func scanProduct(row rowScanner) (product, error) {
	var p product
	err := row.Scan(&p.ID, &p.Title, &p.Price, &p.Stock, &p.Category, &p.Description, &p.Status, &p.SellerName, &p.CreatedAt)
	return p, err
}

func (s *server) createProduct(w http.ResponseWriter, r *http.Request) error {
	var body struct {
		Title       string `json:"title"`
		Price       *int64 `json:"price"`
		Stock       *int64 `json:"stock"`
		Category    string `json:"category"`
		Description string `json:"description"`
		SellerName  string `json:"sellerName"`
	}
	if err := readJSON(w, r, &body); err != nil {
		return err
	}
	title, err := textField(body.Title, "title", 1, 100)
	if err != nil {
		return err
	}
	category, err := textField(body.Category, "category", 1, 40)
	if err != nil {
		return err
	}
	seller, err := textField(body.SellerName, "sellerName", 1, 60)
	if err != nil {
		return err
	}
	description, err := textField(body.Description, "description", 0, 1000)
	if err != nil {
		return err
	}
	// These bounds keep every accepted unit price × purchasable quantity inside int64.
	if body.Price == nil || *body.Price < 1 || *body.Price > math.MaxInt32 {
		return problem(400, 40001, "price 必须是 1 至 2147483647 之间的整数（分）")
	}
	if body.Stock == nil || *body.Stock < 0 || *body.Stock > math.MaxInt32 {
		return problem(400, 40001, "stock 必须是 0 至 2147483647 之间的整数")
	}
	row := s.db.QueryRowContext(r.Context(), `INSERT INTO products(title,price,stock,category,description,seller_name)
        VALUES(?,?,?,?,?,?) RETURNING `+productColumns, title, *body.Price, *body.Stock, category, description, seller)
	p, err := scanProduct(row)
	if err != nil {
		return err
	}
	return respond(w, 201, p)
}

func productID(r *http.Request) (int64, error) {
	id, err := strconv.ParseInt(r.PathValue("id"), 10, 64)
	if err != nil || id <= 0 {
		return 0, problem(400, 40001, "商品 id 必须是正整数")
	}
	return id, nil
}
func (s *server) getProduct(w http.ResponseWriter, r *http.Request) error {
	id, err := productID(r)
	if err != nil {
		return err
	}
	p, err := scanProduct(s.db.QueryRowContext(r.Context(), "SELECT "+productColumns+" FROM products WHERE id=?", id))
	if errors.Is(err, sql.ErrNoRows) {
		return problem(404, 40401, "商品不存在")
	}
	if err != nil {
		return err
	}
	return respond(w, 200, p)
}
func (s *server) changeStatus(w http.ResponseWriter, r *http.Request) error {
	id, err := productID(r)
	if err != nil {
		return err
	}
	var body struct {
		Status string `json:"status"`
	}
	if err = readJSON(w, r, &body); err != nil {
		return err
	}
	if body.Status != "on" && body.Status != "off" {
		return problem(400, 40001, "status 只能为 on 或 off")
	}
	result, err := s.db.ExecContext(r.Context(), "UPDATE products SET status=? WHERE id=?", body.Status, id)
	if err != nil {
		return err
	}
	n, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if n == 0 {
		return problem(404, 40401, "商品不存在")
	}
	return respond(w, 200, map[string]any{"id": id, "status": body.Status})
}
func (s *server) listProducts(w http.ResponseWriter, r *http.Request) error {
	page, size, offset, err := pagination(r)
	if err != nil {
		return err
	}
	q := r.URL.Query()
	var conditions []string
	var args []any
	if q.Has("status") {
		status := q.Get("status")
		if status != "on" && status != "off" {
			return problem(400, 40001, "status 只能为 on 或 off")
		}
		conditions = append(conditions, "status=?")
		args = append(args, status)
	}
	if category := q.Get("category"); category != "" {
		conditions = append(conditions, "category=?")
		args = append(args, category)
	}
	if keyword := q.Get("keyword"); keyword != "" {
		conditions = append(conditions, "instr(lower(title), lower(?)) > 0")
		args = append(args, keyword)
	}
	from := " FROM products"
	if len(conditions) > 0 {
		from += " WHERE " + strings.Join(conditions, " AND ")
	}
	ctx := r.Context()
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var total int
	if err = tx.QueryRowContext(ctx, "SELECT count(*)"+from, args...).Scan(&total); err != nil {
		return err
	}
	rows, err := tx.QueryContext(ctx, "SELECT "+productColumns+from+" ORDER BY id DESC LIMIT ? OFFSET ?", append(args, size, offset)...)
	if err != nil {
		return err
	}
	defer rows.Close()
	items := make([]product, 0, size)
	for rows.Next() {
		item, err := scanProduct(rows)
		if err != nil {
			return err
		}
		items = append(items, item)
	}
	if err = rows.Err(); err != nil {
		return err
	}
	if err = tx.Commit(); err != nil {
		return err
	}
	return respond(w, 200, map[string]any{"page": page, "pageSize": size, "total": total, "items": items})
}
