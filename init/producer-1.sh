#!/usr/bin/env bash
PORT=9001
MAXLEN=10000
COUNT=100

for i in $(seq 1 "$COUNT"); do
  # SummaryEvent 형태의 JSON 페이로드 생성
  PAYLOAD=$(cat <<EOF
{
  "searchEventId": "search-event-$(printf '%010d' $i)",
  "paperId": "paper-$(printf '%010d' $i)",
  "arxivId": "arxiv-$(printf '%010d' $i)",
  "title": "Research Paper Title Number $i",
  "abstract": "This is an abstract for research paper $i. It contains important information about the research conducted and the findings obtained from the study.",
  "journalRefRaw": "Journal of Research, Volume $((i % 100 + 1)), Issue $((i % 12 + 1)), 2024"
}
EOF
)

  redis-cli -c -p "$PORT" \
    XADD "summary:1" MAXLEN "~" "$MAXLEN" "*" \
    message "$PAYLOAD" >/dev/null

  if (( i % 100 == 0 )); then
    echo "inserted $i messages"
  fi
done

echo "Total $COUNT messages inserted to summary:1"
