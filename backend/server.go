package main

import (
	"crypto/rand"
	"database/sql"
	_ "embed"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"math"
	"net/http"
	"strconv"
	"strings"
	"unicode/utf8"

	_ "modernc.org/sqlite"
)

//go:embed schema.sql
var schema string

type server struct{ db *sql.DB }

func openStore(dsn string) (*sql.DB, error) {
	db, err := sql.Open("sqlite", dsn+"?_pragma=foreign_keys(1)&_pragma=busy_timeout(5000)&_txlock=immediate")
	if err != nil {
		return nil, err
	}
	// A single connection queues local requests and also keeps :memory: databases alive.
	db.SetMaxOpenConns(1)
	if _, err = db.Exec(schema); err != nil {
		db.Close()
		return nil, fmt.Errorf("初始化数据库: %w", err)
	}
	return db, nil
}

type apiError struct {
	status, code int
	message      string
}

func (e *apiError) Error() string                    { return e.message }
func problem(status, code int, message string) error { return &apiError{status, code, message} }

type endpoint func(http.ResponseWriter, *http.Request) error

func handle(fn endpoint) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if err := fn(w, r); err != nil {
			var api *apiError
			if !errors.As(err, &api) {
				log.Printf("%s: %v", r.Pattern, err)
				api = &apiError{500, 50000, "服务暂时不可用"}
			}
			writeJSON(w, api.status, api.code, api.message, nil)
		}
	}
}
func writeJSON(w http.ResponseWriter, status, code int, msg string, data any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(map[string]any{"code": code, "message": msg, "data": data}); err != nil {
		log.Printf("写入响应失败: %v", err)
	}
}
func respond(w http.ResponseWriter, status int, data any) error {
	writeJSON(w, status, 0, "ok", data)
	return nil
}
func readJSON(w http.ResponseWriter, r *http.Request, dst any) error {
	d := json.NewDecoder(http.MaxBytesReader(w, r.Body, 64<<10))
	d.DisallowUnknownFields()
	if err := d.Decode(dst); err != nil {
		var t *json.UnmarshalTypeError
		if errors.As(err, &t) && t.Field != "" {
			return problem(400, 40001, t.Field+" 类型错误")
		}
		if strings.HasPrefix(err.Error(), "json: unknown field ") {
			return problem(400, 40001, "未支持的字段 "+strings.TrimPrefix(err.Error(), "json: unknown field "))
		}
		return problem(400, 40001, "请求体必须是单个 JSON 对象，大小上限为 64 KiB")
	}
	if err := d.Decode(new(any)); err != io.EOF {
		return problem(400, 40001, "请求体只能包含一个 JSON 对象")
	}
	return nil
}

func textField(value, name string, min, max int) (string, error) {
	value = strings.TrimSpace(value)
	n := utf8.RuneCountInString(value)
	if n < min || n > max {
		return "", problem(400, 40001, fmt.Sprintf("%s 长度应为 %d 至 %d 个字符", name, min, max))
	}
	return value, nil
}
func pagination(r *http.Request) (page, size, offset int, err error) {
	q := r.URL.Query()
	page, size = 1, 10
	if q.Has("page") {
		page, err = strconv.Atoi(q.Get("page"))
		if err != nil || page < 1 {
			return 0, 0, 0, problem(400, 40001, "page 必须是正整数")
		}
	}
	if q.Has("pageSize") {
		size, err = strconv.Atoi(q.Get("pageSize"))
		if err != nil || size < 1 || size > 50 {
			return 0, 0, 0, problem(400, 40001, "pageSize 范围为 1 至 50")
		}
	}
	if page-1 > math.MaxInt/size {
		return 0, 0, 0, problem(400, 40001, "page 超出范围")
	}
	return page, size, (page - 1) * size, nil
}
func reference(prefix string) (string, error) {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", err
	}
	return prefix + hex.EncodeToString(b[:]), nil
}
