package retailar;

/** Операция над вариантом, который покупатель открыл в каталоге. */
interface VariantOperation<R> {
    R execute(String variantId) throws CatalogException, VisualizationException;
}
