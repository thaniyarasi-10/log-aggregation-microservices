from datetime import datetime, timedelta, timezone
from typing import Any

from bson import ObjectId
from pymongo import MongoClient
from pymongo.collection import Collection
import os

MONGO_URI = os.getenv("MONGO_URI") or "mongodb+srv://thaniyarasik:Th%40ni2005@cluster0.37wfqrt.mongodb.net/logs_db?appName=Cluster0"

DB_NAME = "logs_db"
COLLECTION_NAME = "logs"

def get_collection() -> Collection:
    client = MongoClient(MONGO_URI)
    db = client[DB_NAME]
    return db[COLLECTION_NAME]


def _normalize_services(services: list[str] | None) -> list[str]:
    normalized: list[str] = []
    for service in services or []:
        text = str(service or "").strip()
        if text and text != "*" and text not in normalized:
            normalized.append(text)
    return normalized


def _serialize_value(value: Any) -> Any:
    if isinstance(value, datetime):
        return value.isoformat()
    if isinstance(value, ObjectId):
        return str(value)
    if isinstance(value, dict):
        return {key: _serialize_value(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_serialize_value(item) for item in value]
    return value


def _serialize_document(document: dict[str, Any]) -> dict[str, Any]:
    return {key: _serialize_value(value) for key, value in document.items()}



def _normalize_document_fields(document: dict[str, Any]) -> dict[str, Any]:
    """
    Normalize MongoDB document field names to standard format.
    Handles variations like 'serviceName' vs 'service', 'logLevel' vs 'level'.
    """
    normalized = document.copy()
    
    # Normalize service field
    if 'serviceName' in normalized and 'service' not in normalized:
        normalized['service'] = normalized.pop('serviceName')
    
    # Normalize level field
    if 'logLevel' in normalized and 'level' not in normalized:
        normalized['level'] = normalized.pop('logLevel')
    
    return normalized

def fetch_logs(
    role: str,
    services: list[str] | None,
    start: datetime | None = None,
    end: datetime | None = None,
    limit: int | None = 1000,
) -> list[dict[str, Any]]:
    """
    Fetch logs from MongoDB scoped by role and optionally by time range.

    Parameters
    ----------
    role     : "admin" or "dev"
    services : list of service names (required for dev role)
    start    : inclusive lower bound for timestamp (UTC-aware datetime)
    end      : inclusive upper bound for timestamp (UTC-aware datetime)
               Defaults to now when start is provided but end is omitted.
    """
    role_name = str(role or "").strip().lower()
    collection = get_collection()
    projection = {
        "service": 1,
        "timestamp": 1,
        "level": 1,
        "message": 1,
        "responseTime": 1,
        "statusCode": 1,
        "traceId": 1,
        "spanId": 1,
        "userId": 1,
        "endpoint": 1,
        "method": 1,
        "environment": 1,
        "errorCode": 1,
        "errorDetails": 1,
        "errorType": 1,
        # alternate field names
        "serviceName": 1,
        "logLevel": 1,
    }

    # ── Build base query ──────────────────────────────────────────────
    if role_name == "admin":
        query: dict[str, Any] = {}
    elif role_name == "dev":
        scoped_services = _normalize_services(services)
        if not scoped_services:
            return []
        query = {
            "$or": [
                {"service": {"$in": scoped_services}},
                {"serviceName": {"$in": scoped_services}},
            ]
        }
    else:
        raise ValueError(f"Unsupported role: {role_name!r}")

    # ── Apply time range filter ───────────────────────────────────────
    if start is not None:
        # Ensure timezone-aware
        if isinstance(start, datetime):
            if start.tzinfo is None:
                start = start.replace(tzinfo=timezone.utc)
            start_str = start.isoformat().replace("+00:00", "Z")
        else:
            start_str = str(start)

        effective_end = end if end is not None else datetime.now(timezone.utc)
        if isinstance(effective_end, datetime):
            if effective_end.tzinfo is None:
                effective_end = effective_end.replace(tzinfo=timezone.utc)
            effective_end_str = effective_end.isoformat().replace("+00:00", "Z")
        else:
            effective_end_str = str(effective_end)

        time_filter: dict[str, Any] = {"$gte": start_str, "$lte": effective_end_str}

        if "$or" in query:
            # Wrap existing $or with $and so we can add timestamp
            query = {"$and": [{"$or": query["$or"]}, {"timestamp": time_filter}]}
        else:
            query["timestamp"] = time_filter

    cursor = collection.find(query, projection).sort("timestamp", -1)
    if limit is not None:
        cursor = cursor.limit(limit)
    results = [_serialize_document(_normalize_document_fields(document)) for document in cursor]
    results.reverse()
    return results


fetchLogs = fetch_logs