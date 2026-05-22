# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
./mvnw clean package -DskipTests   # Build
./mvnw spring-boot:run             # Run dev server (port 8080)
./mvnw test                        # Unit tests
./mvnw verify                      # Integration tests
```

Requires Java 21, PostgreSQL 15+ with pgvector extension, and Maven 3.6+.

API docs at http://localhost:8080/swagger-ui.html after startup.

## Architecture

Spring Boot 3.2.5 monolith with a standard layered structure: `controller → service → mapper`. MyBatis-Plus 3.5.7 is the ORM (not JPA/Hibernate). All entity IDs are UUIDs (`IdType.ASSIGN_UUID`). Logical delete via `deleted` column.

**LLM stack:** Spring AI 1.0.0 calling DeepSeek (`deepseek-chat`) via OpenAI-compatible protocol for chat, and Alibaba DashScope `text-embedding-v4` (1536-dim) for embeddings via a custom `EmbeddingModel` bean in `DashScopeEmbeddingConfig`.

**Auth:** Stateless JWT. `JwtAuthenticationFilter` (a `OncePerRequestFilter`) extracts the Bearer token, validates it, and sets the Spring Security context. `BaseController` exposes `getCurrentUserId()` — all controllers extend it. Endpoint authorization is declared in `SecurityConfig`; method-level `@PreAuthorize` is also used.

**API response format:** All endpoints return `ApiResponse<T>` (wraps `success`, `message`, `data`). Constructed via `BaseController.success(data)`.

## Key Flows

### Document Ingestion (RAG pipeline)

1. `DocumentService.uploadDocument()` — saves file to `./uploads/documents/`, inserts `Document` row (status=`UPLOADING`)
2. `DocumentProcessService.processDocument()` — **runs async** on `documentProcessExecutor` thread pool (core=2, max=4)
3. Status state machine: `UPLOADING → PROCESSING → INDEXED` (success) or `FAILED`
4. Parsing via `DocumentParserService`: Apache Tika for complex formats, direct read for text files, ZIP fallback for archives (EPUB/DOCX/XLSX)
5. Chunking: `markdownSectionChunk()` for `.md` files (preserves frontmatter + `##` sections), `smartChunkContent()` for everything else (paragraph-based, 50–500 chars per chunk)
6. `VectorService.embed()` generates 1536-dim embedding, `saveDocumentChunk()` writes chunk + vector to pgvector
7. Knowledge base format restriction: if KB config has `"docType":"CAR_MD"`, only `.md` files are accepted

### QA Flow

`QAService.askQuestion()` persists a `Question` entity, then delegates to `AgentService.answer()` which has a three-stage pipeline:

1. **Tools** — `callToolsIfNeeded()` regex-matches car price/monthly-payment queries and calls `CarPriceCalculator` (hardcoded price DB, annotated with `@Tool` for Spring AI)
2. **Skills** — `SkillClassifier` iterates `List<AgentSkill>` beans (`GreetingSkill`, `ContactSkill`, `ActivitySkill`). Each implements `canHandle()` + `execute()`. First match wins.
3. **RAG** — embed question → cosine similarity search in pgvector (top 5 chunks, filtered by knowledgeBaseId via JOIN on `documents`) → build prompt with chat history from current session → call DeepSeek via `ChatModel`

System prompt is selected per knowledge base: `CAR_SALES_SYSTEM_PROMPT` for car MD KBs, `DEFAULT_SYSTEM_PROMPT` otherwise. Falls back to a static answer if the LLM call fails.

### Vector Search

All queries go through `DocumentChunkVectorMapper.xml` which uses pgvector operators:
- `<=>` cosine distance (used by default in `searchByCosineSimilarity`)
- `<->` L2 distance
- `<#>` negative inner product

Each query JOINs `document_chunk` with `documents` to optionally filter by `knowledge_base_id`.

## Database

PostgreSQL with `pgvector` extension. Two key tables for RAG:
- `document_chunk` — chunk metadata (id, document_id, chunk_index, content)
- `document_chunk_vector` — the actual vector column (`embedding vector(1536)`)

Schema init scripts: `init.sql` (standard) and `init-mp.sql` (MyBatis-Plus compatible).

## Config Notes

- `application.yml` wires DeepSeek via `spring.ai.openai.*` (base-url points to `api.deepseek.com`) and DashScope via custom `dashscope.*` prefix
- Embedding dimension must be 1536 — hardcoded as `VECTOR_DIMENSION` in `VectorService` and matched in `DashScopeEmbeddingConfig`
- MyBatis-Plus maps underscore DB columns to camelCase Java fields automatically
- Multipart upload max: 50MB
