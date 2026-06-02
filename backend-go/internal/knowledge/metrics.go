package knowledge

import "sync/atomic"

type RetrievalMetricsSnapshot struct {
	VectorAcceptedRetrievals      float64
	VectorThresholdFallbacks      float64
	VectorThresholdFilteredChunks float64
}

// RetrievalMetrics は retrieval の vector 採用状況と threshold 関連件数を保持する。
type RetrievalMetrics struct {
	vectorAcceptedRetrievals      atomic.Int64
	vectorThresholdFallbacks      atomic.Int64
	vectorThresholdFilteredChunks atomic.Int64
}

func NewRetrievalMetrics() *RetrievalMetrics {
	return &RetrievalMetrics{}
}

func (m *RetrievalMetrics) RecordVectorAccepted() {
	m.vectorAcceptedRetrievals.Add(1)
}

func (m *RetrievalMetrics) RecordThresholdFallback() {
	m.vectorThresholdFallbacks.Add(1)
}

func (m *RetrievalMetrics) RecordThresholdFiltered(count int) {
	if count <= 0 {
		return
	}
	m.vectorThresholdFilteredChunks.Add(int64(count))
}

func (m *RetrievalMetrics) Snapshot() RetrievalMetricsSnapshot {
	return RetrievalMetricsSnapshot{
		VectorAcceptedRetrievals:      float64(m.vectorAcceptedRetrievals.Load()),
		VectorThresholdFallbacks:      float64(m.vectorThresholdFallbacks.Load()),
		VectorThresholdFilteredChunks: float64(m.vectorThresholdFilteredChunks.Load()),
	}
}
