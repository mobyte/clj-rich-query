(ns libs.db.gencolumnvalues
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.contrib.duck-streams :as duck])
  (:use libs.db.query))


(defn generate-column-value-constants [db table column]
  (let [column (.toLowerCase column)
        table-lower-case (.toLowerCase table)
        ns-name (str "db." table-lower-case)
        file-name (str "src/db/" table-lower-case ".clj")]
    (duck/with-out-writer file-name
      (println (str `(ns ~(symbol ns-name)) "\n"))
      (doseq [row (select-with-db db table)]
        (let [value (get row (keyword column))
              id (get row :id)
              var-name (str column "-" (.toLowerCase (.replace value " " "-")))]
          (println (str `(def ~(symbol var-name) ~value)))
          (println (str `(def ~(symbol (str var-name "-id")) ~id))))))))


(defn generate-column-value-constants-referenced [db table column reference]
  (let [column (.toLowerCase column)
        table-lower-case (.toLowerCase table)
        ns-name (str "db." table-lower-case)
        file-name (str "src/db/" table-lower-case ".clj")]
    (duck/with-out-writer file-name
      (println (str `(ns ~(symbol ns-name)) "\n"))
      (doseq [row (select-deep db table)]
        (let [value (get row (keyword column))
              ref-field-name (get-field-from-row row (first reference) (second reference))
              id (get row :id)
              ref-suffix (str (:table (second reference)) "-" ref-field-name)
              var-name (str column "-" (.toLowerCase (.replace value " " "-")) "-of-" ref-field-name)]
          (println (str `(def ~(symbol var-name) ~value)))
          (println (str `(def ~(symbol (str var-name "-id")) ~id))))))))
