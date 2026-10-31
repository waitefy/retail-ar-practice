"""Проверка входов, построение маски и наложение оттенка."""

from fractions import Fraction
from math import isfinite

from geometry import cross, on_segment, point_in_polygon, segments_intersect
from results import failure, success


def _number(value):
    return type(value) is int or (type(value) is float and isfinite(value))


def _rgb(value):
    return (isinstance(value, list) and len(value) == 3
            and all(type(component) is int and 0 <= component <= 255
                    for component in value))


def _contour_error(contour, height, width):
    if not isinstance(contour, list) or len(contour) < 3:
        return "Контур должен содержать не менее трёх вершин."
    vertices = set()
    for point in contour:
        if (not isinstance(point, list) or len(point) != 2
                or not all(_number(value) for value in point)):
            return "Вершина должна содержать две конечные числовые координаты."
        if not (0 <= point[0] <= width and 0 <= point[1] <= height):
            return "Вершина контура выходит за границы изображения."
        if tuple(point) in vertices:
            return "Вершины повторяются; замыкание контура должно быть неявным."
        vertices.add(tuple(point))

    count = len(contour)
    area = sum(cross([0, 0], contour[i], contour[(i + 1) % count])
               for i in range(count))
    if area == 0:
        return "Площадь контура равна нулю."
    for i in range(count):
        a, b = contour[i], contour[(i + 1) % count]
        for j in range(i + 1, count):
            c, d = contour[j], contour[(j + 1) % count]
            if j == i + 1:
                if on_segment(a, b, d) or on_segment(c, d, a):
                    return "Соседние рёбра накладываются друг на друга."
            elif i == 0 and j == count - 1:
                if on_segment(a, b, c) or on_segment(c, d, b):
                    return "Соседние рёбра накладываются друг на друга."
            elif segments_intersect(a, b, c, d):
                return "Контур содержит самопересечение или самокасание."
    return None


def validate_visualization_input(image, contour, color, intensity):
    """Проверяет входы визуализации; возвращает первую ошибку."""
    if (not isinstance(image, list) or not image
            or not isinstance(image[0], list) or not image[0]):
        return failure("invalid_image", "Изображение должно быть непустой матрицей RGB.")
    height, width = len(image), len(image[0])
    for row in image:
        if not isinstance(row, list) or len(row) != width or not all(_rgb(p) for p in row):
            return failure("invalid_image", "Строки изображения или компоненты RGB некорректны.")
    if not _rgb(color):
        return failure("invalid_color", "Цвет должен содержать три целых значения от 0 до 255.")
    if not _number(intensity) or not 0 <= intensity <= 1:
        return failure("invalid_intensity", "Интенсивность должна быть конечным числом от 0 до 1.")
    if contour is None or (isinstance(contour, list) and not contour):
        return failure("missing_contour", "Контур области губ отсутствует.")
    error = _contour_error(contour, height, width)
    if error:
        return failure("invalid_contour", error)
    return success({"height": height, "width": width, "vertex_count": len(contour)})


def build_lip_mask(height, width, contour):
    """Строит маску по центрам пикселей; входы уже проверены."""
    mask = []
    has_pixels = False
    for y in range(height):
        row = []
        for x in range(width):
            center = (Fraction(2 * x + 1, 2), Fraction(2 * y + 1, 2))
            included = point_in_polygon(center, contour)
            row.append(included)
            if included:
                has_pixels = True
        mask.append(row)
    if not has_pixels:
        return failure("empty_mask", "Контур не содержит центров пикселей.")
    return success(mask)


def apply_shade(image, mask, color, intensity):
    """Смешивает RGB по готовой маске; входы уже проверены."""
    height, width = len(image), len(image[0])
    alpha = Fraction(str(intensity))
    half = Fraction(1, 2)
    result = []
    for y in range(height):
        row = []
        for x in range(width):
            pixel = image[y][x].copy()
            if mask[y][x]:
                for channel in range(3):
                    value = ((1 - alpha) * image[y][x][channel]
                             + alpha * color[channel])
                    pixel[channel] = int(value + half)
            row.append(pixel)
        result.append(row)
    return success(result)
