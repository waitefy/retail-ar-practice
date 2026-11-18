package retailar;

/** Получает связанные сведения о варианте перед выполнением операции. */
abstract class CatalogOperation<R> implements VariantOperation<R> {
    private final Catalog catalog;

    CatalogOperation(Catalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public final R execute(String variantId) throws CatalogException, VisualizationException {
        return prepareResult(catalog.getVariantDetails(variantId));
    }

    protected abstract R prepareResult(VariantDetails details) throws VisualizationException;
}
