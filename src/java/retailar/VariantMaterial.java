package retailar;

import java.math.BigInteger;

/** Связь варианта с материалом, его ролью и порядком показа. */
public record VariantMaterial(String variantId, String materialId,
                              String displayRole, BigInteger displayOrder) { }
