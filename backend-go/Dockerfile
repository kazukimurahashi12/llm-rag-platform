FROM golang:1.26 AS builder

WORKDIR /app

COPY go.mod go.mod
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o server ./cmd/server

FROM gcr.io/distroless/base-debian12

WORKDIR /app

COPY --from=builder /app/server /app/server
COPY --from=builder /app/prompts /app/prompts

EXPOSE 8081

ENTRYPOINT ["/app/server"]
