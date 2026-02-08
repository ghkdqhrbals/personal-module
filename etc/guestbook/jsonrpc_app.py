# jsonrpc_app.py
import hashlib
from datetime import datetime, timezone, timedelta
import os
import requests
from jsonrpcserver import method, serve, dispatch
import json
from sqlalchemy import create_engine, Column, Integer, String, DateTime, ForeignKey
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, relationship

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

engine = create_engine('sqlite:////data/guestbook.db')
Session = sessionmaker(bind=engine)
Base.metadata.create_all(engine)

def hash_pw(pw: str) -> str:
    return hashlib.sha256(pw.encode()).hexdigest()

def send_slack_message(message: str):
    webhook_url = os.getenv('SLACK_WEBHOOK_URL')
    if webhook_url:
        payload = {"text": message}
        response = requests.post(webhook_url, json=payload)
        if response.status_code != 200:
            print(f"Failed to send Slack message: {response.text}")

@method
def health():
    return {"status": "ok"}

@method
def create_guestbook(name: str, password: str, message: str, page: str, parent_id=None):
    if len(message) > 500:
        raise ValueError("Message too long")
    session = Session()
    try:
        if parent_id is not None:
            parent = session.query(Guestbook).filter(Guestbook.id == parent_id).first()
            if not parent:
                raise ValueError("parent not found")
            if parent.page != page:
                raise ValueError("parent page mismatch")
        entry = Guestbook(
            name=name,
            pw_hash=hash_pw(password),
            message=message,
            page=page,
            created_at=datetime.utcnow(),
            parent_id=parent_id
        )
        session.add(entry)
        session.commit()
        send_slack_message(f"New guestbook entry by {name} on page {page}: {message}")
        return {"result": "created"}
    finally:
        session.close()

@method
def list_guestbook(page_filter="", page=1, per_page=10, order="desc", sort=None):
    session = Session()
    try:
        order_l = ((sort if sort is not None else order) or "").strip().lower()
        if order_l not in {"asc", "desc"}:
            raise ValueError("invalid order")
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

@method
def update_guestbook(id: int, password: str, message: str):
    if len(message) > 500:
        raise ValueError("Message too long")
    session = Session()
    try:
        entry = session.query(Guestbook).filter(Guestbook.id == id).first()
        if not entry or entry.pw_hash != hash_pw(password):
            raise ValueError("invalid password")
        entry.message = message
        session.commit()
        return {"result": "updated"}
    finally:
        session.close()

@method
def delete_guestbook(id: int, password: str):
    session = Session()
    try:
        entry = session.query(Guestbook).filter(Guestbook.id == id).first()
        if not entry or entry.pw_hash != hash_pw(password):
            raise ValueError("invalid password")
        # Delete entry and its replies
        session.query(Guestbook).filter((Guestbook.id == id) | (Guestbook.parent_id == id)).delete()
        session.commit()
        return {"result": "deleted"}
    finally:
        session.close()

@method
def docs():
    html = """
<!DOCTYPE html>
<html>
<head>
    <title>JSON-RPC API Documentation</title>
</head>
<body>
    <h1>JSON-RPC API Documentation</h1>
    <p>Available methods:</p>
    <ul>
        <li><strong>health()</strong>: Check service health</li>
        <li><strong>create_guestbook(name, password, message, page, parent_id=None)</strong>: Create a new guestbook entry</li>
        <li><strong>list_guestbook(page_filter="", page=1, per_page=10, order="desc", sort=None)</strong>: List guestbook entries</li>
        <li><strong>update_guestbook(id, password, message)</strong>: Update a guestbook entry</li>
        <li><strong>delete_guestbook(id, password)</strong>: Delete a guestbook entry</li>
        <li><strong>docs()</strong>: Show this documentation</li>
    </ul>
    <p>Example request:</p>
    <pre>
{
    "jsonrpc": "2.0",
    "method": "health",
    "id": 1
}
    </pre>
</body>
</html>
"""
    return html

if __name__ == "__main__":
    serve(port=8001)
