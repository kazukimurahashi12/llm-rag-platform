package knowledge

import (
	"strings"
	"testing"
)

func TestChunkContentSplitsLongContentWithOverlap(t *testing.T) {
	content := strings.Builder{}
	for i := 0; i < 220; i++ {
		content.WriteString(string(rune('0' + i%10)))
	}

	chunks := chunkContent(content.String(), 100, 20)

	if len(chunks) != 3 {
		t.Fatalf("len(chunks) = %d, want 3", len(chunks))
	}
	if len([]rune(chunks[0])) != 100 {
		t.Fatalf("len(chunks[0]) = %d, want 100", len([]rune(chunks[0])))
	}
	if len([]rune(chunks[1])) != 100 {
		t.Fatalf("len(chunks[1]) = %d, want 100", len([]rune(chunks[1])))
	}
	if len([]rune(chunks[2])) != 60 {
		t.Fatalf("len(chunks[2]) = %d, want 60", len([]rune(chunks[2])))
	}
}

func TestChunkContentNormalizesWhitespace(t *testing.T) {
	chunks := chunkContent("  hello\n\nworld\tfrom   go  ", 100, 20)

	if len(chunks) != 1 {
		t.Fatalf("len(chunks) = %d, want 1", len(chunks))
	}
	if chunks[0] != "hello world from go" {
		t.Fatalf("chunks[0] = %q, want normalized text", chunks[0])
	}
}

func TestChunkContentReturnsEmptyForBlankContent(t *testing.T) {
	chunks := chunkContent(" \n\t ", 100, 20)

	if len(chunks) != 0 {
		t.Fatalf("len(chunks) = %d, want 0", len(chunks))
	}
}
