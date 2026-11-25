package main

import (
	"flag"
	"log"
	"net/http"
	"time"
)

func (s *server) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/health", handle(func(w http.ResponseWriter, r *http.Request) error {
		if err := s.db.PingContext(r.Context()); err != nil {
			return err
		}
		return respond(w, 200, nil)
	}))
	mux.HandleFunc("GET /api/products", handle(s.listProducts))
	mux.HandleFunc("POST /api/products", handle(s.createProduct))
	mux.HandleFunc("GET /api/products/{id}", handle(s.getProduct))
	mux.HandleFunc("PATCH /api/products/{id}/status", handle(s.changeStatus))
	mux.HandleFunc("/", handle(func(w http.ResponseWriter, r *http.Request) error { return problem(404, 40401, "接口不存在") }))
	return mux
}

func main() {
	addr := flag.String("addr", "127.0.0.1:8080", "HTTP 监听地址")
	path := flag.String("db", "market.db", "SQLite 数据库路径")
	flag.Parse()
	db, err := openStore(*path)
	if err != nil {
		log.Fatal(err)
	}
	defer db.Close()
	app := &server{db: db}
	srv := http.Server{Addr: *addr, Handler: app.routes(), ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 15 * time.Second, WriteTimeout: 30 * time.Second}
	log.Printf("校园好物市集 http://%s", *addr)
	log.Fatal(srv.ListenAndServe())
}
