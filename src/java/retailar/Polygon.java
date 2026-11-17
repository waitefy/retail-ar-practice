package retailar;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Проверенный простой контур; граница входит в область. */
public final class Polygon {
    private final List<Point> vertices;

    public Polygon(int height, int width, List<? extends List<? extends Number>> contour)
            throws VisualizationException {
        if (height <= 0 || width <= 0) {
            throw new VisualizationException("invalid_image", "Размеры изображения должны быть положительными.");
        }
        if (contour == null || contour.isEmpty()) {
            throw new VisualizationException("missing_contour", "Контур области губ отсутствует.");
        }
        if (contour.size() < 3) {
            throw new VisualizationException("invalid_contour", "Контур должен содержать не менее трёх вершин.");
        }
        List<Point> points = new ArrayList<>();
        Set<Point> unique = new HashSet<>();
        for (List<? extends Number> row : contour) {
            if (row == null || row.size() != 2) {
                throw new VisualizationException("invalid_contour",
                        "Вершина должна содержать две конечные числовые координаты.");
            }
            Point point = new Point(coordinate(row.get(0)), coordinate(row.get(1)));
            if (point.x().signum() < 0 || point.x().compareTo(BigDecimal.valueOf(width)) > 0
                    || point.y().signum() < 0 || point.y().compareTo(BigDecimal.valueOf(height)) > 0) {
                throw new VisualizationException("invalid_contour", "Вершина контура выходит за границы изображения.");
            }
            if (!unique.add(point)) {
                throw new VisualizationException("invalid_contour",
                        "Вершины повторяются; замыкание контура должно быть неявным.");
            }
            points.add(point);
        }
        vertices = List.copyOf(points);
        validateEdges();
    }

    /** Точный тест горизонтальным лучом вправо, включая точки на рёбрах. */
    public boolean contains(BigDecimal x, BigDecimal y) {
        Point point = new Point(x, y);
        boolean inside = false;
        for (int i = 0; i < vertices.size(); i++) {
            Point a = vertices.get(i);
            Point b = vertices.get((i + 1) % vertices.size());
            if (onSegment(a, b, point)) return true;
            if ((a.y().compareTo(y) > 0) != (b.y().compareTo(y) > 0)) {
                int side = cross(a, b, point).signum();
                int direction = b.y().compareTo(a.y());
                if ((side > 0 && direction > 0) || (side < 0 && direction < 0)) inside = !inside;
            }
        }
        return inside;
    }

    private void validateEdges() throws VisualizationException {
        Point origin = new Point(BigDecimal.ZERO, BigDecimal.ZERO);
        BigDecimal area = BigDecimal.ZERO;
        int count = vertices.size();
        for (int i = 0; i < count; i++) {
            area = area.add(cross(origin, vertices.get(i), vertices.get((i + 1) % count)));
        }
        if (area.signum() == 0) {
            throw new VisualizationException("invalid_contour", "Площадь контура равна нулю.");
        }
        for (int i = 0; i < count; i++) {
            Point a = vertices.get(i);
            Point b = vertices.get((i + 1) % count);
            for (int j = i + 1; j < count; j++) {
                Point c = vertices.get(j);
                Point d = vertices.get((j + 1) % count);
                if (j == i + 1) {
                    if (onSegment(a, b, d) || onSegment(c, d, a)) overlappingEdges();
                } else if (i == 0 && j == count - 1) {
                    if (onSegment(a, b, c) || onSegment(c, d, b)) overlappingEdges();
                } else if (segmentsIntersect(a, b, c, d)) {
                    throw new VisualizationException("invalid_contour",
                            "Контур содержит самопересечение или самокасание.");
                }
            }
        }
    }

    private static BigDecimal coordinate(Number value) throws VisualizationException {
        if (value instanceof BigInteger integer) return new BigDecimal(integer);
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(value.longValue());
        }
        if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
            double number = value.doubleValue();
            // Как Fraction(float) в Python: сохраняем точное значение двоичного числа.
            if (Double.isFinite(number)) return new BigDecimal(number);
        }
        throw new VisualizationException("invalid_contour",
                "Вершина должна содержать две конечные числовые координаты.");
    }

    private static BigDecimal cross(Point a, Point b, Point c) {
        return b.x().subtract(a.x()).multiply(c.y().subtract(a.y()))
                .subtract(b.y().subtract(a.y()).multiply(c.x().subtract(a.x())));
    }

    private static boolean onSegment(Point a, Point b, Point point) {
        return cross(a, b, point).signum() == 0
                && a.x().min(b.x()).compareTo(point.x()) <= 0 && point.x().compareTo(a.x().max(b.x())) <= 0
                && a.y().min(b.y()).compareTo(point.y()) <= 0 && point.y().compareTo(a.y().max(b.y())) <= 0;
    }

    private static boolean segmentsIntersect(Point a, Point b, Point c, Point d) {
        int abC = cross(a, b, c).signum();
        int abD = cross(a, b, d).signum();
        int cdA = cross(c, d, a).signum();
        int cdB = cross(c, d, b).signum();
        if (abC * abD < 0 && cdA * cdB < 0) return true;
        return onSegment(a, b, c) || onSegment(a, b, d) || onSegment(c, d, a) || onSegment(c, d, b);
    }

    private static void overlappingEdges() throws VisualizationException {
        throw new VisualizationException("invalid_contour", "Соседние рёбра накладываются друг на друга.");
    }

    private record Point(BigDecimal x, BigDecimal y) {
        private Point {
            // Равные координаты 0, 0.0 и -0.0 должны совпадать и в Set.
            x = x.stripTrailingZeros();
            y = y.stripTrailingZeros();
        }
    }
}
