(ns libs.db.gentablecolumns
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as io])
  (:use libs.db.glib))

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
  (with-open [wrtr (io/writer file-name :append true)]
    (writeln wrtr ";;;; " table)
    (let [lower-case-table-name (.toLowerCase table)
          columns (get-table-columns db table)]
      (writeln wrtr (make-table-string table lower-case-table-name))
      (doseq [column columns]
        (writeln wrtr (make-field-string lower-case-table-name column))))
    (writeln wrtr)))

(defn- write-namespace-area []
  (with-open [wrtr (io/writer file-name)]
    (writeln wrtr (str '(ns db.entities)))
    (writeln wrtr)))

(defn generate-table-column-names [db-spec]
  "Generates source file db/entities which contains table names and
their columns info"
  (io/make-parents (java.io.File. file-name))
  ;; namespace area
  (write-namespace-area)
  (println)
  ;; tables area
  (doseq [table (get-tables db-spec)]
    (write-table-area db-spec table)))

