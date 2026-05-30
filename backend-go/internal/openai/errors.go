package openai

import "fmt"

// ErrorKind は OpenAI 呼び出し失敗の分類を表す。
type ErrorKind string

const (
	ErrorKindConfig    ErrorKind = "CONFIG"
	ErrorKindTimeout   ErrorKind = "TIMEOUT"
	ErrorKindTransport ErrorKind = "TRANSPORT"
	ErrorKindUpstream  ErrorKind = "UPSTREAM"
	ErrorKindDecode    ErrorKind = "DECODE"
	ErrorKindResponse  ErrorKind = "RESPONSE"
	ErrorKindCircuit   ErrorKind = "CIRCUIT_OPEN"
)

// Error は OpenAI 呼び出し失敗の詳細を保持する。
type Error struct {
	Kind       ErrorKind
	StatusCode int
	Message    string
	Cause      error
}

func (e *Error) Error() string {
	if e.StatusCode > 0 {
		return fmt.Sprintf("openai %s error (status=%d): %s", e.Kind, e.StatusCode, e.Message)
	}

	return fmt.Sprintf("openai %s error: %s", e.Kind, e.Message)
}

func (e *Error) Unwrap() error {
	return e.Cause
}
