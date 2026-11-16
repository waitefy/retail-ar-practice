# Каталог на Java

Реализована операция «Предоставить сведения о варианте»: загрузка и проверка
каталога, получение товара, варианта, оттенка и материалов в порядке показа.
Данные соответствуют [логической модели](../../models/er_logical.drawio),
классы — [UML-диаграмме](../../models/classes.drawio).

Пять сущностей представлены неизменяемыми классами `record`: их поля закрыты,
доступны методы чтения. `Catalog` хранит коллекции и выполняет операцию,
`CatalogLoader` читает JSON и проверяет ограничения. Список материалов в
результате также неизменяем. Сетевых запросов при запуске программы нет.

## Среда и сборка

Проверено с **OpenJDK 27.0.1.0.3**, `javac 27.0.1.0.3`, на Linux.
Инструмент сборки — `javac`. Единственная зависимость — **Gson 2.11.0**
([проект библиотеки](https://github.com/google/gson)).

Команды выполняются **из корня репозитория**. При первой сборке скачать JAR
из Maven Central; он хранится локально и не включается в Git:

```bash
java -version
javac -version
mkdir -p src/java/lib src/java/out
curl --fail --location https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar -o src/java/lib/gson-2.11.0.jar
echo '57928d6e5a6edeb2abd3770a8f95ba44dce45f3b23b7a9dc2b309c581552a78b  src/java/lib/gson-2.11.0.jar' | sha256sum -c -
javac -encoding UTF-8 -Xlint:all -cp src/java/lib/gson-2.11.0.jar -d src/java/out src/java/retailar/*.java
```

Для сборки нужен доступ к Maven Central либо локальный JAR указанной версии
с тем же SHA-256. После получения зависимости сборка и запуск работают без сети.
В Windows разделитель элементов classpath — `;` вместо `:`.

## Запуск и результат

```bash
java -cp 'src/java/out:src/java/lib/gson-2.11.0.jar' retailar.Main --variant V001
```

По умолчанию читается `src/data/catalog.json` относительно текущего каталога,
выбирается V001. Путь можно задать явно, в том числе абсолютный:

```bash
java -cp 'src/java/out:src/java/lib/gson-2.11.0.jar' retailar.Main --catalog src/data/catalog.json --variant V003
```

Вход — JSON с пятью коллекциями: `products`, `variants`, `shades`, `materials`,
`variant_materials`. Используется один [учебный каталог](../data/catalog.json),
общий с Python-версией. Проверяются поля, значения, PK/FK, уникальность SKU,
составного ключа связи и порядка материалов. Повторные ключи JSON,
NaN/Infinity, дробные RGB и логические значения вместо чисел отклоняются.
Материалы варианта необязательны; пустой каталог допустим.

Вывод — JSON вида `{"ok": true, "data": {...}, "error": null}`. В `data`:

| Вариант | Товар | Оттенок | Масса, г | Материалы по порядку |
| --- | --- | --- | --- | --- |
| V001 | P001 | S001, RGB [190, 90, 120] | 3.5 | M002, M001 |
| V002 | P001 | S002, RGB [135, 45, 85] | 3.5 | M002, M001 |
| V003 | P001 | S001, RGB [190, 90, 120] | 1.5 | M003, M001 |

`data.product`, `data.variant` и `data.shade` содержат полные записи.
Каждый элемент `data.materials` содержит `material` с полной записью,
`display_role` и `display_order`. Названия полей и значения совпадают с выводом:

```bash
python src/catalog.py --variant V001
```

Успех — код завершения `0`. При ошибке выводится сообщение в `stderr`,
`stdout` пуст, код — `1`: `catalog_read_error` (файл недоступен),
`invalid_catalog` (неверные данные), `invalid_variant_id` (пустой идентификатор),
`variant_not_found` (вариант отсутствует). Например:

```bash
java -cp 'src/java/out:src/java/lib/gson-2.11.0.jar' retailar.Main --variant V999
```

Результат: `Ошибка [variant_not_found]: Вариант V999 отсутствует в каталоге.`
Неверные аргументы командной строки дают код `2`; `--help` — справку и код `0`.

Если данные лежат внутри самостоятельной копии Java-проекта в
`src/java/data/catalog.json`, запускать с `--catalog src/java/data/catalog.json`.
Компиляция от Python и расположения файла данных не зависит.

## Проверки

Из корня репозитория, после получения Gson:

```bash
javac -encoding UTF-8 -Xlint:all -cp src/java/lib/gson-2.11.0.jar -d src/java/out src/java/retailar/*.java src/java/tests/retailar/*.java
java -cp 'src/java/out:src/java/lib/gson-2.11.0.jar' retailar.CatalogChecks src/data/catalog.json
```

Семь групп проверяют сведения и порядок материалов, структуру коллекций,
ключи и связи, значения, чтение JSON, неизменяемость результатов и CLI.
Последняя строка: `Пройдено групп проверок каталога: 7`.
Для самостоятельной копии с данными внутри Java-проекта в команде проверок
указывается `src/java/data/catalog.json`.
