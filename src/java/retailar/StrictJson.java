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

/** Чтение JSON с проверкой повторных ключей и сохранением типов чисел. */
final class StrictJson {
    private StrictJson() {}

    static JsonElement read(String text) throws IOException {
        if (text.startsWith("\ufeff")) {
            throw new IOException("JSON в UTF-8 должен быть без BOM.");
        }
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement value = readValue(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Лишние данные после JSON.");
            return value;
        }
    }

    // Gson проверяет синтаксис; имена проверяются до добавления в JsonObject,
    // чтобы повторное поле не заменило предыдущее без сообщения об ошибке.
    private static JsonElement readValue(JsonReader reader) throws IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject result = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (result.has(key)) throw new IOException("Повтор поля JSON: " + key);
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
                yield new JsonPrimitive(number(token));
            }
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new IOException("Ожидается значение JSON.");
        };
    }

    private static Number number(String token) {
        if (token.matches("-?[0-9]+")) return new BigInteger(token);
        try {
            return new BigDecimal(token);
        } catch (NumberFormatException error) {
            // Показатель степени JSON может выходить за диапазон масштаба BigDecimal.
            return Double.valueOf(token);
        }
    }
}
