package retailar;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Консольное получение сведений о варианте товара. */
public final class Main {
    public static void main(String[] args) {
        System.exit(new Main().run(args, System.out, System.err));
    }

    public int run(String[] args, PrintStream out, PrintStream err) {
        Path catalogPath = Path.of("src/data/catalog.json");
        String variantId = "V001";
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--help" -> {
                        out.println("java ... retailar.Main [--catalog PATH] [--variant ID]");
                        return 0;
                    }
                    case "--catalog", "--variant" -> {
                        String option = args[i];
                        if (++i == args.length || args[i].startsWith("--")) {
                            err.println("Нужно значение параметра " + option);
                            return 2;
                        }
                        if (option.equals("--catalog")) catalogPath = Path.of(args[i]);
                        else variantId = args[i];
                    }
                    default -> {
                        err.println("Неизвестный параметр: " + args[i]);
                        return 2;
                    }
                }
            }
            Catalog catalog = new CatalogLoader().load(catalogPath);
            VariantDetails details = catalog.getVariantDetails(variantId);
            Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .serializeNulls().disableHtmlEscaping().setPrettyPrinting().create();
            JsonObject result = new JsonObject();
            result.addProperty("ok", true);
            result.add("data", gson.toJsonTree(details));
            result.add("error", null);
            out.println(gson.toJson(result));
            return 0;
        } catch (CatalogException error) {
            err.println("Ошибка [" + error.getCode() + "]: " + error.getMessage());
            return 1;
        } catch (InvalidPathException error) {
            err.println("Некорректный путь к каталогу.");
            return 2;
        }
    }
}
