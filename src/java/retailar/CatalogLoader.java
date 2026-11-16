package retailar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Строгое чтение JSON и проверка ограничений логической модели. */
public final class CatalogLoader {
    public Catalog load(Path path) throws CatalogException {
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (CharacterCodingException error) {
            throw invalid("Каталог должен быть в UTF-8.");
        } catch (IOException error) {
            throw new CatalogException("catalog_read_error", "Не удалось прочитать каталог: " + path);
        }
        require(!text.startsWith("\ufeff"), "JSON в UTF-8 должен быть без BOM.");
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement value = readValue(reader);
            require(reader.peek() == JsonToken.END_DOCUMENT, "Лишние данные после JSON.");
            return createCatalog(object(value));
        } catch (IOException | IllegalArgumentException | IllegalStateException error) {
            throw invalid("Некорректный JSON каталога: " + error.getMessage());
        }
    }

    // Gson проверяет синтаксис; имена проверяются до добавления в JsonObject,
    // чтобы повторное поле не заменило предыдущее без сообщения об ошибке.
    private JsonElement readValue(JsonReader reader) throws IOException, CatalogException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject result = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    require(!result.has(key), "Повтор поля JSON: " + key);
                    result.add(key, readValue(reader));
                }
                reader.endObject();
                yield result;
            }
            case BEGIN_ARRAY -> {
                JsonArray result = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) result.add(readValue(reader));
                reader.endArray();
                yield result;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> {
                String token = reader.nextString();
                // Целое 1 и дробное 1.0 различаются, как в Python-каталоге.
                Number number = token.matches("-?[0-9]+")
                        ? new BigInteger(token) : new BigDecimal(token);
                yield new JsonPrimitive(number);
            }
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw invalid("Ожидается значение JSON.");
        };
    }

    private Catalog createCatalog(JsonObject root) throws CatalogException {
        fields(root, "products", "variants", "shades", "materials", "variant_materials");
        Map<String, Product> products = new HashMap<>();
        Map<String, Variant> variants = new HashMap<>();
        Map<String, Shade> shades = new HashMap<>();
        Map<String, Material> materials = new HashMap<>();
        List<VariantMaterial> links = new ArrayList<>();

        for (JsonElement value : array(root, "products")) {
            JsonObject row = object(value);
            fields(row, "product_id", "name", "description", "product_url");
            Product product = new Product(text(row, "product_id"), text(row, "name"),
                    text(row, "description"), url(row, "product_url"));
            unique(products, product.productId(), product);
        }
        for (JsonElement value : array(root, "shades")) {
            JsonObject row = object(value);
            fields(row, "shade_id", "name", "red", "green", "blue");
            Shade shade = new Shade(text(row, "shade_id"), text(row, "name"),
                    rgb(row, "red"), rgb(row, "green"), rgb(row, "blue"));
            unique(shades, shade.shadeId(), shade);
        }
        for (JsonElement value : array(root, "materials")) {
            JsonObject row = object(value);
            fields(row, "material_id", "kind", "title", "url");
            Material material = new Material(text(row, "material_id"), text(row, "kind"),
                    text(row, "title"), url(row, "url"));
            unique(materials, material.materialId(), material);
        }
        Set<String> skus = new HashSet<>();
        for (JsonElement value : array(root, "variants")) {
            JsonObject row = object(value);
            fields(row, "variant_id", "product_id", "shade_id", "sku", "net_weight_g");
            Variant variant = new Variant(text(row, "variant_id"), text(row, "product_id"),
                    text(row, "shade_id"), text(row, "sku"), weight(row));
            require(products.containsKey(variant.productId()) && shades.containsKey(variant.shadeId()),
                    "Вариант ссылается на отсутствующий товар или оттенок.");
            require(skus.add(variant.sku()), "Артикулы вариантов должны быть уникальными.");
            unique(variants, variant.variantId(), variant);
        }
        Set<List<String>> keys = new HashSet<>();
        Set<List<Object>> orders = new HashSet<>();
        for (JsonElement value : array(root, "variant_materials")) {
            JsonObject row = object(value);
            fields(row, "variant_id", "material_id", "display_role", "display_order");
            VariantMaterial link = new VariantMaterial(text(row, "variant_id"), text(row, "material_id"),
                    text(row, "display_role"), integer(row, "display_order"));
            require(link.displayOrder().signum() > 0, "Порядок показа должен быть положительным.");
            require(variants.containsKey(link.variantId()) && materials.containsKey(link.materialId()),
                    "Связь ссылается на отсутствующий вариант или материал.");
            require(keys.add(List.of(link.variantId(), link.materialId())), "Повтор ключа связи.");
            require(orders.add(List.of(link.variantId(), link.displayOrder())),
                    "Порядок показа повторяется в пределах варианта.");
            links.add(link);
        }
        return new Catalog(products, variants, shades, materials, links);
    }

    private JsonObject object(JsonElement value) throws CatalogException {
        require(value != null && value.isJsonObject(), "Ожидается объект JSON.");
        return value.getAsJsonObject();
    }

    private JsonArray array(JsonObject root, String name) throws CatalogException {
        require(root.get(name).isJsonArray(), name + ": ожидается список записей.");
        return root.getAsJsonArray(name);
    }

    private void fields(JsonObject row, String... names) throws CatalogException {
        require(row.keySet().equals(Set.of(names)), "Неверный состав полей: " + row.keySet());
    }

    private String text(JsonObject row, String name) throws CatalogException {
        JsonElement value = row.get(name);
        require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), name + ": нужна строка.");
        String result = value.getAsString();
        require(!result.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0x85),
                name + ": нужна непустая строка.");
        return result;
    }

    private String url(JsonObject row, String name) throws CatalogException {
        String result = text(row, name);
        require(result.codePoints().noneMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0x85),
                name + ": пробелы в URL недопустимы.");
        // Как и urlsplit в Python-версии, проверяем схему и наличие узла,
        // не выполняя сетевой запрос и не требуя ASCII от имени узла.
        var match = java.util.regex.Pattern.compile("(?i)^https?://([^/?#]*)").matcher(result);
        require(match.find(), name + ": нужен HTTP(S) URL с именем узла.");
        String authority = match.group(1);
        String host = authority.substring(authority.lastIndexOf('@') + 1);
        try {
            if (authority.contains("[") || authority.contains("]")) {
                int end = host.indexOf(']');
                require(host.startsWith("[") && end > 1, name + ": некорректный адрес в скобках.");
                String address = host.substring(1, end);
                require(address.matches("v[0-9a-fA-F]+\\..+")
                        || URI.create("http://[" + address + "]/").getHost() != null,
                        name + ": некорректный IP-адрес.");
            } else {
                int colon = host.indexOf(':');
                require(!(colon == 0 || host.isEmpty()), name + ": отсутствует имя узла.");
            }
        } catch (IllegalArgumentException error) {
            throw invalid(name + ": некорректный URL.");
        }
        return result;
    }

    private BigInteger integer(JsonObject row, String name) throws CatalogException {
        JsonElement value = row.get(name);
        require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                && value.getAsNumber() instanceof BigInteger, name + ": нужно целое число.");
        return (BigInteger) value.getAsNumber();
    }

    private int rgb(JsonObject row, String name) throws CatalogException {
        BigInteger value = integer(row, name);
        require(value.signum() >= 0 && value.compareTo(BigInteger.valueOf(255)) <= 0,
                name + ": компонента RGB вне диапазона 0..255.");
        return value.intValue();
    }

    private BigDecimal weight(JsonObject row) throws CatalogException {
        JsonElement value = row.get("net_weight_g");
        require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(), "Масса должна быть числом.");
        BigDecimal result = value.getAsBigDecimal();
        require(result.signum() > 0 && (value.getAsNumber() instanceof BigInteger
                || (Double.isFinite(result.doubleValue()) && result.doubleValue() > 0)),
                "Масса должна быть положительной и конечной.");
        return result;
    }

    private <T> void unique(Map<String, T> records, String key, T value) throws CatalogException {
        require(records.putIfAbsent(key, value) == null, "Повтор первичного ключа: " + key);
    }

    private void require(boolean condition, String message) throws CatalogException {
        if (!condition) throw invalid(message);
    }

    private CatalogException invalid(String message) {
        return new CatalogException("invalid_catalog", message);
    }
}
