"""Проверки каталога, геометрии и консольного примера без сторонних библиотек."""

from copy import deepcopy
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory

from catalog import read_json
from catalog_checks import (
    check_catalog_and_details,
    check_catalog_structure_and_keys,
    check_catalog_constraints,
    check_optional_materials_and_empty_catalog,
    check_variant_errors_and_copies,
    check_json_loading,
    check_console as check_catalog_console,
)
from visualization import apply_shade, build_lip_mask, validate_visualization_input


def _fixture():
    directory = Path(__file__).resolve().parent / "data"
    return read_json(directory / "catalog.json"), read_json(directory / "mask_example.json")


def _validate(image=None, contour=None, color=None, intensity=0.5):
    _, source = _fixture()
    return validate_visualization_input(
        source["image"] if image is None else image,
        source["contour"] if contour is None else contour,
        [190, 90, 120] if color is None else color, intensity)


def _error(result, code):
    assert result["ok"] is False and result["data"] is None, result
    assert result["error"]["code"] == code, result
    assert result["error"]["message"], result


def check_image_validation():
    for image in [[], {}, [[]], [[[]]], [[[1, 2]]], [[[1, 2, 256]]],
                  [[[True, 2, 3]]], [[[1.0, 2, 3]]], [[[1, 2, float("nan")]]],
                  [[[1, 2, 3]], []], [[[1, 2, 3]], None]]:
        _error(_validate(image=image), "invalid_image")
    _, source = _fixture()
    _error(validate_visualization_input(None, source["contour"], [1, 2, 3], 0), "invalid_image")


def check_color_intensity_and_error_order():
    for color in [[], [1, 2], [1, 2, 256], [True, 2, 3], [1.0, 2, 3], (1, 2, 3)]:
        _error(_validate(color=color), "invalid_color")
    for intensity in [True, False, -0.1, 1.1, None, "0.5", float("nan"), float("inf"), 10**400]:
        _error(_validate(intensity=intensity), "invalid_intensity")
    assert _validate(intensity=0)["ok"] and _validate(intensity=1)["ok"]
    _error(validate_visualization_input([], None, [], -1), "invalid_image")
    _error(validate_visualization_input([[[0, 0, 0]]], None, [], -1), "invalid_color")
    _error(validate_visualization_input([[[0, 0, 0]]], None, [0, 0, 0], -1), "invalid_intensity")
    _error(validate_visualization_input([[[0, 0, 0]]], None, [0, 0, 0], 0), "missing_contour")


def check_missing_and_malformed_contours():
    _, source = _fixture()
    for contour in [None, []]:
        _error(validate_visualization_input(source["image"], contour, [1, 2, 3], 0), "missing_contour")
    for contour in ["polygon", [[0, 0], [1, 1]], [[0, 0], [1, 0], [1]],
                    [[0, 0], [1, 0], [True, 1]], [[0, 0], [1, 0], [float("nan"), 1]],
                    [[0, 0], [1, 0], [1, float("inf")]], [[0, 0], [1, 0], [-0.1, 1]],
                    [[0, 0], [1, 0], [4, 1]], [[0, 0], [1, 0], [0, 1], [0, 0]],
                    [[0, 0], [1, 0], [1, 0], [0, 1]]]:
        _error(_validate(contour=contour), "invalid_contour")


def check_invalid_geometry():
    contours = [
        [[0, 0], [1, 1], [2, 2]],  # Нулевая площадь.
        [[0, 0], [3, 3], [0, 3], [2, 0]],  # Пересечение, площадь не нулевая.
        [[0, 0], [3, 0], [1, 0], [1, 2]],  # Наложение соседних рёбер.
        [[0, 0], [3, 0], [3, 2], [1, 0], [0, 2]],  # Касание несоседнего ребра.
        [[1, 0], [3, 0], [3, 2], [0, 2], [0, 0], [2, 0]],  # Замыкающее ребро.
    ]
    for contour in contours:
        _error(_validate(contour=contour), "invalid_contour")
        _error(_validate(contour=list(reversed(contour))), "invalid_contour")


