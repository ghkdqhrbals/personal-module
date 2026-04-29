# MCP Server Feature Specification

## Overview

This document defines the MCP server merged into the existing guestbook FastAPI application.

- Application base: `etc/guestbook/app.py`
- Public MCP endpoint: `https://lowfidev.cloud/mcp/`
- Health endpoint: `https://lowfidev.cloud/health`
- Runtime: FastAPI + FastMCP mounted under `/mcp`

The server provides read-oriented access to the public blog at:

- `https://ghkdqhrbals.github.io/portfolios/`

It does not depend on a local checkout of the `portfolios` project for content resolution anymore. Recent posts, CV content, and individual article content are fetched from the live site.

## Goals

- Expose blog and CV information through MCP tools and resources
- Allow MCP-capable clients to search and retrieve recent content
- Support AI-assisted visitor Q&A grounded in the live public blog
- Reuse the existing guestbook deployment path instead of operating a separate MCP app

## Deployment Architecture

The MCP server is not deployed as a standalone service. It is mounted inside the guestbook application.

- FastAPI app: guestbook routes, health check, comment APIs
- FastMCP app: mounted at `/mcp`
- Lifespan startup initializes the MCP session manager
- Cloudflare Tunnel forwards `lowfidev.cloud` traffic to the guestbook app on port `8000`

## Endpoint Definition

### Health Check

- Method: `GET`
- Path: `/health`
- Expected response: `200 OK`

Purpose:

- Confirms the FastAPI app is running
- Used by deployment monitoring

### Visitor Q&A Streaming Endpoint

- Method: `POST`
- Path: `/ask/stream`
- Content-Type: `application/json`
- Response: `text/event-stream` (SSE)

Request body:

```json
{
  "question": "Redis Stream pending이 뭐야?",
  "page_url": "https://ghkdqhrbals.github.io/portfolios/docs/...",
  "page_title": "Redis Stream 모니터링"
}
```

SSE events:

- `answer_delta`: 모델 답변 토큰/문자열 증분
- `tool_call`: MCP tool 호출 정보 (`tool`, `arguments`)
- `done`: 최종 집계 결과 (`answer`, `sources`, `tool_calls`, `mode`)
- `error`: 처리 중 오류

Notes:

- `/ask/stream` 는 OpenAI Responses API의 streaming 응답을 프론트로 relay 한다.
- `tool_call` 이벤트는 모델이 실제 MCP tool 호출을 추가하는 시점에 전달된다.
- 스트리밍 모드는 remote MCP URL이 설정된 경우(`PUBLIC_MCP_SERVER_URL` 또는 `REMOTE_MCP_SERVER_URL`)에 동작한다.

### MCP Transport Endpoint

- Base path: `/mcp/`
- Transport: Streamable HTTP

Expected behavior:

- A plain browser or simple `curl` request is not a full MCP handshake
- Normal readiness behavior for plain `GET /mcp/` is a protocol-level error such as `406 Not Acceptable`
- `500`, `421`, `502`, `503`, `522` are considered abnormal

## MCP Handshake and Runtime Behavior

The mounted MCP server uses FastMCP streamable HTTP transport.

### Handshake model

1. Client connects to `https://lowfidev.cloud/mcp/`
2. Server creates or associates an MCP session
3. Client sends initialization and capability negotiation requests
4. Server exposes tools and resources
5. Client invokes tools or loads resources over the MCP protocol

### Notes

- Plain `curl` is useful only for endpoint liveness checks
- Actual tool usage requires an MCP client that speaks the protocol correctly
- Session management is initialized during FastAPI lifespan startup via `mcp.session_manager.run()`

## Transport Security

DNS rebinding protection is enabled through FastMCP transport security settings.

Allowed hosts:

- `lowfidev.cloud`
- `localhost:8000`
- `127.0.0.1:8000`

Allowed origins:

- `https://lowfidev.cloud`
- `http://localhost:8000`
- `http://127.0.0.1:8000`

Environment overrides:

- `MCP_ALLOWED_HOSTS`
- `MCP_ALLOWED_ORIGINS`

Values are comma-separated.

## Content Source Strategy

### Source of truth

The source of truth is the live GitHub Pages blog:

- `BLOG_SITE_BASE_URL`

Default:

- `https://ghkdqhrbals.github.io/portfolios`

### Recent posts

Recent posts are extracted from the homepage template payload embedded in the public site.

Behavior:

- Fetch homepage HTML
- Parse `<template id="recent-data">`
- Deserialize embedded JSON entries
- Normalize URL, title, date, parent, category
- Sort by descending date

### CV content

CV content is fetched from the public CV page:

