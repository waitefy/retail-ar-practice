"""Загрузка каталога и выдача связанных сведений о варианте."""

import argparse
from copy import deepcopy
import json
from math import isfinite
from pathlib import Path
import sys
from urllib.parse import urlsplit

from results import failure, success


def _unique_object(pairs):
    record = {}
    for key, value in pairs:
        if key in record:
            raise ValueError(f"Повтор поля JSON: {key}")
        record[key] = value
    return record


def _reject_constant(value):
    raise ValueError(f"Недопустимая константа JSON: {value}")


def read_json(path):
    """Читает UTF-8 JSON без повторяющихся полей и NaN/Infinity."""
    return json.loads(Path(path).read_text(encoding="utf-8"),
                      object_pairs_hook=_unique_object,
                      parse_constant=_reject_constant)


def _text(value):
    return isinstance(value, str) and bool(value.strip())


def _url(value):
    if not _text(value) or any(char.isspace() for char in value):
        return False
    try:
        parts = urlsplit(value)
        return parts.scheme in ("https", "http") and bool(parts.hostname)
    except ValueError:
        return False


def validate_catalog(catalog):
    """Проверяет структуру, ключи и ограничения пяти коллекций."""
    fields = {
        "products": {"product_id", "name", "description", "product_url"},
        "variants": {"variant_id", "product_id", "shade_id", "sku", "net_weight_g"},
        "shades": {"shade_id", "name", "red", "green", "blue"},
        "materials": {"material_id", "kind", "title", "url"},
        "variant_materials": {"variant_id", "material_id", "display_role", "display_order"},
    }
    if not isinstance(catalog, dict) or set(catalog) != set(fields):
        return failure("invalid_catalog", "Нужны ровно пять коллекций каталога.")

    keys = {}
    for collection, expected in fields.items():
        records = catalog[collection]
        if not isinstance(records, list):
            return failure("invalid_catalog", f"{collection}: ожидается список записей.")
        seen = set()
        for record in records:
            if not isinstance(record, dict) or set(record) != expected:
                return failure("invalid_catalog", f"{collection}: неверный состав полей.")
            for name, value in record.items():
                if name in ("red", "green", "blue"):
                    valid = type(value) is int and 0 <= value <= 255
                elif name == "net_weight_g":
                    valid = (type(value) in (int, float) and value > 0
                             and (type(value) is int or isfinite(value)))
                elif name == "display_order":
                    valid = type(value) is int and value > 0
                elif name in ("url", "product_url"):
                    valid = _url(value)
                else:
                    valid = _text(value)
                if not valid:
                    return failure("invalid_catalog", f"{collection}.{name}: неверное значение.")
            if collection == "variant_materials":
                key = (record["variant_id"], record["material_id"])
            else:
                key_name = {"products": "product_id", "variants": "variant_id",
                            "shades": "shade_id", "materials": "material_id"}[collection]
                key = record[key_name]
            if key in seen:
                return failure("invalid_catalog", f"{collection}: повтор первичного ключа {key}.")
            seen.add(key)
        keys[collection] = seen

    skus = set()
    for variant in catalog["variants"]:
        if (variant["product_id"] not in keys["products"]
                or variant["shade_id"] not in keys["shades"]):
            return failure("invalid_catalog", "Вариант ссылается на отсутствующий товар или оттенок.")
        if variant["sku"] in skus:
            return failure("invalid_catalog", "Артикулы вариантов должны быть уникальными.")
        skus.add(variant["sku"])

    orders = set()
    for link in catalog["variant_materials"]:
        if (link["variant_id"] not in keys["variants"]
                or link["material_id"] not in keys["materials"]):
            return failure("invalid_catalog", "Связь ссылается на отсутствующий вариант или материал.")
        order = (link["variant_id"], link["display_order"])
        if order in orders:
            return failure("invalid_catalog", "Порядок показа повторяется в пределах варианта.")
        orders.add(order)
    return success({name: len(records) for name, records in catalog.items()})


def load_catalog(path):
    """Возвращает проверенный каталог либо диагностический результат."""
    try:
        catalog = read_json(path)
    except OSError as error:
        return failure("catalog_read_error", f"Не удалось прочитать каталог: {error}")
    except (ValueError, UnicodeError) as error:
        return failure("invalid_catalog", f"Некорректный JSON каталога: {error}")
    result = validate_catalog(catalog)
    if not result["ok"]:
        return result
    return success(catalog)


def get_variant_details(catalog, variant_id):
    """Операция «Предоставить сведения о варианте»; каталог уже проверен."""
    if not _text(variant_id):
        return failure("invalid_variant_id", "Идентификатор варианта должен быть непустой строкой.")
    variant = next((v for v in catalog["variants"] if v["variant_id"] == variant_id), None)
    if variant is None:
        return failure("variant_not_found", f"Вариант {variant_id} отсутствует в каталоге.")
    product = next(p for p in catalog["products"] if p["product_id"] == variant["product_id"])
    shade = next(s for s in catalog["shades"] if s["shade_id"] == variant["shade_id"])
    materials = {m["material_id"]: m for m in catalog["materials"]}
    links = sorted((link for link in catalog["variant_materials"]
                    if link["variant_id"] == variant_id),
                   key=lambda link: link["display_order"])
    return success({
        "product": deepcopy(product),
        "variant": deepcopy(variant),
        "shade": deepcopy(shade),
        "materials": [
            {"material": deepcopy(materials[link["material_id"]]),
             "display_role": link["display_role"], "display_order": link["display_order"]}
            for link in links
        ],
    })


def main(argv=None):
    parser = argparse.ArgumentParser(description="Получить сведения о варианте товара.")
    parser.add_argument("--catalog", type=Path,
                        default=Path(__file__).resolve().parent / "data" / "catalog.json")
    parser.add_argument("--variant", default="V001")
    args = parser.parse_args(argv)
    result = load_catalog(args.catalog)
    if result["ok"]:
        result = get_variant_details(result["data"], args.variant)
    if not result["ok"]:
        error = result["error"]
        print(f"Ошибка [{error['code']}]: {error['message']}", file=sys.stderr)
        return 1
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