def check_reference_mask_and_immutability():
    _, source = _fixture()
    before = deepcopy(source)
    color = [190, 90, 120]
    result = validate_visualization_input(source["image"], source["contour"],
                                          color, source["intensity"])
    assert result["data"] == {"height": 3, "width": 3, "vertex_count": 4}
    mask = build_lip_mask(3, 3, source["contour"])
    assert mask["data"] == [[True, True, False], [True, True, False], [False, False, False]]
    assert source == before
    assert color == [190, 90, 120]
    mask["data"][0][0] = False
    assert mask["data"][1][0] is True  # Строки маски не разделяют один список.


def check_winding_and_start_vertex():
    _, source = _fixture()
    contour = source["contour"]
    expected = build_lip_mask(3, 3, contour)["data"]
    for vertices in [contour, list(reversed(contour))]:
        for offset in range(len(vertices)):
            rotated = vertices[offset:] + vertices[:offset]
            assert _validate(contour=rotated)["ok"]
            assert build_lip_mask(3, 3, rotated)["data"] == expected


def check_boundaries_and_horizontal_edges():
    contour = [[0.5, 0.5], [2.5, 0.5], [2.5, 2.5], [0.5, 2.5]]
    assert _validate(contour=contour)["ok"]
    assert build_lip_mask(3, 3, contour)["data"] == [[True] * 3 for _ in range(3)]
    contour = [[0, 0], [1, 0], [3, 0], [3, 3], [0, 3]]
    assert _validate(contour=contour)["ok"]  # Коллинеарные рёбра без наложения.
    assert build_lip_mask(3, 3, contour)["data"] == [[True] * 3 for _ in range(3)]


def check_triangle_and_concave_polygon():
    for contour, expected in [
        ([[0, 0], [3, 0], [0, 3]], [[True, True, True], [True, True, False], [True, False, False]]),
        ([[0, 0], [3, 0], [3, 1], [1, 1], [1, 3], [0, 3]],
         [[True, True, True], [True, False, False], [True, False, False]]),
    ]:
        assert _validate(contour=contour)["ok"]
        for vertices in [contour, list(reversed(contour))]:
            assert build_lip_mask(3, 3, vertices)["data"] == expected


def check_empty_and_very_thin_regions():
    contour = [[0, 0], [0.1, 0], [0.1, 0.1], [0, 0.1]]
    assert _validate(contour=contour)["ok"]
    _error(build_lip_mask(3, 3, contour), "empty_mask")
    contour = [[0, 0.5], [3, 0.5], [3, 0.5000000000000001], [0, 0.5000000000000001]]
    assert _validate(contour=contour)["ok"]
    assert build_lip_mask(3, 3, contour)["data"] == [[True] * 3, [False] * 3, [False] * 3]


def check_console_example():
    demo = Path(__file__).resolve().with_name("overlay_demo.py")
    with TemporaryDirectory() as temp:
        result = subprocess.run([sys.executable, str(demo)], cwd=temp, capture_output=True, text=True)
        assert result.returncode == 0 and result.stderr == "", result
        assert result.stdout == ("Вариант: V001 / DEMO-001 / Розовый\n"
                                 "Изображение: 3 × 3; вершин контура: 4\n"
                                 "Маска:\n1 1 0\n1 1 0\n0 0 0\n"
                                 "Результат наложения (RGB):\n"
                                 "[145, 95, 110] [145, 95, 110] [100, 100, 100]\n"
                                 "[145, 95, 110] [145, 95, 110] [100, 100, 100]\n"
                                 "[100, 100, 100] [100, 100, 100] [100, 100, 100]\n")
        for arguments, code in [(["--variant", "V999"], "variant_not_found"),
                                (["--input", str(Path(temp) / "missing.json")], "invalid_input")]:
            result = subprocess.run([sys.executable, str(demo), *arguments],
                                    cwd=temp, capture_output=True, text=True)
            assert result.returncode == 1 and result.stdout == "", result
            assert f"[{code}]" in result.stderr and "Traceback" not in result.stderr, result

        invalid = Path(temp) / "invalid.json"
        invalid.write_text('{"image": [], "contour": [], "intensity": 0.5}', encoding="utf-8")
        result = subprocess.run([sys.executable, str(demo), "--input", str(invalid)],
                                cwd=temp, capture_output=True, text=True)
        assert result.returncode == 1 and result.stdout == "", result
        assert "[invalid_image]" in result.stderr and "Traceback" not in result.stderr, result


