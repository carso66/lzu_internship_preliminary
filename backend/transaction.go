package main

import (
	"context"
	"database/sql"
	"database/sql/driver"
	"errors"
)

// Keep the connection reserved until SQLite has finished the transaction.
type transaction struct {
	*sql.Tx
	conn *sql.Conn
}

func (s *server) begin(ctx context.Context) (*transaction, error) {
	conn, err := s.db.Conn(ctx)
	if err != nil {
		return nil, err
	}
	tx, err := conn.BeginTx(ctx, nil)
	if err != nil {
		conn.Close()
		return nil, err
	}
	return &transaction{Tx: tx, conn: conn}, nil
}

func (t *transaction) discardConnection() {
	_ = t.conn.Raw(func(any) error { return driver.ErrBadConn })
}

func (t *transaction) close() {
	if err := t.Tx.Rollback(); err != nil && !errors.Is(err, sql.ErrTxDone) {
		t.discardConnection()
	}
	_ = t.conn.Close()
}

func (t *transaction) commit() error {
	defer t.conn.Close()
	err := t.Tx.Commit()
	if err != nil {
		// database/sql considers the Tx done after a driver commit error, but
		// SQLite can leave it open (e.g. on a deferred foreign-key violation).
		// A canceled request must not cancel cleanup on this same connection.
		_ = t.Tx.Rollback()
		if _, rollbackErr := t.conn.ExecContext(context.Background(), "ROLLBACK"); rollbackErr != nil {
			t.discardConnection()
		}
	}
	return err
}
