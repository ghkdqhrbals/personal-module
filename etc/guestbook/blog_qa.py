from __future__ import annotations

import html
import json
import os
import re
import uuid
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime
from html.parser import HTMLParser
from typing import Any
from urllib.parse import quote, urljoin, urlparse

SITE_BASE_URL = os.getenv("BLOG_SITE_BASE_URL", "https://ghkdqhrbals.github.io/portfolios").rstrip("/")
RECENT_POSTS_URL = os.getenv("BLOG_RECENT_POSTS_URL", SITE_BASE_URL + "/")
CV_URL = os.getenv("BLOG_CV_URL", SITE_BASE_URL + "/cv/")
SITE_ORIGIN = f"{urlparse(SITE_BASE_URL).scheme}://{urlparse(SITE_BASE_URL).netloc}"
REMOTE_MCP_ALLOWED_TOOLS = [
    "get_recent_posts",
    "search_posts",
    "get_post_content",
    "get_resume",
    "list_categories",
]


@dataclass
class PostRecord:
    date: str
    date_obj: datetime
    title: str
    parent: str
    category: str
    url: str
    file_path: str
    search_text: str
    content: str


class ContentExtractor(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.capture_depth = 0
        self.skip_depth = 0
        self.chunks: list[str] = []
        self.title: str = ""

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attrs_dict = dict(attrs)
        if tag in {"script", "style", "noscript"}:
            self.skip_depth += 1
            return

        if (tag == "main" and attrs_dict.get("id") == "main-content") or (
            tag == "div" and attrs_dict.get("id") == "main-content"
        ):
            self.capture_depth += 1
            return

        if self.capture_depth > 0:
            if tag in {"main", "div", "section", "article", "p", "li", "h1", "h2", "h3", "h4", "blockquote", "pre", "br", "hr"}:
                self.chunks.append("\n")
            if tag == "a":
                href = attrs_dict.get("href")
                if href:
                    self.chunks.append("")

    def handle_endtag(self, tag: str) -> None:
        if tag in {"script", "style", "noscript"} and self.skip_depth > 0:
            self.skip_depth -= 1
            return

        if tag in {"main", "div"} and self.capture_depth > 0:
            self.capture_depth -= 1
            self.chunks.append("\n")
            return

        if self.capture_depth > 0 and tag in {"div", "section", "article", "p", "li", "h1", "h2", "h3", "h4", "blockquote", "pre"}:
            self.chunks.append("\n")

    def handle_data(self, data: str) -> None:
        if self.skip_depth > 0:
            return
        if self.capture_depth > 0:
            stripped = data.strip()
            if stripped:
                self.chunks.append(stripped)

    def text(self) -> str:
        text = "\n".join(chunk for chunk in self.chunks if chunk is not None)
        text = html.unescape(text)
        text = re.sub(r"\n{3,}", "\n\n", text)
        text = re.sub(r"[ \t]+\n", "\n", text)
        text = re.sub(r"\n[ \t]+", "\n", text)
        return text.strip()


def _normalize_text(value: str) -> str:
    return re.sub(r"\s+", " ", (value or "").strip()).lower()


TECH_TERM_ALIASES: tuple[tuple[str, ...], ...] = (
    ("coroutine", "coroutines", "코루틴"),
    ("virtualthread", "virtual", "가상스레드", "가상"),
    ("thread", "threads", "스레드"),
)


def _split_camel_case(value: str) -> str:
    return re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", value or "")


def _expand_alias_tokens(tokens: set[str], normalized_text: str) -> set[str]:
    expanded = set(tokens)
    for aliases in TECH_TERM_ALIASES:
        normalized_aliases = {_normalize_text(alias) for alias in aliases}
        alias_tokens = set().union(*(_tokenize_plain(alias) for alias in normalized_aliases))
        if tokens & alias_tokens or any(alias in normalized_text for alias in normalized_aliases):
            expanded.update(alias_tokens)
    return expanded


def _tokenize_plain(value: str) -> set[str]:
    text = _normalize_text(_split_camel_case(value))
    return {token for token in re.split(r"[^0-9a-zA-Z가-힣]+", text) if len(token) >= 2}


def _tokenize(value: str) -> set[str]:
    normalized_text = _normalize_text(value)
    return _expand_alias_tokens(_tokenize_plain(value), normalized_text)


def _fetch_text(url: str) -> str:
    req = urllib.request.Request(
        url=url,
        headers={
            "User-Agent": "guestbook-mcp/1.0",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as response:
        return response.read().decode("utf-8", errors="replace")


def _absolute_url(value: str) -> str:
    if value.startswith("http://") or value.startswith("https://"):
        return _canonicalize_url(value)
    if value.startswith("/"):
        return _canonicalize_url(urljoin(SITE_ORIGIN, value))
    return _canonicalize_url(urljoin(SITE_BASE_URL + "/", value.lstrip("/")))


def _canonicalize_url(url: str) -> str:
    parsed = urlparse(url.strip())
    if not parsed.scheme or not parsed.netloc:
        return url.strip()

    path = parsed.path or ""
    if path not in {"", "/"}:
        path = path.rstrip("/")
    path = quote(path, safe="/-._~")

    normalized = parsed._replace(path=path)
    return normalized.geturl()


def _extract_search_data(html_text: str) -> list[dict[str, Any]]:
    match = re.search(r"<template[^>]*>\s*(\[\s*\{.*?\}\s*\])\s*</template>", html_text, re.DOTALL)
    if not match:
        raise RuntimeError("최근 포스트 메타데이터를 홈페이지에서 찾지 못했습니다.")
    return json.loads(match.group(1))


def _extract_page_text(html_text: str) -> str:
    parser = ContentExtractor()
    parser.feed(html_text)
    parser.close()
    return parser.text()


def _extract_page_title(html_text: str) -> str:
    match = re.search(r"<title>(.*?)</title>", html_text, re.DOTALL | re.IGNORECASE)
    if not match:
        return ""
    title = html.unescape(match.group(1))
    return title.split("|")[0].strip()


def _parse_date(value: str) -> datetime | None:
    if not value:
        return None
    value = value.strip()
    for fmt in ("%Y-%m-%d", "%Y.%m.%d"):
        try:
            return datetime.strptime(value[:10], fmt)
        except ValueError:
            continue
    return None


def _post_from_search_entry(entry: dict[str, Any]) -> PostRecord | None:
    raw_url = str(entry.get("url") or "").strip()
    title = str(entry.get("title") or "").strip()
    if not raw_url or not title:
        return None

    url = _absolute_url(raw_url)
    date_str = str(entry.get("date") or "").strip()
    date_obj = _parse_date(date_str)
    if date_obj is None:
        return None

    parent = str(entry.get("parent") or "").strip()
    category = str(entry.get("cat") or "").strip()
    return PostRecord(
        date=date_str,
        date_obj=date_obj,
        title=title,
        parent=parent,
        category=category,
        url=url,
        file_path=urlparse(url).path,
        search_text=_normalize_text(
            " ".join(
                [
                    title,
                    parent,
                    category,
                    raw_url,
                    str(entry.get("content") or ""),
                    str(entry.get("description") or ""),
                ]
            )
        ),
        content="",
    )


def collect_posts() -> list[PostRecord]:
    html_text = _fetch_text(RECENT_POSTS_URL)
    search_entries = _extract_search_data(html_text)
    posts = [post for post in (_post_from_search_entry(entry) for entry in search_entries) if post is not None]
    posts.sort(key=lambda item: item.date_obj, reverse=True)
    return posts


def post_summary(post: PostRecord) -> dict[str, str]:
    return {
        "date": post.date,
        "title": post.title,
        "parent": post.parent,
        "category": post.category,
        "url": post.url,
    }


def _score_search_match(query_tokens: set[str], normalized_query: str, post: PostRecord) -> int:
    haystack_tokens = _tokenize(post.search_text)
    title_tokens = _tokenize(post.title)
    category_tokens = _tokenize(post.category)
    score = 0

    score += len(query_tokens & haystack_tokens) * 3
    score += len(query_tokens & title_tokens) * 6
    score += len(query_tokens & category_tokens) * 4

    normalized_title = _normalize_text(post.title)
    normalized_category = _normalize_text(post.category)
    normalized_parent = _normalize_text(post.parent)
    normalized_path = _normalize_text(post.file_path)

    if normalized_query and normalized_query in normalized_title:
        score += 12
    if normalized_query and normalized_query in normalized_category:
        score += 8
    if normalized_query and normalized_query in normalized_parent:
        score += 5
    if normalized_query and normalized_query in normalized_path:
        score += 4

    return score


def search_posts(query: str, limit: int = 10) -> list[PostRecord]:
    normalized_query = _normalize_text(query)
    if not normalized_query:
        return []

    query_tokens = _tokenize(normalized_query)
    if not query_tokens and normalized_query:
        query_tokens = {normalized_query}

    ranked: list[tuple[int, PostRecord]] = []
    for post in collect_posts():
        score = _score_search_match(query_tokens, normalized_query, post)
        if score <= 0:
            continue
        ranked.append((score, post))

    ranked.sort(key=lambda item: (item[0], item[1].date_obj), reverse=True)
    bounded_limit = max(1, min(limit, 50))
    return [post for _, post in ranked[:bounded_limit]]


def read_resume() -> str:
    html_text = _fetch_text(CV_URL)
    return _extract_page_text(html_text)


def get_post_content(url_or_path: str) -> str:
    url = _absolute_url(url_or_path)
    html_text = _fetch_text(url)
    title = _extract_page_title(html_text) or url.rstrip("/").split("/")[-1]
    body = _extract_page_text(html_text)
    if not body:
        return f"오류: '{url}' 페이지 본문을 읽지 못했습니다."
    return f"# {title}\n\n---\n\n{body}"


def list_categories_with_counts() -> dict[str, int]:
    counts: dict[str, int] = {}
    for post in collect_posts():
        category = post.category or "기타"
        counts[category] = counts.get(category, 0) + 1
    return dict(sorted(counts.items(), key=lambda item: item[1], reverse=True))


def _score_post(question_tokens: set[str], page_url: str, page_title: str, post: PostRecord) -> int:
    score = 0
    title_tokens = _tokenize(post.title)
    parent_tokens = _tokenize(post.parent)
    category_tokens = _tokenize(post.category)
    haystack_tokens = title_tokens | parent_tokens | category_tokens

    score += len(question_tokens & haystack_tokens) * 4
    score += len(question_tokens & title_tokens) * 5

    if page_title and _normalize_text(page_title) in _normalize_text(post.title):
        score += 8
    if page_url and post.url.rstrip("/") == page_url.rstrip("/"):
        score += 20

    return score


def select_relevant_posts(question: str, page_url: str = "", page_title: str = "", limit: int = 4) -> list[PostRecord]:
    tokens = _tokenize(question + " " + page_title)
    ranked: list[tuple[int, PostRecord]] = []

    for post in collect_posts():
        score = _score_post(tokens, page_url, page_title, post)
        if score <= 0:
            continue
        ranked.append((score, post))

    ranked.sort(key=lambda item: (item[0], item[1].date_obj), reverse=True)
    return [post for _, post in ranked[:limit]]


def build_context(question: str, page_url: str = "", page_title: str = "") -> tuple[str, list[dict[str, str]]]:
    sources: list[dict[str, str]] = []
    chunks: list[str] = []

    resume = read_resume()
    if resume:
        chunks.append("## Resume\n" + resume[:4000].strip())
        sources.append({"type": "resume", "title": "CV", "url": CV_URL})

    relevant_posts = select_relevant_posts(question, page_url=page_url, page_title=page_title)
    for post in relevant_posts:
        content = get_post_content(post.url)
        excerpt = content.strip()[:3500]
        chunks.append(
            "\n".join(
                [
                    f"## Post: {post.title}",
                    f"Date: {post.date}",
                    f"Category: {post.category or post.parent or '기타'}",
                    f"URL: {post.url}",
                    excerpt,
                ]
            )
        )
        sources.append(
            {
                "type": "post",
                "title": post.title,
                "url": post.url,
                "date": post.date,
                "category": post.category,
            }
        )

    context = "\n\n".join(chunks).strip()
    return context, sources


def _llm_config() -> tuple[str, str, str]:
    api_key = os.getenv("OPENAI_API_KEY") or os.getenv("LLM_API_KEY") or ""
    model = os.getenv("OPENAI_MODEL") or os.getenv("LLM_MODEL") or "gpt-4.1-mini"
    api_base = (os.getenv("OPENAI_API_BASE") or os.getenv("LLM_API_BASE_URL") or "https://api.openai.com/v1").rstrip("/")
    return api_key, model, api_base


def _resolve_remote_mcp_server_url(explicit_remote_mcp_server_url: str = "") -> str:
    return (
        explicit_remote_mcp_server_url.strip()
        or (os.getenv("PUBLIC_MCP_SERVER_URL") or "").strip()
        or (os.getenv("REMOTE_MCP_SERVER_URL") or "").strip()
    )


def _use_remote_mcp_with_responses(explicit_remote_mcp_server_url: str = "") -> bool:
    return bool(_resolve_remote_mcp_server_url(explicit_remote_mcp_server_url))


def _build_user_prompt(question: str, page_url: str = "", page_title: str = "") -> str:
    return (
        f"[방문자가 보고 있던 페이지]\n제목: {page_title or '-'}\nURL: {page_url or '-'}\n\n"
        f"[질문]\n{question}"
    )


def _extract_output_text(body: dict[str, Any]) -> str:
    output_text = body.get("output_text")
    if isinstance(output_text, str) and output_text.strip():
        return output_text.strip()

    chunks: list[str] = []
    for item in body.get("output", []) or []:
        if item.get("type") != "message":
            continue
        for content in item.get("content", []) or []:
            if content.get("type") in {"output_text", "text"} and content.get("text"):
                chunks.append(str(content["text"]).strip())

    return "\n".join(chunk for chunk in chunks if chunk).strip()


def _sources_from_mcp_output(body: dict[str, Any]) -> list[dict[str, str]]:
    sources: list[dict[str, str]] = []
    seen: set[tuple[str, str]] = set()

    for item in body.get("output", []) or []:
        if item.get("type") != "mcp_call":
            continue

        name = str(item.get("name") or "")
        args_raw = item.get("arguments") or "{}"
        try:
            args = json.loads(args_raw) if isinstance(args_raw, str) else dict(args_raw)
        except Exception:
            args = {}

        source: dict[str, str] | None = None
        if name == "get_resume":
            source = {"type": "resume", "title": "CV", "url": CV_URL}
        elif name == "get_post_content":
            raw_value = str(args.get("url_or_path") or "").strip()
            if raw_value:
                source_url = _absolute_url(raw_value)
                source = {"type": "post", "title": raw_value, "url": source_url}

        if not source:
            continue

        dedupe_key = (source.get("type", ""), source.get("url", ""))
        if dedupe_key in seen:
            continue
        seen.add(dedupe_key)
        sources.append(source)

    return sources


def _tool_calls_from_mcp_output(body: dict[str, Any]) -> list[dict[str, Any]]:
    calls: list[dict[str, Any]] = []

    for item in body.get("output", []) or []:
        if item.get("type") != "mcp_call":
            continue

        args_raw = item.get("arguments") or "{}"
        try:
            arguments = json.loads(args_raw) if isinstance(args_raw, str) else dict(args_raw)
        except Exception:
            arguments = {"_raw": str(args_raw)}

        calls.append(
            {
                "tool": str(item.get("name") or ""),
                "arguments": arguments,
            }
        )

    return calls


def _parse_tool_arguments(args_raw: Any) -> dict[str, Any]:
    try:
        return json.loads(args_raw) if isinstance(args_raw, str) else dict(args_raw or {})
    except Exception:
        return {"_raw": str(args_raw)}


def _mcp_call_key(event: dict[str, Any], item: dict[str, Any] | None = None) -> str:
    if item:
        item_id = str(item.get("id") or "")
        if item_id:
            return item_id
    item_id = str(event.get("item_id") or "")
    if item_id:
        return item_id
    return f"output:{event.get('output_index', '')}"


def _normalize_sources(sources: list[dict[str, str]]) -> list[dict[str, str]]:
    normalized: list[dict[str, str]] = []
    seen: set[tuple[str, str]] = set()

    for source in sources:
        source_type = str(source.get("type") or "").strip()
        url = str(source.get("url") or "").strip()
        title = str(source.get("title") or "").strip()
        if not url:
            continue

        dedupe_key = (source_type, url)
        if dedupe_key in seen:
            continue
        seen.add(dedupe_key)

        if source_type == "post" and (not title or title == url):
            title = url.rstrip("/").split("/")[-1] or "Post"
        elif source_type == "resume" and not title:
            title = "CV"

        normalized.append(
            {
                "type": source_type,
                "title": title or url,
                "url": url,
            }
        )

    return normalized


def _call_chat_completion(system_prompt: str, user_prompt: str) -> str:
    api_key, model, api_base = _llm_config()
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY 또는 LLM_API_KEY 환경변수가 필요합니다.")

    payload = {
        "model": model,
        "temperature": 0.2,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
    }
    req = urllib.request.Request(
        url=f"{api_base}/chat/completions",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=45) as response:
            body = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"AI provider error: HTTP {exc.code} - {detail}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"AI provider connection error: {exc}") from exc

    choices = body.get("choices") or []
    if not choices:
        raise RuntimeError("AI provider returned no choices.")

    message = choices[0].get("message") or {}
    content = message.get("content")
    if isinstance(content, str):
        return content.strip()
    if isinstance(content, list):
        parts = [part.get("text", "") for part in content if isinstance(part, dict)]
        return "\n".join(part for part in parts if part).strip()
    raise RuntimeError("AI provider returned an unsupported response format.")


def _call_responses_with_remote_mcp(
    system_prompt: str, user_prompt: str, remote_mcp_server_url: str = ""
) -> dict[str, Any]:
    api_key, model, api_base = _llm_config()
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY 또는 LLM_API_KEY 환경변수가 필요합니다.")

    remote_mcp_server_url = _resolve_remote_mcp_server_url(remote_mcp_server_url)
    if not remote_mcp_server_url:
        raise RuntimeError("PUBLIC_MCP_SERVER_URL 또는 REMOTE_MCP_SERVER_URL 환경변수가 필요합니다.")

    payload = {
        "model": model,
        "instructions": system_prompt,
        "input": user_prompt,
        "tools": [
            {
                "type": "mcp",
                "server_label": "blog_mcp",
                "server_url": remote_mcp_server_url,
                "allowed_tools": REMOTE_MCP_ALLOWED_TOOLS,
                "require_approval": "never",
            }
        ],
    }

    req = urllib.request.Request(
        url=f"{api_base}/responses",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            body = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Responses API error: HTTP {exc.code} - {detail}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Responses API connection error: {exc}") from exc

    answer = _extract_output_text(body)
    if not answer:
        raise RuntimeError("Responses API returned no output text.")

    sources = _sources_from_mcp_output(body)
    tool_calls = _tool_calls_from_mcp_output(body)
    normalized_sources = _normalize_sources(sources)
    return {
        "answer": answer,
        "sources": normalized_sources,
        "tool_calls": tool_calls,
    }


def _stream_responses_with_remote_mcp(
    system_prompt: str, user_prompt: str, remote_mcp_server_url: str = ""
):
    api_key, model, api_base = _llm_config()
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY 또는 LLM_API_KEY 환경변수가 필요합니다.")

    remote_mcp_server_url = _resolve_remote_mcp_server_url(remote_mcp_server_url)
    if not remote_mcp_server_url:
        raise RuntimeError("PUBLIC_MCP_SERVER_URL 또는 REMOTE_MCP_SERVER_URL 환경변수가 필요합니다.")

    payload = {
        "model": model,
        "instructions": system_prompt,
        "input": user_prompt,
        "stream": True,
        "tools": [
            {
                "type": "mcp",
                "server_label": "blog_mcp",
                "server_url": remote_mcp_server_url,
                "allowed_tools": REMOTE_MCP_ALLOWED_TOOLS,
                "require_approval": "never",
            }
        ],
    }

    req = urllib.request.Request(
        url=f"{api_base}/responses",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )

    call_id = str(uuid.uuid4())
    answer_chunks: list[str] = []
    tool_calls: list[dict[str, Any]] = []
    seen_tools: set[str] = set()
    mcp_call_items: dict[str, dict[str, Any]] = {}
    final_response: dict[str, Any] | None = None

    def emit_tool_call(tool_name: str, arguments: dict[str, Any]):
        dedupe_key = json.dumps({"tool": tool_name, "arguments": arguments}, ensure_ascii=False, sort_keys=True)
        if not tool_name or dedupe_key in seen_tools:
            return None
        seen_tools.add(dedupe_key)
        tool_call = {"tool": tool_name, "arguments": arguments}
        tool_calls.append(tool_call)
        return [
            {"event": "tool_call", "call_id": call_id, "tool_call": tool_call},
        ]

    seen_event_types: list[str] = []
    ignored_lines: list[str] = []

    def handle_stream_event(event: dict[str, Any]) -> list[dict[str, Any]]:
        event_type = str(event.get("type") or "")
        if event_type:
            seen_event_types.append(event_type)

        emitted_events: list[dict[str, Any]] = []

        if event_type == "error":
            error = event.get("error") or event
            raise RuntimeError(f"Responses API stream error: {json.dumps(error, ensure_ascii=False)}")
        if event_type in {"response.failed", "response.incomplete"}:
            response_body = event.get("response") or {}
            error = response_body.get("error") or response_body.get("incomplete_details") or response_body
            raise RuntimeError(f"Responses API {event_type}: {json.dumps(error, ensure_ascii=False)}")

        if event_type == "response.output_text.delta":
            delta = str(event.get("delta") or "")
            if delta:
                answer_chunks.append(delta)
                emitted_events.append({"event": "answer_delta", "call_id": call_id, "delta": delta})
        elif event_type == "response.output_text.done":
            text = str(event.get("text") or "")
            if text and not answer_chunks:
                answer_chunks.append(text)
                emitted_events.append({"event": "answer_delta", "call_id": call_id, "delta": text})
        elif event_type == "response.output_item.added":
            item = event.get("item") or {}
            if item.get("type") == "mcp_call":
                key = _mcp_call_key(event, item)
                mcp_call_items[key] = {
                    "tool": str(item.get("name") or ""),
                    "arguments": _parse_tool_arguments(item.get("arguments") or "{}"),
                }
        elif event_type == "response.mcp_call_arguments.done":
            key = _mcp_call_key(event)
            cached = mcp_call_items.setdefault(key, {"tool": "", "arguments": {}})
            cached["arguments"] = _parse_tool_arguments(event.get("arguments") or "{}")
            emitted_events.extend(emit_tool_call(str(cached.get("tool") or ""), cached["arguments"]) or [])
        elif event_type == "response.output_item.done":
            item = event.get("item") or {}
            if item.get("type") == "message" and not answer_chunks:
                text = _extract_output_text({"output": [item]})
                if text:
                    answer_chunks.append(text)
                    emitted_events.append({"event": "answer_delta", "call_id": call_id, "delta": text})
            elif item.get("type") == "mcp_call":
                key = _mcp_call_key(event, item)
                cached = mcp_call_items.setdefault(key, {"tool": "", "arguments": {}})
                cached["tool"] = str(item.get("name") or cached.get("tool") or "")
                if "arguments" in item:
                    cached["arguments"] = _parse_tool_arguments(item.get("arguments") or "{}")
                emitted_events.extend(emit_tool_call(str(cached.get("tool") or ""), cached.get("arguments") or {}) or [])
        elif event_type == "response.completed":
            response_body = event.get("response") or {}
            if isinstance(response_body, dict):
                final_response.update(response_body)

        return emitted_events

    final_response = {}

    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            data_lines: list[str] = []
            for raw_line in response:
                line = raw_line.decode("utf-8", errors="replace").rstrip("\r\n")
                if line == "":
                    if not data_lines:
                        continue
                    data = "\n".join(data_lines)
                    data_lines = []
                    if data == "[DONE]":
                        break
                    try:
                        event = json.loads(data)
                    except json.JSONDecodeError:
                        ignored_lines.append(data)
                        continue
                    for emitted in handle_stream_event(event):
                        yield emitted
                    continue

                if line.startswith("data:"):
                    data_lines.append(line[5:].lstrip())
                    continue
                if line.startswith(("event:", "id:", "retry:", ":")):
                    continue

                ignored_lines.append(line)

            if data_lines:
                data = "\n".join(data_lines)
                if data != "[DONE]":
                    try:
                        event = json.loads(data)
                    except json.JSONDecodeError:
                        ignored_lines.append(data)
                    else:
                        for emitted in handle_stream_event(event):
                            yield emitted

            if not seen_event_types and ignored_lines:
                body_text = "\n".join(ignored_lines).strip()
                try:
                    body = json.loads(body_text)
                except json.JSONDecodeError:
                    preview = body_text[:500]
                    raise RuntimeError(f"Responses API returned non-SSE data: {preview}")
                if isinstance(body, dict) and body.get("error"):
                    raise RuntimeError(f"Responses API error: {json.dumps(body['error'], ensure_ascii=False)}")
                if isinstance(body, dict):
                    final_response.update(body)
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Responses API error: HTTP {exc.code} - {detail}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Responses API connection error: {exc}") from exc

    answer = "".join(answer_chunks).strip()
    final_answer = _extract_output_text(final_response)
    if final_answer and final_answer not in answer:
        separator = "\n" if answer else ""
        answer = f"{answer}{separator}{final_answer}".strip()
        yield {"event": "answer_delta", "call_id": call_id, "delta": f"{separator}{final_answer}"}

    sources: list[dict[str, str]] = []
    sources = _normalize_sources(_sources_from_mcp_output(final_response))
    if not tool_calls:
        for tool_call in _tool_calls_from_mcp_output(final_response):
            for emitted in emit_tool_call(str(tool_call.get("tool") or ""), tool_call.get("arguments") or {}) or []:
                yield emitted

    if not answer and not tool_calls:
        status = str(final_response.get("status") or "")
        error = final_response.get("error") or final_response.get("incomplete_details") or ""
        event_summary = ", ".join(dict.fromkeys(seen_event_types)) or "none"
        raise RuntimeError(
            "Responses stream produced no answer or tool calls. "
            f"status={status or '-'}, events={event_summary}, error={json.dumps(error, ensure_ascii=False)}"
        )

    yield {
        "event": "done",
        "call_id": call_id,
        "result": {
            "answer": answer,
            "sources": sources,
            "tool_calls": tool_calls,
            "mode": "responses_remote_mcp_stream",
        },
    }



def answer_visitor_question(
    question: str,
    page_url: str = "",
    page_title: str = "",
    remote_mcp_server_url: str = "",
) -> dict[str, Any]:
    clean_question = (question or "").strip()
    if not clean_question:
        raise ValueError("question is required")
    if len(clean_question) > 1500:
        raise ValueError("question is too long")

    system_prompt = (
        "너는 황보규민의 블로그 방문자 질문을 대신 답변하는 AI다. "
        "답변은 한국어로 작성한다. "
        "가능하면 먼저 연결된 MCP 도구를 사용해서 필요한 정보만 찾아본 뒤 답한다. "
        "특정 주제, 키워드, 기술명을 물으면 search_posts 를 우선 사용해 관련 글을 찾는다. "
        "제공된 MCP 도구와 그 결과 안에서만 답하고, 추측이 필요한 경우에는 추측이라고 밝힌다. "
        "정보가 없으면 모른다고 답한다. "
        "참고 링크를 포함할 때는 반드시 Markdown 링크 형식 [제목](URL) 만 사용하고, 생 URL은 쓰지 않는다. "
        "특히 https://ghkdqhrbals.github.io/portfolios/docs 로 시작하는 URL은 반드시 [글 제목](URL) Markdown 링크 형식으로만 작성한다. "
        "링크가 필요하면 [링크](URL) 같은 일반 라벨 대신 글 제목 자체를 링크로 만든다. "
        "URL 끝의 불필요한 trailing slash 는 제거된 형태를 사용한다. "
        "답변 말미에 관련 포스팅이나 이력서를 언급할 때는 가능하면 Markdown 링크로 직접 연결한다. "
        "답변 끝에는 짧게 핵심만 정리한다."
    )
    user_prompt = _build_user_prompt(clean_question, page_url=page_url, page_title=page_title)

    if _use_remote_mcp_with_responses(remote_mcp_server_url):
        result = _call_responses_with_remote_mcp(
            system_prompt, user_prompt, remote_mcp_server_url=remote_mcp_server_url
        )
        result["mode"] = "responses_remote_mcp"
        return result

    context, sources = build_context(clean_question, page_url=page_url, page_title=page_title)
    if not context:
        raise RuntimeError("블로그 컨텍스트를 찾지 못했습니다.")

    fallback_user_prompt = user_prompt + f"\n\n[참고 컨텍스트]\n{context}"
    answer = _call_chat_completion(system_prompt, fallback_user_prompt)
    normalized_sources = _normalize_sources(sources)
    return {
        "answer": answer,
        "sources": normalized_sources,
        "mode": "chat_completions_context_fallback",
    }


def stream_visitor_question(
    question: str,
    page_url: str = "",
    page_title: str = "",
    remote_mcp_server_url: str = "",
):
    clean_question = (question or "").strip()
    if not clean_question:
        raise ValueError("question is required")
    if len(clean_question) > 1500:
        raise ValueError("question is too long")

    if not _use_remote_mcp_with_responses(remote_mcp_server_url):
        raise RuntimeError("스트리밍은 remote MCP + Responses API 모드에서만 지원됩니다.")

    system_prompt = (
        "너는 황보규민의 블로그 방문자 질문을 대신 답변하는 AI다. "
        "답변은 한국어로 작성한다. "
        "가능하면 먼저 연결된 MCP 도구를 사용해서 필요한 정보만 찾아본 뒤 답한다. "
        "특정 주제, 키워드, 기술명을 물으면 search_posts 를 우선 사용해 관련 글을 찾는다. "
        "제공된 MCP 도구와 그 결과 안에서만 답하고, 추측이 필요한 경우에는 추측이라고 밝힌다. "
        "정보가 없으면 모른다고 답한다. "
        "참고 링크를 포함할 때는 반드시 Markdown 링크 형식 [제목](URL) 만 사용하고, 생 URL은 쓰지 않는다. "
        "특히 https://ghkdqhrbals.github.io/portfolios/docs 로 시작하는 URL은 반드시 [글 제목](URL) Markdown 링크 형식으로만 작성한다. "
        "링크가 필요하면 [링크](URL) 같은 일반 라벨 대신 글 제목 자체를 링크로 만든다. "
        "URL 끝의 불필요한 trailing slash 는 제거된 형태를 사용한다."
    )
    user_prompt = _build_user_prompt(clean_question, page_url=page_url, page_title=page_title)
    yield from _stream_responses_with_remote_mcp(
        system_prompt, user_prompt, remote_mcp_server_url=remote_mcp_server_url
    )
