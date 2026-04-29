# app.py
import hashlib
import json
import os
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Optional

import requests
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from mcp.server.fastmcp import FastMCP
from pydantic import BaseModel, Field
from sqlalchemy import create_engine, Column, Integer, String, DateTime, ForeignKey
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, relationship

from blog_qa import (
    answer_visitor_question,
    collect_posts,
    get_post_content as load_post_content,
    list_categories_with_counts,
    post_summary,
    read_resume,
)

Base = declarative_base()

class Guestbook(Base):
    __tablename__ = 'guestbook'
    id = Column(Integer, primary_key=True, autoincrement=True)
    name = Column(String)
    pw_hash = Column(String)
    message = Column(String)
    page = Column(String)
    created_at = Column(DateTime)
    parent_id = Column(Integer, ForeignKey('guestbook.id'))
    replies = relationship("Guestbook", backref="parent", remote_side=[id])

database_url = os.getenv('DATABASE_URL', 'sqlite:///data/guestbook.db')
if database_url.startswith("sqlite:///"):
    sqlite_path = database_url.replace("sqlite:///", "", 1)
    Path(sqlite_path).parent.mkdir(parents=True, exist_ok=True)
engine = create_engine(database_url)
Session = sessionmaker(bind=engine)
Base.metadata.create_all(engine)

app = FastAPI()

