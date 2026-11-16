package retailar;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Проверенный каталог. Ссылки между записями разрешаются по идентификаторам. */
public final class Catalog {
    private final Map<String, Product> products;
    private final Map<String, Variant> variants;
    private final Map<String, Shade> shades;
    private final Map<String, Material> materials;
    private final List<VariantMaterial> variantMaterials;

    Catalog(Map<String, Product> products, Map<String, Variant> variants,
            Map<String, Shade> shades, Map<String, Material> materials,
            List<VariantMaterial> variantMaterials) {
        this.products = Map.copyOf(products);
        this.variants = Map.copyOf(variants);
        this.shades = Map.copyOf(shades);
        this.materials = Map.copyOf(materials);
        this.variantMaterials = List.copyOf(variantMaterials);
    }

    /** Операция «Предоставить сведения о варианте». */
    public VariantDetails getVariantDetails(String variantId) throws CatalogException {
        if (variantId == null || variantId.codePoints()
                .allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0x85)) {
            throw new CatalogException("invalid_variant_id",
                    "Идентификатор варианта должен быть непустой строкой.");
        }
        Variant variant = variants.get(variantId);
        if (variant == null) {
            throw new CatalogException("variant_not_found",
                    "Вариант " + variantId + " отсутствует в каталоге.");
        }
        List<VariantDetails.MaterialView> views = variantMaterials.stream()
                .filter(link -> link.variantId().equals(variantId))
                .sorted(Comparator.comparing(VariantMaterial::displayOrder))
                .map(link -> new VariantDetails.MaterialView(materials.get(link.materialId()),
                        link.displayRole(), link.displayOrder()))
                .toList();
        return new VariantDetails(products.get(variant.productId()), variant,
                shades.get(variant.shadeId()), views);
    }
}