- default `BLOG_CV_URL = https://ghkdqhrbals.github.io/portfolios/cv/`

Behavior:

- Fetch page HTML
- Extract main content area
- Return plain text/markdown-like content

### Individual post content

Behavior:

- Resolve absolute post URL
- Fetch public page HTML
- Extract page title and main content body
- Return a markdown-style response:

```md
# Title

---

Body...
```

## Tools

### `get_recent_posts`

Purpose:

- Returns recent post metadata from the public blog

Arguments:

- `limit: int = 10`
- `category: str = ""`

Behavior:

- Caps `limit` to `1..100`
- Filters by case-insensitive category if provided
- Returns JSON array

Response shape:

```json
[
  {
    "date": "2026-02-11",
    "title": "Coroutine and VirutalThread",
    "parent": "Java-Kotlin",
    "category": "Java-Kotlin",
    "url": "https://ghkdqhrbals.github.io/portfolios/docs/Java-Kotlin/51/"
  }
]
```

### `get_post_content`

Purpose:

- Returns the full content of a specific blog post

Arguments:

- `url_or_path: str`

Accepted forms:

- Absolute public post URL
- Relative path under the public blog

Behavior:

- Fetches live page HTML
- Extracts body content
- Returns markdown-style text

### `get_resume`

Purpose:

- Returns CV content from the public CV page

Arguments:

- none

Behavior:

- Fetches the live CV page
- Extracts the main content block

### `list_categories`

Purpose:

- Returns category counts derived from recent post metadata

Arguments:

- none

Behavior:

- Aggregates categories from the live post dataset
- Returns JSON dictionary sorted by descending count

### `answer_blog_visitor_question`

Purpose:

- Uses blog and CV content as grounding for visitor-facing answers

Arguments:

- `question: str`
- `page_url: str = ""`
- `page_title: str = ""`

Behavior:

- Selects relevant posts from live metadata
- Builds context from CV and matched post content
- Calls OpenAI with grounded context
- Returns answer plus sources

## Resources

### `blog://recent`

- Returns the recent-post listing

### `blog://resume`

- Returns CV content

### `blog://categories`

- Returns category counts

## Non-MCP Guestbook APIs

The application still serves the original guestbook APIs.

### `GET /guestbook`

- Lists threads and replies

### `POST /guestbook`

- Creates a comment

### `PUT /guestbook/{id}`

- Updates a comment if password matches

### `DELETE /guestbook/{id}`

- Deletes a comment and replies if password matches

These APIs are unrelated to MCP transport but share the same application and deployment unit.

## Environment Variables

### Required

- `OPENAI_API_KEY`

### Optional OpenAI

- `OPENAI_MODEL`
- `OPENAI_API_BASE`

### Optional blog content configuration

- `BLOG_SITE_BASE_URL`
- `BLOG_RECENT_POSTS_URL`
- `BLOG_CV_URL`

### Optional MCP transport security

- `MCP_ALLOWED_HOSTS`
- `MCP_ALLOWED_ORIGINS`

### Guestbook persistence

- `DATABASE_URL`
- `GUESTBOOK_DATA_DIR`

## Deployment Pipeline Expectations

Current CI/CD verifies:

1. `OPENAI_API_KEY` exists
2. guestbook data directory exists
3. container builds and starts
4. `/health` becomes ready
5. `/mcp` or `/mcp/` returns a non-5xx protocol response
6. failure path prints container status and logs

## Readiness Rules

### `/health`

- Must return `200`

### `/mcp` or `/mcp/`

For a plain HTTP probe:

- `406` is normal
- `405` may also be acceptable depending on client/request style
- Any `<500` MCP protocol response can be treated as endpoint liveness

Abnormal:

- `500`
- `421`
- `502`
- `503`
- `522`

## Validation Checklist

- `curl -i https://lowfidev.cloud/health`
- `curl -i https://lowfidev.cloud/mcp/`
- MCP client can connect to `https://lowfidev.cloud/mcp/`
- `get_recent_posts` returns live blog entries
- `get_resume` returns live CV content
- `get_post_content` returns body text for a real public article

## Known Constraints

- Read operations depend on the public GitHub Pages site being reachable
- HTML parsing is coupled to the current site structure
- Homepage recent-post parsing depends on the embedded template payload format
- Plain `curl` cannot fully validate tool execution; a real MCP client is required for end-to-end verification

## Future Improvements

- Add explicit tests for homepage metadata parsing
- Add a fallback parser if homepage template structure changes
- Cache fetched blog content to reduce repeated live fetch cost
- Add a small MCP smoke-test client in CI for protocol-level verification
