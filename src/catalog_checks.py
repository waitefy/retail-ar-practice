"""Проверки каталога и его консольного запуска: только стандартная библиотека."""

from copy import deepcopy
import json
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory

from catalog import get_variant_details, load_catalog, read_json, validate_catalog


def _fixture():
    return read_json(Path(__file__).resolve().parent / "data" / "catalog.json")


def _error(result, code):
    assert result["ok"] is False and result["data"] is None, result
    assert result["error"]["code"] == code, result
    assert result["error"]["message"], result


def check_catalog_and_details():
    catalog = _fixture()
    assert validate_catalog(catalog)["data"] == {
        "products": 1, "variants": 3, "shades": 2, "materials": 3, "variant_materials": 6}
    for variant_id, shade_id, materials in [
        ("V001", "S001", ["M002", "M001"]), ("V002", "S002", ["M002", "M001"]),
        ("V003", "S001", ["M003", "M001"]),
    ]:
        result = get_variant_details(catalog, variant_id)
        assert result["ok"] and result["error"] is None
        details = result["data"]
        assert details["product"]["product_id"] == "P001"
        assert details["variant"]["variant_id"] == variant_id
        assert details["shade"]["shade_id"] == shade_id
        assert [m["material"]["material_id"] for m in details["materials"]] == materials
    catalog["variant_materials"].reverse()
    assert get_variant_details(catalog, "V001")["data"]["materials"][0]["display_order"] == 1


def check_catalog_structure_and_keys():
    catalog = _fixture()
    for bad in [None, [], {}, {**catalog, "extra": []}, {**catalog, "products": {}}]:
        _error(validate_catalog(bad), "invalid_catalog")
    for collection in catalog:
        for replacement in [None, {}, {**catalog[collection][0], "extra": "value"}]:
            bad = deepcopy(catalog)
            bad[collection][0] = replacement
            _error(validate_catalog(bad), "invalid_catalog")
        bad = deepcopy(catalog)
        bad[collection].append(deepcopy(bad[collection][0]))
        _error(validate_catalog(bad), "invalid_catalog")
    for collection, field in [("variants", "product_id"), ("variants", "shade_id"),
                              ("variant_materials", "variant_id"),
                              ("variant_materials", "material_id")]:
        bad = deepcopy(catalog)
        bad[collection][0][field] = "ABSENT"
        _error(validate_catalog(bad), "invalid_catalog")


def check_catalog_constraints():
    catalog = _fixture()
    cases = [
        ("variants", 1, "sku", "DEMO-001"),
        ("variant_materials", 1, "display_order", 1),
        ("variant_materials", 0, "display_order", True),
        ("variant_materials", 0, "display_order", 0),
        ("variants", 0, "net_weight_g", 0), ("variants", 0, "net_weight_g", True),
        ("variants", 0, "net_weight_g", float("inf")),
        ("variants", 0, "net_weight_g", float("nan")),
        ("shades", 0, "red", 256), ("shades", 0, "red", True),
        ("products", 0, "name", " "), ("materials", 0, "url", "not a URL"),
        ("materials", 0, "url", "ftp://example.org/a"),
        ("materials", 0, "url", "https://[bad"),
    ]
    for collection, index, field, value in cases:
        bad = deepcopy(catalog)
        bad[collection][index][field] = value
        _error(validate_catalog(bad), "invalid_catalog")


def check_optional_materials_and_empty_catalog():
    catalog = _fixture()
    catalog["variant_materials"] = []
    assert validate_catalog(catalog)["ok"]
    assert get_variant_details(catalog, "V001")["data"]["materials"] == []
    assert validate_catalog({name: [] for name in catalog})["ok"]


def check_variant_errors_and_copies():
    catalog = _fixture()
    before = deepcopy(catalog)
    for value in [None, "", " ", 1, True, []]:
        _error(get_variant_details(catalog, value), "invalid_variant_id")
    _error(get_variant_details(catalog, "V999"), "variant_not_found")
    result = get_variant_details(catalog, "V001")["data"]
    for name in ("product", "variant", "shade"):
        result[name].clear()
    result["materials"][0]["material"].clear()
    result["materials"][0]["display_order"] = 999
    assert catalog == before


def check_json_loading():
    with TemporaryDirectory() as temp:
        path = Path(temp) / "catalog.json"
        _error(load_catalog(path), "catalog_read_error")
        for text in ['{"products": [], "products": []}', '{"x": NaN}', '{"x": Infinity}', '{']:
            path.write_text(text, encoding="utf-8")
            _error(load_catalog(path), "invalid_catalog")
        path.write_bytes(b"\xff")
        _error(load_catalog(path), "invalid_catalog")
    assert load_catalog(Path(__file__).resolve().parent / "data" / "catalog.json")["ok"]


def check_console():
    program = Path(__file__).resolve().parent / "catalog.py"
    with TemporaryDirectory() as temp:
        for variant_id in ("V001", "V002", "V003"):
            run = subprocess.run([sys.executable, str(program), "--variant", variant_id],
                                 cwd=temp, capture_output=True, text=True)
            assert run.returncode == 0 and not run.stderr, run
            assert json.loads(run.stdout) == get_variant_details(_fixture(), variant_id)
        missing = str(Path(temp) / "missing.json")
        invalid = Path(temp) / "invalid.json"
        invalid.write_text("{}", encoding="utf-8")
        for arguments, code in ((["--variant", "V999"], "variant_not_found"),
                                (["--catalog", missing], "catalog_read_error"),
                                (["--catalog", str(invalid)], "invalid_catalog")):
            run = subprocess.run([sys.executable, str(program), *arguments],
                                 cwd=temp, capture_output=True, text=True)
            assert run.returncode == 1 and not run.stdout, run
            assert f"[{code}]" in run.stderr and "Traceback" not in run.stderr, run
        custom = Path(temp) / "custom.json"
        catalog = _fixture()
        catalog["products"][0]["name"] = "Проверочный товар"
        custom.write_text(json.dumps(catalog, ensure_ascii=False), encoding="utf-8")
        run = subprocess.run([sys.executable, str(program), "--catalog", str(custom)],
                             cwd=temp, capture_output=True, text=True)
        assert run.returncode == 0 and not run.stderr, run
        assert json.loads(run.stdout)["data"]["product"]["name"] == "Проверочный товар"
        run = subprocess.run([sys.executable, str(program), "--unknown"],
                             cwd=temp, capture_output=True, text=True)
        assert run.returncode == 2 and not run.stdout, run


def main():
    checks = (check_catalog_and_details, check_catalog_structure_and_keys,
              check_catalog_constraints, check_optional_materials_and_empty_catalog,
              check_variant_errors_and_copies, check_json_loading, check_console)
    for check in checks:
        check()
        print(f"OK: {check.__name__}")
    print(f"Пройдено групп проверок: {len(checks)}")


if __name__ == "__main__":
    main()
