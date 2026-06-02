package http

import (
	"errors"
	"net/http"
	"strconv"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/knowledge"
	"github.com/labstack/echo/v4"
)

// RegisterReindexRoutes は knowledge reindex job API を登録する。
func RegisterReindexRoutes(
	e *echo.Echo,
	tokenService jwtClaimsParser,
	reindexJobService *knowledge.ReindexJobService,
) {
	reindexGroup := e.Group("/v1/knowledge-documents")
	reindexGroup.Use(jwtMiddleware(tokenService))
	reindexGroup.Use(adminMiddleware())

	reindexGroup.POST("/reindex", func(c echo.Context) error {
		response, err := reindexJobService.SubmitAllDocumentsJob(c.Request().Context())
		if err != nil {
			return writeKnowledgeJobError(c, "failed to submit all-documents reindex job", err)
		}
		return c.JSON(http.StatusAccepted, response)
	})

	reindexGroup.POST("/:knowledgeDocumentId/reindex", func(c echo.Context) error {
		documentID, err := strconv.ParseInt(c.Param("knowledgeDocumentId"), 10, 64)
		if err != nil {
			return writeError(c, http.StatusBadRequest, "invalid knowledgeDocumentId", []string{err.Error()})
		}
		response, err := reindexJobService.SubmitSingleDocumentJob(c.Request().Context(), documentID)
		if err != nil {
			return writeKnowledgeJobError(c, "failed to submit single-document reindex job", err)
		}
		return c.JSON(http.StatusAccepted, response)
	})

	jobGroup := e.Group("/v1/knowledge-documents/reindex-jobs")
	jobGroup.Use(jwtMiddleware(tokenService))
	jobGroup.Use(adminMiddleware())

	jobGroup.GET("", func(c echo.Context) error {
		limit, _ := strconv.Atoi(c.QueryParam("limit"))
		offset, _ := strconv.Atoi(c.QueryParam("offset"))
		return c.JSON(http.StatusOK, reindexJobService.ListJobs(limit, offset))
	})

	jobGroup.GET("/:jobId", func(c echo.Context) error {
		response, err := reindexJobService.GetJob(c.Param("jobId"))
		if err != nil {
			return writeKnowledgeJobError(c, "failed to get reindex job", err)
		}
		return c.JSON(http.StatusOK, response)
	})

	jobGroup.POST("/:jobId/retry", func(c echo.Context) error {
		response, err := reindexJobService.RetryJob(c.Request().Context(), c.Param("jobId"))
		if err != nil {
			return writeKnowledgeJobError(c, "failed to retry reindex job", err)
		}
		return c.JSON(http.StatusAccepted, response)
	})

	jobGroup.DELETE("/:jobId", func(c echo.Context) error {
		if err := reindexJobService.DeleteJob(c.Param("jobId")); err != nil {
			return writeKnowledgeJobError(c, "failed to delete reindex job", err)
		}
		return c.NoContent(http.StatusNoContent)
	})
}

func writeKnowledgeJobError(c echo.Context, message string, err error) error {
	status := http.StatusInternalServerError
	switch {
	case errors.Is(err, knowledge.ErrKnowledgeDocumentNotFound), errors.Is(err, knowledge.ErrKnowledgeReindexJobNotFound):
		status = http.StatusNotFound
	default:
		errorMessage := err.Error()
		if errorMessage == "knowledge reindex job cannot be deleted while active" ||
			errorMessage == "only failed reindex jobs can be retried" {
			status = http.StatusBadRequest
		}
	}
	return writeError(c, status, message, []string{err.Error()})
}
