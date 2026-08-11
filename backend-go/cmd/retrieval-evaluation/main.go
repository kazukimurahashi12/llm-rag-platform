package main

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"time"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/db"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/evaluation"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/knowledge"
)

func main() {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	cfg := config.Load()
	cfg.RAG.VectorSearchEnabled = false

	postgres, err := db.NewPostgres(cfg.Database)
	if err != nil {
		fatal(err)
	}
	if err := postgres.Migrate(ctx); err != nil {
		fatal(fmt.Errorf("migrate database: %w", err))
	}

	if err := evaluation.SeedDefaultRetrievalKnowledge(ctx, postgres.SQLDB()); err != nil {
		fatal(fmt.Errorf("seed retrieval knowledge: %w", err))
	}

	repository := knowledge.NewRepository(postgres.SQLDB())
	retrievalService := knowledge.NewRetrievalService(repository, cfg.RAG, nil, knowledge.NewRetrievalMetrics())
	evaluationService := evaluation.NewRetrievalEvaluationService(retrievalService)

	result, err := evaluationService.EvaluateDefaultCases(ctx, nil)
	if err != nil {
		fatal(err)
	}

	encoder := json.NewEncoder(os.Stdout)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(result); err != nil {
		fatal(err)
	}
}

func fatal(err error) {
	fmt.Fprintln(os.Stderr, err.Error())
	os.Exit(1)
}