mcp = FastMCP(
    "ghkdqhrbals-blog",
    instructions=(
        "이 서버는 황보규민의 기술 블로그 최근 포스팅과 이력서 정보를 제공합니다. "
        "get_recent_posts 로 최근 포스팅을 조회하고, "
        "get_post_content 로 특정 포스팅 내용을, "
        "get_resume 으로 이력서를 확인할 수 있습니다. "
        "answer_visitor_question 으로 블로그 방문자 질문에 답변할 수도 있습니다."
    ),
    streamable_http_path="/",
)

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    # CORS origins must be scheme + host + optional port (no path).
    allow_origins=[
        "http://localhost:4000",
        "http://127.0.0.1:4000",
        "https://ghkdqhrbals.github.io",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.mount("/mcp", mcp.streamable_http_app())

def hash_pw(pw: str) -> str:
    return hashlib.sha256(pw.encode()).hexdigest()

def send_slack_message(message: str):
    webhook_url = os.getenv('SLACK_WEBHOOK_URL')
    if webhook_url:
        payload = {"text": message}
        response = requests.post(webhook_url, json=payload)
        if response.status_code != 200:
            print(f"Failed to send Slack message: {response.text}")

class CreateReq(BaseModel):
    name: str
    password: str
    message: str = Field(..., max_length=500)
    page: str
    parent_id: Optional[int] = None

class UpdateReq(BaseModel):
    password: str
    message: str = Field(..., max_length=500)


@mcp.tool()
def get_recent_posts(limit: int = 10, category: str = "") -> str:
    limit = max(1, min(limit, 100))
    posts = collect_posts()
    if category:
        posts = [p for p in posts if p.category.lower() == category.lower()]
    return json.dumps([post_summary(p) for p in posts[:limit]], ensure_ascii=False, indent=2)


@mcp.tool()
def get_post_content(url_or_path: str) -> str:
    return load_post_content(url_or_path)


@mcp.tool()
def get_resume() -> str:
    content = read_resume()
    return content or "이력서 파일을 찾을 수 없습니다."


@mcp.tool()
def list_categories() -> str:
    return json.dumps(list_categories_with_counts(), ensure_ascii=False, indent=2)


@mcp.tool()
def answer_blog_visitor_question(question: str, page_url: str = "", page_title: str = "") -> str:
    result = answer_visitor_question(question=question, page_url=page_url, page_title=page_title)
    return json.dumps(result, ensure_ascii=False, indent=2)


@mcp.resource("blog://recent")
def resource_recent_posts() -> str:
    return get_recent_posts()


@mcp.resource("blog://resume")
def resource_resume() -> str:
    return get_resume()


@mcp.resource("blog://categories")
def resource_categories() -> str:
    return list_categories()

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/guestbook")
def create(req: CreateReq):
    session = Session()
    try:
        if req.parent_id is not None:
            parent = session.query(Guestbook).filter(Guestbook.id == req.parent_id).first()
            if not parent:
                raise HTTPException(404, "parent not found")
            if parent.page != req.page:
                raise HTTPException(400, "parent page mismatch")
        entry = Guestbook(
            name=req.name,
            pw_hash=hash_pw(req.password),
            message=req.message,
            page=req.page,
            created_at=datetime.utcnow(),
            parent_id=req.parent_id
        )
        session.add(entry)
        session.commit()
        send_slack_message(f"New guestbook entry by {req.name} on page {req.page}: {req.message}")
        return {"result": "created"}
    finally:
        session.close()

@app.get("/guestbook")
def list_guestbook(
        page_filter: str = "",
        page: int = 1,
        per_page: int = 10,
        order: str = "desc",
        sort: Optional[str] = None,
):
    session = Session()
    try:
        order_l = ((sort if sort is not None else order) or "").strip().lower()
        if order_l not in {"asc", "desc"}:
            raise HTTPException(400, "invalid order")
        order_col = Guestbook.id.asc() if order_l == "asc" else Guestbook.id.desc()

        if per_page < 1:
            per_page = 10
        if per_page > 50:
            per_page = 50
        if page < 1:
            page = 1

        offset = (page - 1) * per_page
        query = session.query(Guestbook).filter(Guestbook.parent_id.is_(None))
        if page_filter:
            query = query.filter(Guestbook.page == page_filter)
            total = session.query(Guestbook).filter(Guestbook.page == page_filter, Guestbook.parent_id.is_(None)).count()
        else:
            total = session.query(Guestbook).filter(Guestbook.parent_id.is_(None)).count()

        rows = query.order_by(order_col).limit(per_page).offset(offset).all()

        parent_ids = [r.id for r in rows]
        replies_by_parent = {pid: [] for pid in parent_ids}

        if parent_ids:
            reply_rows = session.query(Guestbook).filter(Guestbook.parent_id.in_(parent_ids)).order_by(Guestbook.parent_id, Guestbook.id).all()
            for rr in reply_rows:
                replies_by_parent.setdefault(rr.parent_id, []).append(rr)

        # 날짜 변환
        kst = timezone(timedelta(hours=9))

        def fmt_row(entry):
            dt = entry.created_at.replace(tzinfo=timezone.utc).astimezone(kst)
            formatted_date = dt.strftime('%Y-%m-%d %H:%M')
            return (entry.id, entry.name, entry.message, formatted_date, entry.parent_id)

        threads = []
        for row in rows:
            threads.append(
                {
                    "entry": fmt_row(row),
                    "replies": [fmt_row(r) for r in replies_by_parent.get(row.id, [])],
                }
            )

        return {"threads": threads, "total": total, "page": page, "per_page": per_page}
    finally:
        session.close()

@app.put("/guestbook/{id}")
def update(id: int, req: UpdateReq):
    session = Session()
    try:
        entry = session.query(Guestbook).filter(Guestbook.id == id).first()
        if not entry or entry.pw_hash != hash_pw(req.password):
            raise HTTPException(403, "invalid password")
        entry.message = req.message
        session.commit()
        return {"result": "updated"}
    finally:
        session.close()

@app.delete("/guestbook/{id}")
def delete(id: int, password: str):
    session = Session()
    try:
        entry = session.query(Guestbook).filter(Guestbook.id == id).first()
        if not entry or entry.pw_hash != hash_pw(password):
            raise HTTPException(403, "invalid password")
        # Delete entry and its replies
        session.query(Guestbook).filter((Guestbook.id == id) | (Guestbook.parent_id == id)).delete()
        session.commit()
        return {"result": "deleted"}
    finally:
        session.close()
