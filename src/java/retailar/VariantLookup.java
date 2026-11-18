package retailar;

/** Возвращает сведения карточки открытого варианта. */
final class VariantLookup extends CatalogOperation<VariantDetails> {
    VariantLookup(Catalog catalog) {
        super(catalog);
    }

    @Override
    protected VariantDetails prepareResult(VariantDetails details) {
        return details;
    }
}
