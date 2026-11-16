package retailar;

/** Ожидаемая ошибка чтения каталога или выполнения операции. */
public final class CatalogException extends Exception {
    private static final long serialVersionUID = 1L;
    private final String code;

    public CatalogException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
