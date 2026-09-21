## file 索引建表结构
```json
{
  "mappings": {
    "properties": {
      "fileName": {
        "type": "text",
        "fields": { "keyword": { "type": "keyword" } }
      },
      "resourceType": { "type": "keyword" },
      "resourceId": { "type": "keyword" },
      "resourceUrl": { "type": "keyword", "index": false },
      "visibleUserIds": { "type": "keyword" },
      "isPublic": { "type": "boolean" },
      "isDelete": { "type": "boolean" }
    }
  }
}
```