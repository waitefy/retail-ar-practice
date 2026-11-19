package retailar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Проверки наложения оттенка и его запуска из CLI. */
public final class PreviewChecks {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Нужны пути к каталогу и примеру изображения.");
        reference(Path.of(args[0]), Path.of(args[1]));
        rounding();
        copiesAndLimits();
        invalidInput(Path.of(args[0]));
        selection(Path.of(args[0]), Path.of(args[1]));
        System.out.println("Пройдено групп проверок просмотра оттенка и выбора: 5");
    }

    private static void reference(Path catalog, Path input) {
        for (String id : List.of("V001", "V002", "V003")) {
            String output = run(0, "", "--catalog", catalog.toString(), "--variant", id,
                    "--input", input.toString());
            JsonObject data = JsonParser.parseString(output).getAsJsonObject().getAsJsonObject("data");
            String details = run(0, "", "--catalog", catalog.toString(), "--variant", id);
            check(data.get("details").equals(JsonParser.parseString(details).getAsJsonObject().get("data")),
                    "Сведения о варианте совпадают: " + id);
            check(data.get("mask").equals(JsonParser.parseString(
                    "[[true,true,false],[true,true,false],[false,false,false]]")), "Маска общего примера");
            String rgb = id.equals("V002") ? "[118,73,93]" : "[145,95,110]";
            String unmasked = "[100,100,100]";
            String first = "[" + rgb + "," + rgb + "," + unmasked + "]";
            String last = "[" + unmasked + "," + unmasked + "," + unmasked + "]";
            check(data.get("image").equals(JsonParser.parseString("[" + first + "," + first + "," + last + "]")),
                    "Результат по всем пикселям: " + id);
        }
    }

    private static void selection(Path catalog, Path input) {
        for (String id : List.of("V001", "V002", "V003")) {
            String[] args = {"--catalog", catalog.toString(), "--input", input.toString(), "--variant", id};
            JsonObject expected = JsonParser.parseString(run(0, "", args)).getAsJsonObject();
            JsonObject data = expected.getAsJsonObject("data");
            JsonObject selection = data.getAsJsonObject("selection");
            check(selection.get("status").getAsString().equals("no_selection")
                    && selection.get("details").isJsonNull(), "Просмотр без выбора: " + id);

            String[] selectedArgs = Arrays.copyOf(args, args.length + 1);
            selectedArgs[args.length] = "--select";
            JsonObject actual = JsonParser.parseString(run(0, "", selectedArgs)).getAsJsonObject();
            selection.addProperty("status", "selected");
            selection.add("details", data.get("details"));
            check(actual.equals(expected), "Подтверждение выбора меняет только его результат: " + id);
        }
    }

    private static void rounding() {
        checkBlend(1, 36, "0.3", 12);
        checkBlend(0, 45, "0.7", 32);
        checkBlend(5, 0, "0.9", 1);
    }

    private static void checkBlend(int original, int shade, String intensity, int expected) {
        int[][][] image = {{{original, original, original}}};
        boolean[][] mask = {{true}};
        int[] color = {shade, shade, shade};
        int[][][] actual = ShadePreview.applyShade(image, mask, color, new BigDecimal(intensity));
        check(Arrays.equals(actual[0][0], new int[] {expected, expected, expected}), "Округление при " + intensity);
    }

    private static void copiesAndLimits() {
        int[][][] image = {{{1, 2, 3}, {4, 5, 6}}};
        boolean[][] mask = {{true, false}};
        int[] color = {7, 8, 9};
        int[][][] first = ShadePreview.applyShade(image, mask, color, BigDecimal.ZERO);
        check(Arrays.deepEquals(first, image), "Интенсивность 0");
        int[][][] second = ShadePreview.applyShade(image, mask, color, BigDecimal.ONE);
        check(Arrays.deepEquals(second, new int[][][] {{{7, 8, 9}, {4, 5, 6}}}), "Интенсивность 1");
        second[0][0][0] = 50;
        second[0][1][0] = 51;
        check(Arrays.deepEquals(image, new int[][][] {{{1, 2, 3}, {4, 5, 6}}})
                && Arrays.deepEquals(first, image) && Arrays.equals(mask[0], new boolean[] {true, false})
                && Arrays.equals(color, new int[] {7, 8, 9}), "Входы и результаты независимы");
    }

    private static void invalidInput(Path catalog) throws Exception {
        Path input = Files.createTempFile("java-preview-", ".json");
        try {
            expect(catalog, input, "{", "invalid_input");
            expect(catalog, input, "{\"image\":null,\"image\":null,\"contour\":null,\"intensity\":1}", "invalid_input");
            expect(catalog, input, "{\"image\":[]}", "invalid_input");
            expect(catalog, input, source("[]", "null", "1"), "invalid_image");
            expect(catalog, input, source("[[[true,0,0]]]", "null", "1"), "invalid_image");
            expect(catalog, input, source("[[[1.0,0,0]]]", "null", "1"), "invalid_image");
            expect(catalog, input, source("[[[256,0,0]]]", "null", "1"), "invalid_image");
            expect(catalog, input, source("[[[0,0,0]],[]]", "null", "1"), "invalid_image");
            for (String intensity : List.of("true", "\"0.5\"", "1.1", "-0.1", "1e400")) {
                expect(catalog, input, source("[[[0,0,0]]]", "null", intensity), "invalid_intensity");
            }
            expect(catalog, input, source("[[[0,0,0]]]", "null", "1"), "missing_contour");
            expect(catalog, input, source("[[[0,0,0]]]", "[[0,0],[1,0],[true,1]]", "1"), "invalid_contour");
            expect(catalog, input, source("[[[0,0,0]]]", "[[0,0],[0.1,0],[0,0.1]]", "1"), "empty_mask");
            run(1, "Ошибка [variant_not_found]", "--catalog", catalog.toString(), "--variant", "V999",
                    "--input", input.toString(), "--select");
            run(2, "Нужно значение параметра --input", "--input");
        } finally {
            Files.delete(input);
        }
        run(1, "Ошибка [invalid_input]", "--catalog", catalog.toString(), "--input", input.toString(), "--select");
    }

    private static String source(String image, String contour, String intensity) {
        return "{\"image\":" + image + ",\"contour\":" + contour + ",\"intensity\":" + intensity + "}";
    }

    private static void expect(Path catalog, Path input, String source, String code) throws Exception {
        Files.writeString(input, source, StandardCharsets.UTF_8);
        run(1, "Ошибка [" + code + "]", "--catalog", catalog.toString(), "--input", input.toString());
    }

    private static String run(int expected, String diagnostic, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = new Main().run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        String message = err.toString(StandardCharsets.UTF_8);
        check(code == expected, "Код завершения " + Arrays.toString(args) + ": " + code + " " + message);
        check(expected == 0 ? message.isEmpty() && out.size() > 0
                : message.startsWith(diagnostic) && out.size() == 0, "Вывод: " + message);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
