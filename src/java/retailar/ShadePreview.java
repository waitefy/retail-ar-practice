package retailar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Строит изображение с оттенком открытого варианта. */
final class ShadePreview extends CatalogOperation<ShadePreview.Result> {
    private final Path inputPath;

    ShadePreview(Catalog catalog, Path inputPath) {
        super(catalog);
        this.inputPath = inputPath;
    }

    @Override
    protected Result prepareResult(VariantDetails details) throws VisualizationException {
        JsonObject source = readInput();
        int[][][] image = readImage(source.get("image"));
        BigDecimal intensity = readIntensity(source.get("intensity"));
        List<List<Number>> contour = readContour(source.get("contour"));
        Shade shade = details.shade();
        int[] color = {shade.red(), shade.green(), shade.blue()};
        boolean[][] mask = new LipMaskBuilder(image.length, image[0].length, contour).build();
        return new Result(details, mask, applyShade(image, mask, color, intensity));
    }

    private JsonObject readInput() throws VisualizationException {
        JsonElement source;
        try {
            source = StrictJson.read(Files.readString(inputPath, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException | IllegalStateException error) {
            throw new VisualizationException("invalid_input", "Не удалось прочитать входной JSON: "
                    + error.getMessage());
        }
        if (!source.isJsonObject() || !source.getAsJsonObject().keySet().equals(Set.of("image", "contour", "intensity"))) {
            throw new VisualizationException("invalid_input", "Нужны поля image, contour и intensity.");
        }
        return source.getAsJsonObject();
    }

    private static int[][][] readImage(JsonElement value) throws VisualizationException {
        if (!value.isJsonArray() || value.getAsJsonArray().isEmpty()
                || !value.getAsJsonArray().get(0).isJsonArray()
                || value.getAsJsonArray().get(0).getAsJsonArray().isEmpty()) {
            throw new VisualizationException("invalid_image", "Изображение должно быть непустой матрицей RGB.");
        }
        var rows = value.getAsJsonArray();
        int width = rows.get(0).getAsJsonArray().size();
        int[][][] image = new int[rows.size()][][];
        for (int y = 0; y < rows.size(); y++) {
            JsonElement row = rows.get(y);
            if (!row.isJsonArray() || row.getAsJsonArray().size() != width) throw invalidPixels();
            image[y] = new int[width][3];
            for (int x = 0; x < width; x++) {
                JsonElement pixel = row.getAsJsonArray().get(x);
                if (!pixel.isJsonArray() || pixel.getAsJsonArray().size() != 3) throw invalidPixels();
                for (int channel = 0; channel < 3; channel++) {
                    Number component = readNumber(pixel.getAsJsonArray().get(channel));
                    if (!(component instanceof BigInteger integer) || integer.signum() < 0
                            || integer.bitLength() > 8) throw invalidPixels();
                    image[y][x][channel] = integer.intValue();
                }
            }
        }
        return image;
    }

    private static VisualizationException invalidPixels() {
        return new VisualizationException("invalid_image", "Строки изображения или компоненты RGB некорректны.");
    }

    private static BigDecimal readIntensity(JsonElement value) throws VisualizationException {
        Number number = readNumber(value);
        double intensity = number == null ? Double.NaN : number.doubleValue();
        if (!Double.isFinite(intensity) || intensity < 0 || intensity > 1) {
            throw new VisualizationException("invalid_intensity",
                    "Интенсивность должна быть конечным числом от 0 до 1.");
        }
        // Как Fraction(str(intensity)) в Python: смешиваем по десятичной записи double.
        return BigDecimal.valueOf(intensity);
    }

    private static List<List<Number>> readContour(JsonElement value) throws VisualizationException {
        if (value.isJsonNull()) return List.of();
        if (!value.isJsonArray()) {
            throw new VisualizationException("invalid_contour", "Контур должен содержать не менее трёх вершин.");
        }
        List<List<Number>> contour = new ArrayList<>();
        for (JsonElement vertex : value.getAsJsonArray()) {
            List<Number> point = new ArrayList<>(2);
            if (vertex.isJsonArray() && vertex.getAsJsonArray().size() == 2) {
                for (JsonElement coordinate : vertex.getAsJsonArray()) point.add(readNumber(coordinate));
            }
            contour.add(point);
        }
        return contour;
    }

    private static Number readNumber(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsNumber() : null;
    }

    /** Смешивает проверенные RGB и возвращает новые пиксели по всему изображению. */
    static int[][][] applyShade(int[][][] image, boolean[][] mask, int[] color, BigDecimal intensity) {
        int height = image.length;
        int width = image[0].length;
        int[][][] result = new int[height][width][];
        BigDecimal originalWeight = BigDecimal.ONE.subtract(intensity);
        BigDecimal[] shadeContribution = new BigDecimal[3];
        for (int channel = 0; channel < 3; channel++) {
            shadeContribution[channel] = intensity.multiply(BigDecimal.valueOf(color[channel]));
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int[] pixel = image[y][x].clone();
                if (mask[y][x]) {
                    for (int channel = 0; channel < 3; channel++) {
                        BigDecimal value = originalWeight.multiply(BigDecimal.valueOf(pixel[channel]))
                                .add(shadeContribution[channel]);
                        pixel[channel] = value.setScale(0, RoundingMode.HALF_UP).intValueExact();
                    }
                }
                result[y][x] = pixel;
            }
        }
        return result;
    }

    record Result(VariantDetails details, boolean[][] mask, int[][][] image) {}
}
