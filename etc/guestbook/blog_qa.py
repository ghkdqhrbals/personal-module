from __future__ import annotations

import json
import os
import re
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any

import frontmatter

BASE_DIR = Path(__file__).resolve().parents[2]
DOCS_DIR = BASE_DIR / "docs"
CV_PATH = BASE_DIR / "cv.md"
SITE_BASE_URL = "https://ghkdqhrbals.github.io/portfolios"
REMOTE_MCP_ALLOWED_TOOLS = [
    "get_recent_posts",
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
    content: str


def _normalize_text(value: str) -> str:
    return re.sub(r"\s+", " ", (value or "").strip()).lower()


def _tokenize(value: str) -> set[str]:
    text = _normalize_text(value)
    return {token for token in re.split(r"[^0-9a-zA-Z가-힣]+", text) if len(token) >= 2}


def _load_markdown(path: Path):
    return frontmatter.load(str(path))


def collect_posts() -> list[PostRecord]:
    posts: list[PostRecord] = []
    if not DOCS_DIR.exists():
        return posts

    for md_file in sorted(DOCS_DIR.rglob("*.md")):
        try:
            post = _load_markdown(md_file)
        except Exception:
            continue

        date_val = post.metadata.get("date")
        title = post.metadata.get("title", "")
        parent = post.metadata.get("parent", "")

        if not date_val or not title:
            continue

        if isinstance(date_val, datetime):
            date_obj = date_val
            date_str = date_val.strftime("%Y-%m-%d")
        else:
            date_str = str(date_val)
            try:
                date_obj = datetime.strptime(date_str[:10], "%Y-%m-%d")
            except ValueError:
                continue

        rel = md_file.relative_to(BASE_DIR)
        url_path = "/" + str(rel).replace("\\", "/").replace(".md", "/")
        url = SITE_BASE_URL.rstrip("/") + url_path
        parts = rel.parts
        category = parts[1] if len(parts) > 2 else ""

        posts.append(
            PostRecord(
                date=date_str,
                date_obj=date_obj,
                title=title,
                parent=parent,
                category=category,
                url=url,
                file_path=str(md_file),
                content=post.content,
            )
        )

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


def read_resume() -> str:
    if not CV_PATH.exists():
        return ""
    try:
        post = _load_markdown(CV_PATH)
        return post.content
    except Exception:
        return ""


def get_post_content(url_or_path: str) -> str:
    path_str = url_or_path
    if path_str.startswith("http"):
        prefix = SITE_BASE_URL.rstrip("/")
        path_str = path_str[len(prefix):].lstrip("/")
        path_str = path_str.rstrip("/") + ".md"

    target = BASE_DIR / path_str
    if not target.exists():
        alt = BASE_DIR / path_str.replace(".md", "") / "index.md"
        if alt.exists():
            target = alt
        else:
            return f"오류: '{path_str}' 파일을 찾을 수 없습니다."

    try:
        post = _load_markdown(target)
        meta_lines = [f"# {post.metadata.get('title', target.stem)}"]
        if post.metadata.get("date"):
            meta_lines.append(f"**날짜**: {post.metadata['date']}")
        if post.metadata.get("parent"):
            meta_lines.append(f"**카테고리**: {post.metadata['parent']}")
        header = "\n".join(meta_lines)
        return header + "\n\n---\n\n" + post.content
    except Exception as exc:
        return f"파일 읽기 오류: {exc}"


def list_categories_with_counts() -> dict[str, int]:
    counts: dict[str, int] = {}
    for post in collect_posts():
        category = post.category or "기타"
        counts[category] = counts.get(category, 0) + 1
    return dict(sorted(counts.items(), key=lambda item: item[1], reverse=True))


def _score_post(question_tokens: set[str], page_url: str, page_title: str, post: PostRecord) -> int:
    score = 0
    haystack_tokens = _tokenize(" ".join([post.title, post.parent, post.category, post.content[:2500]]))

    score += len(question_tokens & haystack_tokens) * 4
    score += len(question_tokens & _tokenize(post.title)) * 5

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
        if score > 0:
            ranked.append((score, post))

    ranked.sort(key=lambda item: (item[0], item[1].date_obj), reverse=True)
    return [post for _, post in ranked[:limit]]


def build_context(question: str, page_url: str = "", page_title: str = "") -> tuple[str, list[dict[str, str]]]:
    sources: list[dict[str, str]] = []
    chunks: list[str] = []

    resume = read_resume()
    if resume:
        chunks.append("## Resume\n" + resume[:4000].strip())
        sources.append({"type": "resume", "title": "CV", "url": SITE_BASE_URL + "/cv/"})

    for post in select_relevant_posts(question, page_url=page_url, page_title=page_title):
        excerpt = post.content.strip()[:3500]
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

    return "\n\n".join(chunks).strip(), sources


def _llm_config() -> tuple[str, str, str]:
    api_key = os.getenv("OPENAI_API_KEY") or os.getenv("LLM_API_KEY") or ""
    model = os.getenv("OPENAI_MODEL") or os.getenv("LLM_MODEL") or "gpt-4.1-mini"
    api_base = (os.getenv("OPENAI_API_BASE") or os.getenv("LLM_API_BASE_URL") or "https://api.openai.com/v1").rstrip("/")
    return api_key, model, api_base


def _use_remote_mcp_with_responses() -> bool:
    return bool((os.getenv("REMOTE_MCP_SERVER_URL") or "").strip())


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
            source = {"type": "resume", "title": "CV", "url": SITE_BASE_URL + "/cv/"}
        elif name == "get_post_content":
            raw_value = str(args.get("url_or_path") or "").strip()
            if raw_value:
                source_url = raw_value
                if not source_url.startswith("http"):
                    source_url = SITE_BASE_URL.rstrip("/") + "/" + raw_value.strip("/").replace(".md", "/")
                source = {"type": "post", "title": raw_value, "url": source_url}

        if not source:
            continue

        dedupe_key = (source.get("type", ""), source.get("url", ""))
        if dedupe_key not in seen:
            seen.add(dedupe_key)
            sources.append(source)

    return sources


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


def _call_responses_with_remote_mcp(system_prompt: str, user_prompt: str) -> dict[str, Any]:
    api_key, model, api_base = _llm_config()
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY 또는 LLM_API_KEY 환경변수가 필요합니다.")

    remote_mcp_server_url = (os.getenv("REMOTE_MCP_SERVER_URL") or "").strip()
    if not remote_mcp_server_url:
        raise RuntimeError("REMOTE_MCP_SERVER_URL 환경변수가 필요합니다.")

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

    return {"answer": answer, "sources": _sources_from_mcp_output(body)}


def answer_visitor_question(question: str, page_url: str = "", page_title: str = "") -> dict[str, Any]:
    clean_question = (question or "").strip()
    if not clean_question:
        raise ValueError("question is required")
    if len(clean_question) > 1500:
        raise ValueError("question is too long")

    system_prompt = (
        "너는 황보규민의 블로그 방문자 질문을 대신 답변하는 AI다. "
        "답변은 한국어로 작성한다. "
        "가능하면 먼저 연결된 MCP 도구를 사용해서 필요한 정보만 찾아본 뒤 답한다. "
        "제공된 MCP 도구와 그 결과 안에서만 답하고, 추측이 필요한 경우에는 추측이라고 밝힌다. "
        "정보가 없으면 모른다고 답한다. "
        "답변 끝에는 짧게 핵심만 정리하고, 필요한 경우 참고한 글 제목이나 이력서를 언급한다."
    )
    user_prompt = _build_user_prompt(clean_question, page_url=page_url, page_title=page_title)

    if _use_remote_mcp_with_responses():
        result = _call_responses_with_remote_mcp(system_prompt, user_prompt)
        result["mode"] = "responses_remote_mcp"
        return result

    context, sources = build_context(clean_question, page_url=page_url, page_title=page_title)
    if not context:
        raise RuntimeError("블로그 컨텍스트를 찾지 못했습니다.")

    fallback_user_prompt = user_prompt + f"\n\n[참고 컨텍스트]\n{context}"
    answer = _call_chat_completion(system_prompt, fallback_user_prompt)
    return {"answer": answer, "sources": sources, "mode": "chat_completions_context_fallback"}
