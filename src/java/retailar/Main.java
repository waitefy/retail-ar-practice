package retailar;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Консольный просмотр и предварительный выбор варианта товара. */
public final class Main {
    public static void main(String[] args) {
        System.exit(new Main().run(args, System.out, System.err));
    }

    public int run(String[] args, PrintStream out, PrintStream err) {
        Path catalogPath = Path.of("src/data/catalog.json");
        Path inputPath = null;
        String variantId = "V001";
        boolean selected = false;
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--help" -> {
                        out.println("java ... retailar.Main [--catalog PATH] [--variant ID] [--input PATH] [--select]");
                        return 0;
                    }
                    case "--select" -> selected = true;
                    case "--catalog", "--variant", "--input" -> {
                        String option = args[i];
                        if (++i == args.length || args[i].startsWith("--")) {
                            err.println("Нужно значение параметра " + option);
                            return 2;
                        }
                        switch (option) {
                            case "--catalog" -> catalogPath = Path.of(args[i]);
                            case "--input" -> inputPath = Path.of(args[i]);
                            default -> variantId = args[i];
                        }
                    }
                    default -> {
                        err.println("Неизвестный параметр: " + args[i]);
                        return 2;
                    }
                }
            }
            if (selected && inputPath == null) inputPath = Path.of("src/data/mask_example.json");
            Catalog catalog = new CatalogLoader().load(catalogPath);
            VariantOperation<?> operation = inputPath == null
                    ? new VariantLookup(catalog) : new ShadePreview(catalog, inputPath, selected);
            Object data = operation.execute(variantId);
            Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .serializeNulls().disableHtmlEscaping().setPrettyPrinting().create();
            JsonObject result = new JsonObject();
            result.addProperty("ok", true);
            result.add("data", gson.toJsonTree(data));
            result.add("error", null);
            out.println(gson.toJson(result));
            return 0;
        } catch (CatalogException error) {
            err.println("Ошибка [" + error.getCode() + "]: " + error.getMessage());
            return 1;
        } catch (VisualizationException error) {
            err.println("Ошибка [" + error.getCode() + "]: " + error.getMessage());
            return 1;
        } catch (InvalidPathException error) {
            err.println("Некорректный путь к файлу.");
            return 2;
        }
    }
}
