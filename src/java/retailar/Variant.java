package retailar;

import java.math.BigDecimal;

/** Исполнение товара с собственным артикулом и массой. */
public record Variant(String variantId, String productId, String shadeId,
                      String sku, BigDecimal netWeightG) { }
