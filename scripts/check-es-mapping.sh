#!/bin/bash
# check-es-mapping.sh
# 检查 es_book 索引 mapping 是否与代码定义一致

ES_URL="${SPRING_ELASTICSEARCH_URIS:-http://localhost:9200}"
EXPECTED_FIELDS="title author description content"

echo "检查 es_book 索引..."

# 支持基础认证
if [ -n "$SPRING_ELASTICSEARCH_USERNAME" ] && [ -n "$SPRING_ELASTICSEARCH_PASSWORD" ]; then
    MAPPING=$(curl -u "${SPRING_ELASTICSEARCH_USERNAME}:${SPRING_ELASTICSEARCH_PASSWORD}" -sf "${ES_URL}/es_book/_mapping" 2>/dev/null)
else
    MAPPING=$(curl -sf "${ES_URL}/es_book/_mapping" 2>/dev/null)
fi

if [ $? -ne 0 ]; then
  echo "⚠️  es_book 索引不存在或连接失败，需要执行全量同步"
  exit 1
fi

for field in $EXPECTED_FIELDS; do
  if ! echo "$MAPPING" | grep -q "\"$field\""; then
    echo "❌ 字段 '$field' 在 ES mapping 中不存在，可能需要重建索引"
    exit 1
  fi
done

echo "✅ es_book mapping 检查通过"