def check_overlay_reference_and_copies():
    _, source = _fixture()
    before = deepcopy(source)
    mask = build_lip_mask(3, 3, source["contour"])["data"]
    mask_before = deepcopy(mask)
    color = [190, 90, 120]
    result = apply_shade(source["image"], mask, color, 0.5)
    assert result["ok"] and result["error"] is None
    assert result["data"] == [
        [[145, 95, 110], [145, 95, 110], [100, 100, 100]],
        [[145, 95, 110], [145, 95, 110], [100, 100, 100]],
        [[100, 100, 100], [100, 100, 100], [100, 100, 100]],
    ]
    result["data"][0][0][0] = 0
    result["data"][2][2][0] = 0
    assert result["data"][0][1] == [145, 95, 110]
    assert result["data"][2][1] == [100, 100, 100]
    assert source == before and mask == mask_before and color == [190, 90, 120]


def check_overlay_intensity_and_rounding():
    image = [[[0, 1, 254], [10, 20, 30]]]
    mask = [[True, False]]
    color = [255, 0, 255]
    assert apply_shade(image, mask, color, 0)["data"] == image
    assert apply_shade(image, mask, color, 1)["data"] == [[[255, 0, 255], [10, 20, 30]]]
    assert apply_shade(image, mask, color, 0.5)["data"] == [[[128, 1, 255], [10, 20, 30]]]
    assert image == [[[0, 1, 254], [10, 20, 30]]] and color == [255, 0, 255]
    for original, shade, intensity, expected in [(1, 36, 0.3, 12),
                                                (0, 45, 0.7, 32),
                                                (5, 0, 0.9, 1)]:
        result = apply_shade([[[original] * 3]], [[True]], [shade] * 3, intensity)
        assert result["data"] == [[[expected] * 3]]


def check_shade_comparison_uses_original():
    _, source = _fixture()
    image = source["image"]
    before = deepcopy(image)
    mask = build_lip_mask(3, 3, source["contour"])["data"]
    first = apply_shade(image, mask, [190, 90, 120], 0.5)["data"]
    second = apply_shade(image, mask, [135, 45, 85], 0.5)["data"]
    assert first[0][0] == [145, 95, 110]
    assert second[0][0] == [118, 73, 93]
    assert image == before


def main():
    checks = (check_catalog_and_details, check_catalog_structure_and_keys,
              check_catalog_constraints, check_optional_materials_and_empty_catalog,
              check_variant_errors_and_copies, check_json_loading, check_catalog_console,
              check_image_validation,
              check_color_intensity_and_error_order, check_missing_and_malformed_contours,
              check_invalid_geometry, check_reference_mask_and_immutability,
              check_winding_and_start_vertex, check_boundaries_and_horizontal_edges,
              check_triangle_and_concave_polygon, check_empty_and_very_thin_regions,
              check_overlay_reference_and_copies, check_overlay_intensity_and_rounding,
              check_shade_comparison_uses_original, check_console_example)
    for check in checks:
        check()
        print(f"OK: {check.__name__}")
    print(f"Пройдено групп проверок: {len(checks)}")


if __name__ == "__main__":
    main()
