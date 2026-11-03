"""Сведения о варианте, наложение оттенка и предварительный выбор в CLI."""

import argparse
from pathlib import Path
import sys

from catalog import get_selection_result, get_variant_details, load_catalog, read_json
from results import failure
from visualization import apply_shade, build_lip_mask, validate_visualization_input


def _report_error(result):
    error = result["error"]
    print(f"Ошибка [{error['code']}]: {error['message']}", file=sys.stderr)
    return 1


def main(argv=None):
    directory = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(
        description="Просмотреть вариант с наложением оттенка и задать предварительный выбор.")
    parser.add_argument("--catalog", type=Path, default=directory / "data" / "catalog.json")
    parser.add_argument("--input", type=Path, default=directory / "data" / "mask_example.json")
    parser.add_argument("--variant", default="V001", help="вариант для просмотра (по умолчанию V001)")
    parser.add_argument("--select", action="store_true",
                        help="предварительно выбрать просматриваемый вариант; без флага выбора нет")
    args = parser.parse_args(argv)
    catalog = load_catalog(args.catalog)
    if not catalog["ok"]:
        return _report_error(catalog)
    details = get_variant_details(catalog["data"], args.variant)
    if not details["ok"]:
        return _report_error(details)
    try:
        source = read_json(args.input)
    except (OSError, ValueError, UnicodeError) as error:
        return _report_error(failure("invalid_input", f"Не удалось прочитать входной JSON: {error}"))
    if not isinstance(source, dict) or set(source) != {"image", "contour", "intensity"}:
        return _report_error(failure("invalid_input", "Нужны поля image, contour и intensity."))
    shade = details["data"]["shade"]
    color = [shade["red"], shade["green"], shade["blue"]]
    validation = validate_visualization_input(source["image"], source["contour"],
                                               color, source["intensity"])
    if not validation["ok"]:
        return _report_error(validation)
    dimensions = validation["data"]
    mask = build_lip_mask(dimensions["height"], dimensions["width"], source["contour"])
    if not mask["ok"]:
        return _report_error(mask)
    overlay = apply_shade(source["image"], mask["data"], color, source["intensity"])
    product = details["data"]["product"]
    variant = details["data"]["variant"]
    print(f"Товар: {product['product_id']} / {product['name']}")
    print(f"Описание: {product['description']}")
    print(f"Страница товара: {product['product_url']}")
    print(f"Вариант: {variant['variant_id']} / {variant['sku']} / {shade['name']}")
    print(f"Масса нетто: {variant['net_weight_g']} г")
    materials = details["data"]["materials"]
    print("Материалы:" if materials else "Материалы: отсутствуют.")
    for link in materials:
        material = link["material"]
        print(f"{link['display_order']}. {material['material_id']} / {material['title']}")
        print(f"   {material['url']}")
    print(f"Изображение: {dimensions['height']} × {dimensions['width']}; "
          f"вершин контура: {dimensions['vertex_count']}")
    print("Маска:")
    for row in mask["data"]:
        print(" ".join("1" if value else "0" for value in row))
    print("Результат наложения (RGB):")
    for row in overlay["data"]:
        print(" ".join(str(pixel) for pixel in row))
    selection = get_selection_result(catalog["data"], args.variant if args.select else None)
    if not selection["ok"]:
        return _report_error(selection)
    print(f"Результат предварительного выбора: {selection['data']['status']}")
    if selection["data"]["status"] == "selected":
        selected = selection["data"]["details"]
        print(f"Предварительно выбран: {selected['product']['product_id']} / "
              f"{selected['variant']['variant_id']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
