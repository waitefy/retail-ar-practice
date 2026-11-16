package retailar;

/** Материал карточки: например, фотография упаковки или палитра. */
public record Material(String materialId, String kind, String title, String url) { }
