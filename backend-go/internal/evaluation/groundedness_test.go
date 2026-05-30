package evaluation

import (
	"testing"
)

func TestToRetrievedDocuments(t *testing.T) {
	docs := toRetrievedDocuments(nil)
	if len(docs) != 0 {
		t.Fatalf("expected empty documents, got %d", len(docs))
	}
}
