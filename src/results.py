"""Единый формат результатов операций."""


def success(data):
    return {"ok": True, "data": data, "error": None}


def failure(code, message):
    return {"ok": False, "data": None,
            "error": {"code": code, "message": message}}
