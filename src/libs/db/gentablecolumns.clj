(ns libs.db.gentablecolumns
  (:require [clojure.java.jdbc :as jdbc])
  (:require [clojure.contrib.duck-streams :as duck]))

(def file-name "src/db/entities.clj")
(def table-prefix "table-")

(defn- make-field-string [table field]
  (let [field-name (symbol (str table "-"
                                (.toLowerCase (:name field))))]
    (str `(def ~field-name ~field))))

(defn- make-table-string [table lower-case-table]
  (let [table-name (symbol (str table-prefix lower-case-table))]
    (str `(def ~table-name ~table))))

(defn- get-table-columns [db table]
  (jdbc/with-connection db
    (let [conn (jdbc/connection)
          meta (.getMetaData conn)]
      (into []
            (map (fn [x] (zipmap [:name :table :type]
                                 [(:column_name x)
                                  table
                                  (zipmap [:name :size]
                                          [(:type_name x) (:column_size x)])]))
                 (resultset-seq (.getColumns meta nil nil table "%")))))))

(defn- get-tables [db]
  (jdbc/with-connection db
    (let [conn (jdbc/connection)
          meta (.getMetaData conn)]
      (into []
            (map #(:table_name %)
                 (resultset-seq (.getTables meta nil nil "%" nil)))))))

(defn- write-table-area [db table]
  (duck/with-out-append-writer file-name
    (println (str ";;;; " table))
    (let [lower-case-table-name (.toLowerCase table)
          columns (get-table-columns db table)]
      (println (make-table-string table lower-case-table-name))
      (doseq [column columns]
        (println (make-field-string lower-case-table-name column))))
    (println)))

(defn- write-namespace-area []
  (duck/with-out-writer file-name
    (println (str '(ns db.entities)))
    (println)))

(defn generate-table-column-names [db-spec]
  "Generates source file db/entities which contains table names and
their columns info"
  (duck/make-parents (java.io.File. file-name))
  ;; namespace area
  (write-namespace-area)
  (println)
  ;; tables area
  (doseq [table (get-tables db-spec)]
    (write-table-area db-spec table)))

