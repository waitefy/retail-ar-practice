package retailar;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Проверки каталога без дополнительной библиотеки тестирования. */
public final class CatalogChecks {
    private final CatalogLoader loader = new CatalogLoader();
    private final String fixture;
    private final Path temp;

    private CatalogChecks(Path path, Path temp) throws Exception {
        fixture = Files.readString(path, StandardCharsets.UTF_8);
        this.temp = temp;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Нужен путь к catalog.json.");
        Path temp = Files.createTempDirectory("java-catalog-checks-");
        try {
            CatalogChecks checks = new CatalogChecks(Path.of(args[0]), temp);
            checks.details();
            checks.structure();
            checks.keys();
            checks.values();
            checks.reading();
            checks.immutability();
            checks.console();
            System.out.println("Пройдено групп проверок каталога: 7");
        } finally {
            try (var files = Files.list(temp)) {
                for (Path file : files.toList()) Files.delete(file);
            }
            Files.delete(temp);
        }
    }

    private JsonObject fixture() {
        return JsonParser.parseString(fixture).getAsJsonObject();
    }

    private Catalog load(String text) throws Exception {
        Path path = temp.resolve("input.json");
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return loader.load(path);
    }

    private void details() throws Exception {
        Catalog catalog = load(fixture);
        for (String id : List.of("V001", "V002", "V003")) {
            VariantDetails result = catalog.getVariantDetails(id);
            check(result.product().productId().equals("P001"), "Товар");
            check(result.variant().variantId().equals(id), "Вариант");
            check(result.shade().shadeId().equals(id.equals("V002") ? "S002" : "S001"), "Оттенок");
            check(result.materials().stream().map(v -> v.material().materialId()).toList()
                    .equals(List.of(id.equals("V003") ? "M003" : "M002", "M001")), "Материалы");
        }
        JsonObject reversed = fixture();
        List<com.google.gson.JsonElement> links = new ArrayList<>();
        reversed.getAsJsonArray("variant_materials").forEach(links::add);
        Collections.reverse(links);
        JsonArray array = new JsonArray();
        links.forEach(array::add);
        reversed.add("variant_materials", array);
        check(load(reversed.toString()).getVariantDetails("V001").materials().getFirst()
                .displayOrder().intValueExact() == 1, "Порядок материалов");
        JsonObject optional = fixture();
        optional.add("variant_materials", new JsonArray());
        check(load(optional.toString()).getVariantDetails("V001").materials().isEmpty(), "Материалы необязательны");
        JsonObject empty = fixture();
        for (String key : List.copyOf(empty.keySet())) empty.add(key, new JsonArray());
        Catalog emptyCatalog = load(empty.toString());
        expect("variant_not_found", () -> emptyCatalog.getVariantDetails("V001"));
        for (String id : new String[] {null, "", " ", "\u00a0", "\u0085"}) {
            expect("invalid_variant_id", () -> catalog.getVariantDetails(id));
        }
        expect("variant_not_found", () -> catalog.getVariantDetails("V999"));
        System.out.println("OK: сведения, сортировка, необязательные материалы и идентификаторы");
    }

    private void structure() throws Exception {
        for (String bad : List.of("null", "[]", "{}")) expect("invalid_catalog", () -> load(bad));
        JsonObject extra = fixture();
        extra.addProperty("extra", 1);
        expect("invalid_catalog", () -> load(extra.toString()));
        for (String key : fixture().keySet()) {
            JsonObject missing = fixture();
            missing.remove(key);
            expect("invalid_catalog", () -> load(missing.toString()));
            JsonObject wrongCollection = fixture();
            wrongCollection.add(key, new JsonObject());
            expect("invalid_catalog", () -> load(wrongCollection.toString()));
            for (String badRow : List.of("null", "[]", "{}", "{\"extra\":1}")) {
                JsonObject wrongRow = fixture();
                wrongRow.getAsJsonArray(key).set(0, JsonParser.parseString(badRow));
                expect("invalid_catalog", () -> load(wrongRow.toString()));
            }
        }
        System.out.println("OK: структура пяти коллекций");
    }

    private void keys() throws Exception {
        for (String key : fixture().keySet()) {
            JsonObject duplicate = fixture();
            JsonArray rows = duplicate.getAsJsonArray(key);
            rows.add(rows.get(0).deepCopy());
            expect("invalid_catalog", () -> load(duplicate.toString()));
        }
        for (String[] pair : List.of(new String[] {"variants", "product_id"},
                new String[] {"variants", "shade_id"}, new String[] {"variant_materials", "variant_id"},
                new String[] {"variant_materials", "material_id"})) {
            invalidField(pair[0], 0, pair[1], "\"ABSENT\"");
        }
        invalidField("variants", 1, "sku", "\"DEMO-001\"");
        invalidField("variant_materials", 1, "display_order", "1");
        System.out.println("OK: PK, FK, SKU и уникальность порядка показа");
    }

