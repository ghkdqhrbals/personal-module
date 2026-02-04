# app.py
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from datetime import datetime, timezone, timedelta
import sqlite3, hashlib
from typing import Optional

app = FastAPI()

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
DB = "guestbook.db"

def hash_pw(pw: str) -> str:
    return hashlib.sha256(pw.encode()).hexdigest()

def conn():
    return sqlite3.connect(DB)

# init
with conn() as c:
    # 테이블 생성
    c.execute("""
    CREATE TABLE IF NOT EXISTS guestbook (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT,
      pw_hash TEXT,
      message TEXT,
      page TEXT,
            created_at TEXT,
            parent_id INTEGER
    )
    """)

    # 칼럼 존재 확인 및 추가
    columns = c.execute("PRAGMA table_info(guestbook)").fetchall()
    column_names = [col[1] for col in columns]

    if 'page' not in column_names:
        c.execute("ALTER TABLE guestbook ADD COLUMN page TEXT")
    if 'created_at' not in column_names:
        c.execute("ALTER TABLE guestbook ADD COLUMN created_at TEXT")
    if 'parent_id' not in column_names:
        c.execute("ALTER TABLE guestbook ADD COLUMN parent_id INTEGER")

class CreateReq(BaseModel):
    name: str
    password: str
    message: str = Field(..., max_length=500)
    page: str
    parent_id: Optional[int] = None

class UpdateReq(BaseModel):
    password: str
    message: str = Field(..., max_length=500)

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/guestbook")
def create(req: CreateReq):
    if req.parent_id is not None:
        with conn() as c:
            parent = c.execute(
                "SELECT id, page FROM guestbook WHERE id=?",
                (req.parent_id,),
            ).fetchone()
            if not parent:
                raise HTTPException(404, "parent not found")
            if parent[1] != req.page:
                raise HTTPException(400, "parent page mismatch")
    with conn() as c:
        c.execute(
            "INSERT INTO guestbook (name, pw_hash, message, page, created_at, parent_id) VALUES (?,?,?,?,?,?)",
            (req.name, hash_pw(req.password), req.message, req.page, datetime.utcnow().isoformat(), req.parent_id)
        )
    return {"result": "created"}

@app.get("/guestbook")
def list_guestbook(
        page_filter: str = "",
        page: int = 1,
        per_page: int = 10,
        order: str = "desc",
        sort: Optional[str] = None,
):
    order_l = ((sort if sort is not None else order) or "").strip().lower()
    if order_l not in {"asc", "desc"}:
        raise HTTPException(400, "invalid order")
    order_sql = "ASC" if order_l == "asc" else "DESC"

    if per_page < 1:
        per_page = 10
    if per_page > 50:
        per_page = 50
    if page < 1:
        page = 1

    offset = (page - 1) * per_page
    with conn() as c:
        if page_filter:
            rows = c.execute(
                f"SELECT id, name, message, created_at, parent_id FROM guestbook WHERE page=? AND parent_id IS NULL ORDER BY id {order_sql} LIMIT ? OFFSET ?",
                (page_filter, per_page, offset),
            ).fetchall()
            total = c.execute(
                "SELECT COUNT(*) FROM guestbook WHERE page=? AND parent_id IS NULL",
                (page_filter,),
            ).fetchone()[0]
        else:
            rows = c.execute(
                f"SELECT id, name, message, created_at, parent_id FROM guestbook WHERE parent_id IS NULL ORDER BY id {order_sql} LIMIT ? OFFSET ?",
                (per_page, offset),
            ).fetchall()
            total = c.execute(
                "SELECT COUNT(*) FROM guestbook WHERE parent_id IS NULL",
            ).fetchone()[0]

        parent_ids = [r[0] for r in rows]
        replies_by_parent = {pid: [] for pid in parent_ids}

        if parent_ids:
            placeholders = ",".join(["?"] * len(parent_ids))
            reply_rows = c.execute(
                f"SELECT id, name, message, created_at, parent_id FROM guestbook WHERE parent_id IN ({placeholders}) ORDER BY parent_id ASC, id ASC",
                tuple(parent_ids),
            ).fetchall()
            for rr in reply_rows:
                replies_by_parent.setdefault(rr[4], []).append(rr)
    # 날짜 변환
    kst = timezone(timedelta(hours=9))

    def fmt_row(row):
        id, name, message, created_at, parent_id = row
        dt = datetime.fromisoformat(created_at).replace(tzinfo=timezone.utc).astimezone(kst)
        formatted_date = dt.strftime('%Y-%m-%d %H:%M')
        return (id, name, message, formatted_date, parent_id)

    threads = []
    for row in rows:
        parent_id = row[0]
        threads.append(
            {
                "entry": fmt_row(row),
                "replies": [fmt_row(r) for r in replies_by_parent.get(parent_id, [])],
            }
        )

    return {"threads": threads, "total": total, "page": page, "per_page": per_page}

@app.put("/guestbook/{id}")
def update(id: int, req: UpdateReq):
    with conn() as c:
        row = c.execute("SELECT pw_hash FROM guestbook WHERE id=?", (id,)).fetchone()
        if not row or row[0] != hash_pw(req.password):
            raise HTTPException(403, "invalid password")
        c.execute("UPDATE guestbook SET message=? WHERE id=?", (req.message, id))
    return {"result": "updated"}

@app.delete("/guestbook/{id}")
def delete(id: int, password: str):
    with conn() as c:
        row = c.execute("SELECT pw_hash FROM guestbook WHERE id=?", (id,)).fetchone()
        if not row or row[0] != hash_pw(password):
            raise HTTPException(403, "invalid password")
        c.execute("DELETE FROM guestbook WHERE id=? OR parent_id=?", (id, id))
    return {"result": "deleted"}