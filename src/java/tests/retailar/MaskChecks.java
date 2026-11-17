package retailar;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Проверки геометрии и маски на примерах процедурной версии. */
public final class MaskChecks {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Нужен путь к mask_example.json.");
        MaskChecks checks = new MaskChecks();
        checks.reference(Path.of(args[0]));
        checks.invalidInput();
        checks.invalidGeometry();
        checks.order();
        checks.boundaries();
        checks.concave();
        checks.emptyAndThin();
        checks.copies();
        System.out.println("Пройдено групп проверок маски: 8");
    }

    private void reference(Path path) throws Exception {
        var source = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        int height = source.getAsJsonArray("image").size();
        int width = source.getAsJsonArray("image").get(0).getAsJsonArray().size();
        List<List<Number>> contour = new ArrayList<>();
        for (JsonElement row : source.getAsJsonArray("contour")) {
            List<Number> point = new ArrayList<>();
            for (JsonElement coordinate : row.getAsJsonArray()) {
                String token = coordinate.getAsString();
                point.add(token.matches("-?(0|[1-9][0-9]*)")
                        ? new BigInteger(token) : Double.valueOf(token));
            }
            contour.add(point);
        }
        check(Arrays.deepEquals(new LipMaskBuilder(height, width, contour).build(), mask("110", "110", "000")),
                "Маска общего примера");
        System.out.println("OK: маска из общего mask_example.json");
    }

    private void invalidInput() throws Exception {
        var square = contour(0, 0, 2, 0, 2, 2, 0, 2);
        expect("invalid_image", () -> new LipMaskBuilder(0, 3, square));
        expect("invalid_image", () -> new LipMaskBuilder(3, -1, square));
        expect("missing_contour", () -> new LipMaskBuilder(3, 3, null));
        expect("missing_contour", () -> new LipMaskBuilder(3, 3, List.of()));
        for (List<List<Number>> bad : List.of(
                contour(0, 0, 1, 1), contour(0, 0, 1, 0, -0.1, 1), contour(0, 0, 1, 0, 4, 1),
                contour(0, 0, 1, 0, Double.NaN, 1), contour(0, 0, 1, 0, 1, Double.POSITIVE_INFINITY),
                contour(0, 0, 1, 0, 0, 1, 0.0, -0.0), contour(0, 0, 1, 0, 1.0, 0.0, 0, 1))) {
            expect("invalid_contour", () -> new LipMaskBuilder(3, 3, bad));
        }
        List<List<Number>> malformed = contour(0, 0, 1, 0, 0, 1);
        malformed.set(2, List.of(1));
        expect("invalid_contour", () -> new LipMaskBuilder(3, 3, malformed));
        malformed.set(2, null);
        expect("invalid_contour", () -> new LipMaskBuilder(3, 3, malformed));
        malformed.set(2, Arrays.asList(null, 1));
        expect("invalid_contour", () -> new LipMaskBuilder(3, 3, malformed));
        System.out.println("OK: размеры, отсутствие контура, координаты и повторные вершины");
    }

    private void invalidGeometry() throws Exception {
        for (List<List<Number>> bad : List.of(
                contour(0, 0, 1, 1, 2, 2),
                contour(0, 0, 3, 3, 0, 3, 2, 0),
                contour(0, 0, 3, 0, 1, 0, 1, 2),
                contour(0, 0, 3, 0, 3, 2, 1, 0, 0, 2),
                contour(1, 0, 3, 0, 3, 2, 0, 2, 0, 0, 2, 0))) {
            expect("invalid_contour", () -> new LipMaskBuilder(3, 3, bad));
            Collections.reverse(bad);
            expect("invalid_contour", () -> new LipMaskBuilder(3, 3, bad));
        }
        System.out.println("OK: площадь, пересечения, самокасания и наложение рёбер");
    }

    private void order() throws Exception {
        var square = contour(0, 0, 2, 0, 2, 2, 0, 2);
        boolean[][] expected = mask("110", "110", "000");
        for (int direction = 0; direction < 2; direction++) {
            for (int offset = 0; offset < square.size(); offset++) {
                check(Arrays.deepEquals(new LipMaskBuilder(3, 3, square).build(), expected), "Порядок вершин");
                Collections.rotate(square, 1);
            }
            Collections.reverse(square);
        }
        System.out.println("OK: направление обхода и начальная вершина");
    }

    private void boundaries() throws Exception {
        boolean[][] full = mask("111", "111", "111");
        check(Arrays.deepEquals(new LipMaskBuilder(3, 3, contour(0.5, 0.5, 2.5, 0.5, 2.5, 2.5, 0.5, 2.5))
                .build(), full), "Граница включена");
        check(Arrays.deepEquals(new LipMaskBuilder(3, 3, contour(0, 0, 1, 0, 3, 0, 3, 3, 0, 3))
                .build(), full), "Коллинеарные рёбра без наложения");
        check(Arrays.deepEquals(new LipMaskBuilder(2, 4, contour(0, 0, 4, 0, 4, 2, 0, 2))
                .build(), mask("1111", "1111")), "Прямоугольные размеры и край изображения");
        System.out.println("OK: граница, горизонтальные рёбра и прямоугольное изображение");
    }

    private void concave() throws Exception {
        List<List<List<Number>>> contours = List.of(contour(0, 0, 3, 0, 0, 3),
                contour(0, 0, 3, 0, 3, 1, 1, 1, 1, 3, 0, 3));
        boolean[][][] expected = {mask("111", "110", "100"), mask("111", "100", "100")};
        for (int i = 0; i < contours.size(); i++) {
            for (int direction = 0; direction < 2; direction++) {
                check(Arrays.deepEquals(new LipMaskBuilder(3, 3, contours.get(i)).build(), expected[i]),
                        "Треугольник или вогнутый контур");
                Collections.reverse(contours.get(i));
            }
        }
        System.out.println("OK: треугольник и вогнутый контур");
    }

    private void emptyAndThin() throws Exception {
        var empty = new LipMaskBuilder(3, 3, contour(0, 0, 0.1, 0, 0.1, 0.1, 0, 0.1));
        expect("empty_mask", empty::build);
        check(Arrays.deepEquals(new LipMaskBuilder(3, 3,
                contour(0, 0.5, 3, 0.5, 3, 0.5000000000000001, 0, 0.5000000000000001)).build(),
                mask("111", "000", "000")), "Узкая область не теряется при округлении");
        System.out.println("OK: пустая маска и очень тонкая область");
    }

    private void copies() throws Exception {
        var source = contour(0, 0, 2, 0, 2, 2, 0, 2);
        var before = source.stream().map(List::copyOf).toList();
        var builder = new LipMaskBuilder(3, 3, source);
        boolean[][] first = builder.build();
        check(source.equals(before), "Исходный контур не изменён");
        first[0][0] = false;
        check(first[1][0], "Строки результата независимы");
        source.getFirst().set(0, 3);
        source.clear();
        check(Arrays.deepEquals(builder.build(), mask("110", "110", "000")),
                "Изменения входа и результата не влияют на повторный вызов");
        System.out.println("OK: защита контура и независимые маски");
    }

    private static List<List<Number>> contour(Number... coordinates) {
        List<List<Number>> points = new ArrayList<>();
        for (int i = 0; i < coordinates.length; i += 2) {
            points.add(new ArrayList<>(Arrays.asList(coordinates[i], coordinates[i + 1])));
        }
        return points;
    }

    private static boolean[][] mask(String... rows) {
        boolean[][] result = new boolean[rows.length][];
        for (int y = 0; y < rows.length; y++) {
            result[y] = new boolean[rows[y].length()];
            for (int x = 0; x < rows[y].length(); x++) result[y][x] = rows[y].charAt(x) == '1';
        }
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expect(String code, Checked action) throws Exception {
        try {
            action.run();
        } catch (VisualizationException error) {
            check(code.equals(error.getCode()) && !error.getMessage().isBlank(),
                    "Ожидалась ошибка " + code + ", получена " + error.getCode());
            return;
        }
        throw new AssertionError("Ожидалась ошибка " + code);
    }

    @FunctionalInterface
    private interface Checked {
        void run() throws Exception;
    }
}
