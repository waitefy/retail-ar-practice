"""Точные геометрические предикаты для переданных числовых координат."""

from fractions import Fraction


def cross(a, b, c):
    """Ориентированная удвоенная площадь треугольника ABC."""
    ax, ay = Fraction(a[0]), Fraction(a[1])
    bx, by = Fraction(b[0]), Fraction(b[1])
    cx, cy = Fraction(c[0]), Fraction(c[1])
    return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)


def on_segment(a, b, point):
    return (cross(a, b, point) == 0
            and min(a[0], b[0]) <= point[0] <= max(a[0], b[0])
            and min(a[1], b[1]) <= point[1] <= max(a[1], b[1]))


def segments_intersect(a, b, c, d):
    ab_c, ab_d = cross(a, b, c), cross(a, b, d)
    cd_a, cd_b = cross(c, d, a), cross(c, d, b)
    if ((ab_c < 0 < ab_d or ab_d < 0 < ab_c)
            and (cd_a < 0 < cd_b or cd_b < 0 < cd_a)):
        return True
    return (on_segment(a, b, c) or on_segment(a, b, d)
            or on_segment(c, d, a) or on_segment(c, d, b))


def point_in_polygon(point, contour):
    """Граница включена; горизонтальный луч направлен вправо."""
    inside = False
    for index in range(len(contour)):
        a = contour[index]
        b = contour[(index + 1) % len(contour)]
        if on_segment(a, b, point):
            return True
        # Разные стороны строгого сравнения дают полуоткрытый интервал по Y.
        if (a[1] > point[1]) != (b[1] > point[1]):
            side = cross(a, b, point)
            # Знак учитывает направление ребра; деление координат не нужно.
            if (side > 0 and b[1] > a[1]) or (side < 0 and b[1] < a[1]):
                inside = not inside
    return inside
