package retailar;

/** Товар каталога. Запись неизменяема. */
public record Product(String productId, String name, String description, String productUrl) { }