    private void values() throws Exception {
        for (String bad : List.of("true", "null", "\"10\"", "-1", "256", "1.0", "1e0")) {
            invalidField("shades", 0, "red", bad);
        }
        for (String bad : List.of("true", "null", "\"1\"", "0", "-1", "1.0")) {
            invalidField("variant_materials", 0, "display_order", bad);
        }
        for (String bad : List.of("true", "null", "\"1\"", "0", "-1", "1e999", "1e-999")) {
            invalidField("variants", 0, "net_weight_g", bad);
        }
        for (String bad : List.of("\"\"", "\" \"", "\"\\u00a0\"", "\"\\u0085\"", "1", "true", "null")) {
            invalidField("products", 0, "name", bad);
        }
        for (String bad : List.of("\"not a URL\"", "\"ftp://example.org/a\"", "\"https://[bad\"",
                "\"https:///a\"", "\"https://example.org/a b\"")) {
            invalidField("materials", 0, "url", bad);
        }
        JsonObject hugeOrder = fixture();
        hugeOrder.getAsJsonArray("variant_materials").get(1).getAsJsonObject()
                .addProperty("display_order", new java.math.BigInteger("999999999999999999999"));
        check(load(hugeOrder.toString()).getVariantDetails("V001").materials().size() == 2,
                "Большой положительный порядок показа");
        for (String url : List.of("https://пример.рф/a", "https://my_host/a", "https://[::1]/a")) {
            JsonObject custom = fixture();
            custom.getAsJsonArray("products").get(0).getAsJsonObject().addProperty("product_url", url);
            check(load(custom.toString()).getVariantDetails("V001").product().productUrl().equals(url),
                    "URL: схема и имя узла");
        }
        System.out.println("OK: значения, числовые типы, диапазоны и URL");
    }

    private void invalidField(String collection, int index, String field, String value) throws Exception {
        JsonObject bad = fixture();
        bad.getAsJsonArray(collection).get(index).getAsJsonObject().add(field, JsonParser.parseString(value));
        expect("invalid_catalog", () -> load(bad.toString()));
    }

    private void reading() throws Exception {
        expect("catalog_read_error", () -> loader.load(temp.resolve("missing.json")));
        for (String bad : List.of("{", fixture + " {}", "{\"products\":[],\"products\":[]}",
                fixture.replace("\"red\": 190", "\"red\": 190, \"red\": 191"),
                fixture.replace("3.5", "NaN"), fixture.replace("3.5", "Infinity"),
                fixture.replace("3.5", "-Infinity"), "/* comment */" + fixture, "\ufeff" + fixture)) {
            expect("invalid_catalog", () -> load(bad));
        }
        Path invalidUtf8 = temp.resolve("invalid-utf8.json");
        Files.write(invalidUtf8, new byte[] {(byte) 0xff});
        expect("invalid_catalog", () -> loader.load(invalidUtf8));
        System.out.println("OK: чтение UTF-8, синтаксис JSON и повторные поля");
    }

    private void immutability() throws Exception {
        Catalog catalog = load(fixture);
        VariantDetails first = catalog.getVariantDetails("V001");
        try {
            first.materials().clear();
            throw new AssertionError("Список материалов изменяем");
        } catch (UnsupportedOperationException expected) {
            check(catalog.getVariantDetails("V001").equals(first), "Каталог не изменился");
        }
        List<VariantDetails.MaterialView> source = new ArrayList<>(first.materials());
        VariantDetails copy = new VariantDetails(first.product(), first.variant(), first.shade(), source);
        source.clear();
        check(copy.materials().size() == 2, "Защитная копия списка");
        System.out.println("OK: неизменяемость результата и защитное копирование");
    }

    private void console() throws Exception {
        Path path = temp.resolve("cli.json");
        Files.writeString(path, fixture, StandardCharsets.UTF_8);
        for (String id : List.of("V001", "V002", "V003")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int code = new Main().run(new String[] {"--catalog", path.toString(), "--variant", id},
                    new PrintStream(out, true, StandardCharsets.UTF_8),
                    new PrintStream(err, true, StandardCharsets.UTF_8));
            check(code == 0 && err.size() == 0, "Успех CLI");
            JsonObject result = JsonParser.parseString(out.toString(StandardCharsets.UTF_8)).getAsJsonObject();
            check(result.get("ok").getAsBoolean() && result.get("error").isJsonNull(), "Формат ответа");
            check(result.getAsJsonObject("data").getAsJsonObject("variant")
                    .get("variant_id").getAsString().equals(id), "Вариант CLI");
        }
        for (String[] args : List.of(new String[] {"--unknown"}, new String[] {"--variant"},
                new String[] {"--catalog", "--variant", "V001"})) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            check(new Main().run(args, new PrintStream(out), new PrintStream(new ByteArrayOutputStream())) == 2
                    && out.size() == 0, "Ошибка аргументов CLI");
        }
        for (String id : List.of("V999", "")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            check(new Main().run(new String[] {"--catalog", path.toString(), "--variant", id},
                    new PrintStream(out), new PrintStream(err)) == 1 && out.size() == 0
                    && err.toString().contains(id.isEmpty() ? "invalid_variant_id" : "variant_not_found"),
                    "Диагностика CLI");
        }
        System.out.println("OK: консольный запуск и коды завершения");
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private void expect(String code, CheckedAction action) throws Exception {
        try {
            action.run();
        } catch (CatalogException error) {
            check(error.getCode().equals(code) && !error.getMessage().isBlank(), "Код ошибки: " + error);
            return;
        }
        throw new AssertionError("Ожидалась ошибка " + code);
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
