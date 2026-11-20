"""Сверка Python и Java на общем каталоге и примере визуализации."""

from difflib import unified_diff
import json
import os
from pathlib import Path
import subprocess
import sys

from catalog import get_selection_result, get_variant_details, load_catalog, read_json
from results import success
from visualization import apply_shade, build_lip_mask, validate_visualization_input


def require_data(result, scenario):
    if not result["ok"]:
        raise ValueError(f"{scenario}: Python вернул {result['error']}")
    return result["data"]


def python_preview(catalog, source, details, variant_id, selected):
    shade = details["shade"]
    color = [shade["red"], shade["green"], shade["blue"]]
    image, contour, intensity = source["image"], source["contour"], source["intensity"]
    dimensions = require_data(
        validate_visualization_input(image, contour, color, intensity), variant_id
    )
    mask = require_data(
        build_lip_mask(dimensions["height"], dimensions["width"], contour), variant_id
    )
    result = require_data(apply_shade(image, mask, color, intensity), variant_id)
    selection = require_data(
        get_selection_result(catalog, variant_id if selected else None), variant_id
    )
    return success({"details": details, "mask": mask, "image": result, "selection": selection})


def run_java(command, variant_id, *options):
    return subprocess.run(
        [*command, "--variant", variant_id, *options],
        capture_output=True,
        encoding="utf-8",
        timeout=10,
    )


def compare_result(scenario, expected, completed):
    if completed.returncode != 0 or completed.stderr:
        raise ValueError(f"{scenario}: Java завершилась с кодом {completed.returncode}\n{completed.stderr}")
    try:
        actual = json.loads(completed.stdout)
    except ValueError as error:
        raise ValueError(f"{scenario}: Java вернула некорректный JSON: {error}") from error

    expected_json = json.dumps(expected, ensure_ascii=False, sort_keys=True, indent=2)
    actual_json = json.dumps(actual, ensure_ascii=False, sort_keys=True, indent=2)
    if expected_json != actual_json:
        diff = unified_diff(
            expected_json.splitlines(), actual_json.splitlines(),
            fromfile="Python", tofile="Java", lineterm="",
        )
        raise ValueError(f"{scenario}: результаты различаются\n" + "\n".join(diff))
    return actual["data"]


def check_unknown_variant(catalog, command):
    scenario = "V999, отсутствующий вариант"
    expected = get_variant_details(catalog, "V999")
    actual = run_java(command, "V999")
    if (
        expected["ok"] or expected["error"]["code"] != "variant_not_found"
        or actual.returncode != 1 or actual.stdout
        or not actual.stderr.startswith("Ошибка [variant_not_found]:")
    ):
        raise ValueError(
            f"{scenario}: ожидался variant_not_found\n"
            f"Python: {expected}\nJava: код {actual.returncode}, "
            f"stdout={actual.stdout!r}, stderr={actual.stderr!r}"
        )


def main():
    directory = Path(__file__).resolve().parent
    catalog_path = directory / "data" / "catalog.json"
    input_path = directory / "data" / "mask_example.json"
    java_dir = directory / "java"
    class_file = java_dir / "out" / "retailar" / "Main.class"
    gson = java_dir / "lib" / "gson-2.11.0.jar"
    try:
        if not class_file.is_file() or not gson.is_file():
            raise ValueError("Сначала соберите Java-программу по src/java/README.md.")
        classpath = os.pathsep.join((str(java_dir / "out"), str(gson)))
        command = ["java", "-cp", classpath, "retailar.Main", "--catalog", str(catalog_path)]
        catalog = require_data(load_catalog(catalog_path), "Загрузка каталога")
        source = read_json(input_path)
        for variant_id in ("V001", "V002", "V003"):
            details = get_variant_details(catalog, variant_id)
            data = require_data(details, variant_id)
            compare_result(f"{variant_id}, каталог", details, run_java(command, variant_id))
            for selected in (False, True):
                options = ["--input", str(input_path)]
                if selected:
                    options.append("--select")
                status = "selected" if selected else "no_selection"
                expected = python_preview(catalog, source, data, variant_id, selected)
                actual = compare_result(
                    f"{variant_id}, {status}", expected, run_java(command, variant_id, *options)
                )
                if variant_id == "V002" and actual["image"][0][0] != [118, 73, 93]:
                    raise ValueError(f"V002, {status}: ожидался пиксель [118, 73, 93].")
        check_unknown_variant(catalog, command)
    except (OSError, ValueError, subprocess.TimeoutExpired) as error:
        print(error, file=sys.stderr)
        return 1

    print("Совпали 3 ответа каталога, 6 результатов просмотра и код ошибки для V999.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
