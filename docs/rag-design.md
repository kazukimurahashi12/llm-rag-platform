# RAG Design

## Retrieval Strategy

- 標準設定:
  - `vector-search-enabled=true`
  - `top-k=3`
  - `min-similarity-score=0.4`
  - `rerank-enabled=false`
- vector 検索を先に試し、条件を満たさない場合は keyword 検索へ fallback します
- ACE 分析結果を使って同カテゴリ文書へ軽い優先度 boost をかけます

## Knowledge Structure

- `knowledge_documents`
  - title
  - content
  - ace_category
  - access_scope
- `knowledge_document_chunks`
  - knowledge_document_id
  - chunk_index
  - content
  - embedding

## Retrieval Quality Evaluation

標準評価ケースは `src/main/resources/evaluation/retrieval-cases.json` に置きます。

評価指標:

- Hit Rate
- MRR
- Recall@K
- Precision@K

比較対象:

- `topK`
- `minSimilarityScore`
- `rerankEnabled`

## Hallucination Suppression

ハルシネーション抑制は 2 段です。

1. retrieval で根拠を取得し prompt に埋め込む
2. 生成後に groundedness を採点し、低信頼なら fallback 応答へ切り替える

これにより、根拠不足のまま断定的な advice を返し続ける挙動を抑えます。
