from collections import deque
from pathlib import Path
import sys

from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import app as guestbook_app


def _ask_payload(question: str = "Redis Stream pending이 뭐야?") -> dict[str, str]:
    return {
        "question": question,
        "page_url": "https://ghkdqhrbals.github.io/portfolios/docs/redis-stream",
        "page_title": "Redis Stream",
    }


def _stub_answer(**kwargs):
    return {
        "answer": "stubbed answer",
        "sources": [],
        "tool_calls": [],
        "mode": "test",
        "received_question": kwargs["question"],
    }


def setup_function():
    guestbook_app._ask_rate_limit_hits.clear()


def test_health_endpoint():
    client = TestClient(guestbook_app.app)

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_ask_rate_limit_allows_limit_then_rejects_same_ip(monkeypatch):
    monkeypatch.setattr(guestbook_app, "ASK_RATE_LIMIT_PER_MINUTE", 2)
    monkeypatch.setattr(guestbook_app, "answer_visitor_question", _stub_answer)
    monkeypatch.setattr(guestbook_app, "_public_mcp_server_url", lambda request: "https://lowfidev.cloud/mcp/")
    client = TestClient(guestbook_app.app)
    headers = {"X-Forwarded-For": "203.0.113.10"}

    first = client.post("/ask", json=_ask_payload("first"), headers=headers)
    second = client.post("/ask", json=_ask_payload("second"), headers=headers)
    third = client.post("/ask", json=_ask_payload("third"), headers=headers)

    assert first.status_code == 200
    assert second.status_code == 200
    assert third.status_code == 429
    assert third.json()["detail"] == "Too many requests. Limit is 2 per minute."
    assert len(guestbook_app._ask_rate_limit_hits["203.0.113.10"]) == 2


def test_ask_rate_limit_is_isolated_by_client_ip(monkeypatch):
    monkeypatch.setattr(guestbook_app, "ASK_RATE_LIMIT_PER_MINUTE", 1)
    monkeypatch.setattr(guestbook_app, "answer_visitor_question", _stub_answer)
    monkeypatch.setattr(guestbook_app, "_public_mcp_server_url", lambda request: "https://lowfidev.cloud/mcp/")
    client = TestClient(guestbook_app.app)

    first_ip_first = client.post("/ask", json=_ask_payload(), headers={"X-Forwarded-For": "203.0.113.11"})
    first_ip_second = client.post("/ask", json=_ask_payload(), headers={"X-Forwarded-For": "203.0.113.11"})
    second_ip_first = client.post("/ask", json=_ask_payload(), headers={"X-Forwarded-For": "203.0.113.12"})

    assert first_ip_first.status_code == 200
    assert first_ip_second.status_code == 429
    assert second_ip_first.status_code == 200


def test_ask_rate_limit_prefers_first_forwarded_for_ip(monkeypatch):
    monkeypatch.setattr(guestbook_app, "ASK_RATE_LIMIT_PER_MINUTE", 1)
    monkeypatch.setattr(guestbook_app, "answer_visitor_question", _stub_answer)
    monkeypatch.setattr(guestbook_app, "_public_mcp_server_url", lambda request: "https://lowfidev.cloud/mcp/")
    client = TestClient(guestbook_app.app)
    headers = {"X-Forwarded-For": "203.0.113.13, 10.0.0.1"}

    first = client.post("/ask", json=_ask_payload(), headers=headers)
    second = client.post("/ask", json=_ask_payload(), headers=headers)

    assert first.status_code == 200
    assert second.status_code == 429
    assert "203.0.113.13" in guestbook_app._ask_rate_limit_hits
    assert "10.0.0.1" not in guestbook_app._ask_rate_limit_hits


def test_ask_rate_limit_expires_old_hits(monkeypatch):
    monkeypatch.setattr(guestbook_app, "ASK_RATE_LIMIT_PER_MINUTE", 1)
    monkeypatch.setattr(guestbook_app, "ASK_RATE_LIMIT_WINDOW_SECONDS", 60)
    monkeypatch.setattr(guestbook_app, "answer_visitor_question", _stub_answer)
    monkeypatch.setattr(guestbook_app, "_public_mcp_server_url", lambda request: "https://lowfidev.cloud/mcp/")
    guestbook_app._ask_rate_limit_hits["203.0.113.14"] = deque([1000.0])

    class FixedDateTime:
        @staticmethod
        def now():
            class FixedNow:
                @staticmethod
                def timestamp():
                    return 1060.0

            return FixedNow()

    monkeypatch.setattr(guestbook_app, "datetime", FixedDateTime)
    client = TestClient(guestbook_app.app)

    response = client.post("/ask", json=_ask_payload(), headers={"X-Forwarded-For": "203.0.113.14"})

    assert response.status_code == 200
    assert list(guestbook_app._ask_rate_limit_hits["203.0.113.14"]) == [1060.0]
