package retailar;

import java.math.BigDecimal;
import java.util.List;

/** Строит маску по центрам пикселей внутри проверенного контура. */
public final class LipMaskBuilder {
    private final int height;
    private final int width;
    private final Polygon polygon;

    public LipMaskBuilder(int height, int width, List<? extends List<? extends Number>> contour)
            throws VisualizationException {
        polygon = new Polygon(height, width, contour);
        this.height = height;
        this.width = width;
    }

    public boolean[][] build() throws VisualizationException {
        boolean[][] mask = new boolean[height][width];
        boolean hasPixels = false;
        BigDecimal half = new BigDecimal("0.5");
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean included = polygon.contains(BigDecimal.valueOf(x).add(half), BigDecimal.valueOf(y).add(half));
                mask[y][x] = included;
                if (included) hasPixels = true;
            }
        }
        if (!hasPixels) {
            throw new VisualizationException("empty_mask", "Контур не содержит центров пикселей.");
        }
        return mask;
    }
}
