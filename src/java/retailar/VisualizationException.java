package retailar;

/** Ожидаемая ошибка входных данных визуализации или построения маски. */
public final class VisualizationException extends Exception {
    private static final long serialVersionUID = 1L;
    private final String code;

    public VisualizationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
