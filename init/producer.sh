#!/usr/bin/env bash
PORT=9001
MAXLEN=10000
COUNT=100
PARTITIONS=6

for i in $(seq 1 "$COUNT"); do
  PARTITION=$(( (i - 1) % PARTITIONS ))

  PAYLOAD=$(cat <<EOF
{
  "searchEventId": "search-event-$(printf '%010d' $i)",
  "paperId": "paper-$(printf '%010d' $i)",
  "arxivId": "arxiv-$(printf '%010d' $i)",
  "title": "Research Paper Title Number $i",
  "abstract": "This is an abstract for research paper $i.",
  "journalRefRaw": "Journal of Research, Volume $((i % 100 + 1)), Issue $((i % 12 + 1)), 2024"
}
EOF
)

  redis-cli -c -p "$PORT" \
    XADD "summary:${PARTITION}" MAXLEN "~" "$MAXLEN" "*" \
    message "$PAYLOAD" >/dev/null
done

echo "Total $COUNT messages inserted to summary:0~5"