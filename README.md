## Features

* sql query dsl: with-db, select(-deep, -with-db), update, insert, delete.
* access to fields of fk-referenced tables through select result (select-deep, get-field-from-row).
* generating source files of table names, columns infos and constants from db, which allows autocomplete these entities in the repl and clojre-mode in emacs.
* java interoperability: map->object, object-to-map, table insert.

## Examples

We have db spec:

```clj
user> (def db {:classname "com.mysql.jdbc.Driver"
                           :subprotocol "mysql"
                           :subname (str "//localhost:3306/clj_query")
                           :user "clj"
                           :password "clj"})
```

Generating tables and columns info file and constants from "recordtypes" table

```clj
user> (require '[ libs.db.gentablecolumns :as gen ])
nil
user> (require '[ libs.db.gencolumnvalues :as genval ])
nil
user> (gen/generate-table-column-names db)
nil
user> (genval/generate-column-value-constants db table-recordtypes (:name recordtypes-name))
nil
```

After that files src/db/entities.clj and db/recordtypes will be generated. In my example case:

entities.clj:

```clj
(ns db.entities)

;;;; players
(def table-players "players")
(def players-id {:type {:size 10, :name "INT UNSIGNED"}, :table "players", :name "id"})
(def players-name {:type {:size 255, :name "VARCHAR"}, :table "players", :name "name"})
(def players-type_id {:type {:size 10, :name "INT UNSIGNED"}, :table "players", :name "type_id"})

;;;; playertypes
(def table-playertypes "playertypes")
(def playertypes-id {:type {:size 10, :name "INT UNSIGNED"}, :table "playertypes", :name "id"})
(def playertypes-name {:type {:size 255, :name "VARCHAR"}, :table "playertypes", :name "name"})

;;;; records
(def table-records "records")
(def records-id {:type {:size 10, :name "INT UNSIGNED"}, :table "records", :name "id"})
(def records-type_id {:type {:size 10, :name "INT UNSIGNED"}, :table "records", :name "type_id"})
(def records-score {:type {:size 19, :name "BIGINT"}, :table "records", :name "score"})
(def records-player_id {:type {:size 10, :name "INT UNSIGNED"}, :table "records", :name "player_id"})

;;;; recordtypes
(def table-recordtypes "recordtypes")
(def recordtypes-id {:type {:size 10, :name "INT UNSIGNED"}, :table "recordtypes", :name "id"})
(def recordtypes-name {:type {:size 255, :name "VARCHAR"}, :table "recordtypes", :name "name"})
```

recordtypes.clj:

```clj
(ns db.recordtypes)

(def name-kills-per-round "kills per round")
(def name-kills-per-game "kills per game")
(def name-longest-undead-time "longest undead time")
```


Now we can use other clj_query functions with tables and columns autocomplete:

```clj
user> (require '[ db.recordtypes :as rectypes ])
nil
user> (use 'db.entities)
nil
user> (def record (first (q/select-deep db table-records 
                              :join [[table-recordtypes [:= recordtypes-id records-type_id]]] 
                              :where [:= recordtypes-name rectypes/name-kills-per-round])))
#'user/record
```

Let's get "player" type through fk-referenced tables chain: records -> players -> playertypes

```clj
user> (q/get-field-from-row record records-player_id players-type_id playertypes-name)
"bot"
```

So the best result of "kills per round" belongs to "bot" player.
