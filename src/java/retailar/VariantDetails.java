package retailar;

import java.math.BigInteger;
import java.util.List;

/** Неизменяемый результат операции получения сведений. */
public record VariantDetails(Product product, Variant variant, Shade shade,
                             List<MaterialView> materials) {
    public VariantDetails {
        materials = List.copyOf(materials);
    }

    public record MaterialView(Material material, String displayRole, BigInteger displayOrder) { }
}
