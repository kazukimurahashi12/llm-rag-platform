package com.example.llmragplatform.service

import com.example.llmragplatform.config.RagProperties
import com.example.llmragplatform.domain.entity.KnowledgeDocument
import com.example.llmragplatform.domain.entity.KnowledgeDocumentAccessScope
import com.example.llmragplatform.domain.entity.KnowledgeDocumentChunk
import com.example.llmragplatform.exception.ResourceNotFoundException
import com.example.llmragplatform.generated.model.KnowledgeDocumentCreateRequest
import com.example.llmragplatform.generated.model.KnowledgeDocumentListResponse
import com.example.llmragplatform.generated.model.KnowledgeDocumentResponse
import com.example.llmragplatform.generated.model.KnowledgeReindexResponse
import com.example.llmragplatform.infrastructure.repository.KnowledgeDocumentChunkRepository
import com.example.llmragplatform.infrastructure.repository.KnowledgeDocumentRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class KnowledgeDocumentService(
    private val ragProperties: RagProperties,
    private val knowledgeDocumentRepository: KnowledgeDocumentRepository,
    private val knowledgeDocumentChunkRepository: KnowledgeDocumentChunkRepository,
    private val knowledgeChunkingService: KnowledgeChunkingService,
    private val knowledgeEmbeddingService: KnowledgeEmbeddingService,
    private val knowledgeAccessControlService: KnowledgeAccessControlService,
) {

    fun getDocuments(limit: Int, offset: Int): KnowledgeDocumentListResponse {
        // limit は 1 以上 100 以下に丸める。
        val safeLimit = limit.coerceIn(1, 100)
        // offset は 0 未満にならないよう補正する。
        val safeOffset = offset.coerceAtLeast(0)
        // 作成日時の新しい順で文書一覧を取得する。
        val allDocuments = knowledgeDocumentRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
            .filter { knowledgeAccessControlService.canAccess(it) }
        // offset と limit を適用し、API 応答モデルへ変換する。
        val items = allDocuments.drop(safeOffset).take(safeLimit).map(::toResponse)

        // 一覧 API 用のレスポンスを組み立てて返す。
        return KnowledgeDocumentListResponse()
            // 返却対象の文書一覧を設定する。
            .items(items)
            // 全件数を設定する。
            .totalCount(allDocuments.size.toLong())
            // 実際に適用した limit を設定する。
            .limit(safeLimit)
            // 実際に適用した offset を設定する。
            .offset(safeOffset)
    }

    @Transactional
    fun createDocument(request: KnowledgeDocumentCreateRequest): KnowledgeDocumentResponse {
        // まず文書本体を knowledge_documents へ保存する。
        val savedDocument = knowledgeDocumentRepository.save(
            // リクエスト内容から永続化用エンティティを作る。
            KnowledgeDocument(
                // 文書タイトルを保存する。
                title = request.title,
                // 文書本文を保存する。
                content = request.content,
                // 未指定時は既定値の SHARED を使う。
                accessScope = request.accessScope?.value?.let { KnowledgeDocumentAccessScope.valueOf(it) }
                    ?: KnowledgeDocumentAccessScope.SHARED,
                // 文書ごとに明示許可するユーザー名一覧を保存する。
                allowedUsernames = request.allowedUsernames?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
                    ?: emptySet()
            )
        )
        // 保存した文書本文を chunk 分割し、chunk エンティティ一覧へ変換する。
        val chunks = knowledgeChunkingService.chunk(savedDocument.content)
            .mapIndexed { index, chunk ->
                // 1 件分の chunk エンティティを作る。
                KnowledgeDocumentChunk(
                    // 親となる文書をひも付ける。
                    knowledgeDocument = savedDocument,
                    // chunk の順番を設定する。
                    chunkIndex = index,
                    // chunk 本文を設定する。
                    content = chunk
                )
            }
        // 作成した chunk 一覧をまとめて保存する。
        val savedChunks = knowledgeDocumentChunkRepository.saveAll(chunks)
        // vector 検索が有効なら、保存した chunk に embedding を付与する。
        knowledgeEmbeddingService.enrichChunks(savedChunks)

        // 保存した文書を API 応答モデルへ変換して返す。
        return toResponse(savedDocument)
    }

    fun reindexDocuments(): KnowledgeReindexResponse {
        // 既存の chunk を全件取得する。
        val allChunks = knowledgeDocumentChunkRepository.findAll()
        // 取得した chunk 全件に対して embedding を再生成する。
        val embeddingsUpdated = knowledgeEmbeddingService.enrichChunks(allChunks)

        // 全件再インデックス結果をレスポンスへ詰めて返す。
        return KnowledgeReindexResponse()
            // 対象文書数として文書総数を設定する。
            .documentsProcessed(knowledgeDocumentRepository.count())
            // 対象 chunk 数を設定する。
            .chunksProcessed(allChunks.size.toLong())
            // 実際に更新できた embedding 数を設定する。
            .embeddingsUpdated(embeddingsUpdated.toLong())
            // 実行時点で vector 検索が有効かどうかを返す。
            .vectorSearchEnabled(ragProperties.vectorSearchEnabled)
    }

    fun reindexDocument(documentId: Long): KnowledgeReindexResponse {
        // 指定 ID の文書を取得し、なければ 404 用例外を投げる。
        val document = knowledgeDocumentRepository.findById(documentId)
            .orElseThrow { ResourceNotFoundException("Knowledge document not found: $documentId") }
        // 対象文書にひもづく chunk を順番どおりに取得する。
        val chunks = knowledgeDocumentChunkRepository.findAllByKnowledgeDocumentOrderByChunkIndexAsc(document)
        // その文書の chunk だけに対して embedding を再生成する。
        val embeddingsUpdated = knowledgeEmbeddingService.enrichChunks(chunks)

        // 単一文書の再インデックス結果をレスポンスへ詰めて返す。
        return KnowledgeReindexResponse()
            // 処理対象文書数は 1 件固定。
            .documentsProcessed(1)
            // 対象 chunk 数を設定する。
            .chunksProcessed(chunks.size.toLong())
            // 実際に更新できた embedding 数を設定する。
            .embeddingsUpdated(embeddingsUpdated.toLong())
            // 実行時点で vector 検索が有効かどうかを返す。
            .vectorSearchEnabled(ragProperties.vectorSearchEnabled)
    }

    private fun toResponse(document: KnowledgeDocument): KnowledgeDocumentResponse {
        // 内部エンティティを API 応答モデルへ変換する。
        return KnowledgeDocumentResponse()
            // 文書 ID を設定する。
            .id(document.id)
            // 文書タイトルを設定する。
            .title(document.title)
            // 文書本文を設定する。
            .content(document.content)
            // 文書 ACL を設定する。
            .accessScope(KnowledgeDocumentResponse.AccessScopeEnum.fromValue(document.accessScope.name))
            // 明示許可ユーザー名一覧を設定する。
            .allowedUsernames(document.allowedUsernames.toList())
            // 作成日時を UTC の OffsetDateTime へ変換して設定する。
            .createdAt(OffsetDateTime.ofInstant(document.createdAt, ZoneOffset.UTC))
    }
}
