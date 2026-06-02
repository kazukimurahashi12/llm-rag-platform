package evaluation

import "testing"

func TestRetrievalNormalize(t *testing.T) {
	if normalize("  AbC  ") != "abc" {
		t.Fatalf("normalize did not trim/lowercase as expected")
	}
}
