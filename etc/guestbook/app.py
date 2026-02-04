# app.py
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from datetime import datetime, timezone, timedelta
import sqlite3, hashlib

app = FastAPI()

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:4000", "http://127.0.0.1:4000", "https://ghkdqhrbals.github.io/portfolios"],  # Jekyll 서버 주소
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
      created_at TEXT
    )
    """)

    # 칼럼 존재 확인 및 추가
    columns = c.execute("PRAGMA table_info(guestbook)").fetchall()
    column_names = [col[1] for col in columns]

    if 'page' not in column_names:
        c.execute("ALTER TABLE guestbook ADD COLUMN page TEXT")
    if 'created_at' not in column_names:
        c.execute("ALTER TABLE guestbook ADD COLUMN created_at TEXT")

class CreateReq(BaseModel):
    name: str
    password: str
    message: str
    page: str

class UpdateReq(BaseModel):
    password: str
    message: str

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/guestbook")
def create(req: CreateReq):
    with conn() as c:
        c.execute(
            "INSERT INTO guestbook (name, pw_hash, message, page, created_at) VALUES (?,?,?,?,?)",
            (req.name, hash_pw(req.password), req.message, req.page, datetime.utcnow().isoformat())
        )
    return {"result": "created"}

@app.get("/guestbook")
def list_guestbook(page: str = "", page_num: int = 1, per_page: int = 10):
    offset = (page_num - 1) * per_page
    with conn() as c:
        if page:
            rows = c.execute("SELECT id, name, message, created_at FROM guestbook WHERE page=? ORDER BY id DESC LIMIT ? OFFSET ?", (page, per_page, offset)).fetchall()
            total = c.execute("SELECT COUNT(*) FROM guestbook WHERE page=?", (page,)).fetchone()[0]
        else:
            rows = c.execute("SELECT id, name, message, created_at FROM guestbook ORDER BY id DESC LIMIT ? OFFSET ?", (per_page, offset)).fetchall()
            total = c.execute("SELECT COUNT(*) FROM guestbook").fetchone()[0]
    # 날짜 변환
    kst = timezone(timedelta(hours=9))
    formatted_rows = []
    for row in rows:
        id, name, message, created_at = row
        dt = datetime.fromisoformat(created_at).replace(tzinfo=timezone.utc).astimezone(kst)
        formatted_date = dt.strftime('%Y-%m-%d %H:%M')
        formatted_rows.append((id, name, message, formatted_date))
    return {"entries": formatted_rows, "total": total, "page": page_num, "per_page": per_page}

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
        c.execute("DELETE FROM guestbook WHERE id=?", (id,))
    return {"result": "deleted"}