package db

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
	_ "github.com/lib/pq"
)

// Postgres は backend-go の最小 PostgreSQL 接続を保持する。
type Postgres struct {
	sqlDB *sql.DB
}

// NewPostgres は PostgreSQL への接続ハンドルを生成する。
func NewPostgres(cfg config.DatabaseConfig) (*Postgres, error) {
	dsn := fmt.Sprintf(
		"host=%s port=%d dbname=%s user=%s password=%s sslmode=%s",
		cfg.Host,
		cfg.Port,
		cfg.Name,
		cfg.Username,
		cfg.Password,
		cfg.SSLMode,
	)

	sqlDB, err := sql.Open("postgres", dsn)
	if err != nil {
		return nil, err
	}

	sqlDB.SetMaxOpenConns(5)
	sqlDB.SetMaxIdleConns(5)
	sqlDB.SetConnMaxLifetime(30 * time.Minute)

	return &Postgres{sqlDB: sqlDB}, nil
}

// Ping は PostgreSQL への疎通を確認する。
func (p *Postgres) Ping(ctx context.Context) error {
	if p == nil || p.sqlDB == nil {
		return fmt.Errorf("database handle is not initialized")
	}

	return p.sqlDB.PingContext(ctx)
}

// SQLDB は repository 層向けに sql.DB を返す。
func (p *Postgres) SQLDB() *sql.DB {
	if p == nil {
		return nil
	}

	return p.sqlDB
}
