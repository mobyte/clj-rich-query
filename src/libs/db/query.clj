(ns libs.db.query
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.java.jdbc.internal :as jdbcint])
  (:import 
   (java.sql PreparedStatement)))

(defn- prepare-select-filter [expr]
  (if (vector? expr)
    (cond (= :in (first expr)) (list (prepare-select-filter (second expr))
                                     (symbol (name (first expr)))
                                     (rest (reduce #(cons (symbol (str \,)) (cons %2 %1)) nil
                                                   (map prepare-select-filter (nth expr 2)))))
          :else (list (prepare-select-filter (second expr))
                      (symbol (name (first expr)))
                      (prepare-select-filter (nth expr 2))))
    (if (map? expr)
      (symbol (str (:table expr) "." (:name expr)))
      expr)))

(defn- make-select-filter [expr]
  (str "where " (prepare-select-filter expr)))

(defn- prepare-select-order [expr]
  (if (vector? expr)
    (str (:name (first expr)) " " (name (or (second expr) "")))
    (:name expr)))

(defn- make-select-order [expr]
  (str "order by " (reduce #(str %1 "," %2) (map #(prepare-select-order %) expr))))

(defn- make-select-fields [fields]
  (reduce #(str %1 ", " %2) (map #(str (:table %) "." (:name %)) fields)))

(defn- make-select-join [expr]
  (reduce #(str %1 " " %2) 
          (map (fn [x] (str "inner join " (symbol (first x)) " on " (prepare-select-filter (second x)))) expr)))

(defn make-key-from-field [field]
  (keyword (.toLowerCase (:name field))))

(defn- make-key-from-string [name]
  (keyword (.toLowerCase name)))

(defn- make-field-parameter [field]
  (cond (map? field)
        (make-key-from-field field)
        (keyword? field)
        field
        :else (keyword field)))

(defn get-field-from-row [row field & fields]
  (if-not fields
    (get (if (fn? row) (row) row)
         (make-key-from-field field))
    (apply get-field-from-row (get-field-from-row row field) fields)))

(def g-foreign-keys nil)

(defn- get-foreign-keys* [db table]
  (jdbc/with-connection db
    (let [meta (.getMetaData (jdbc/find-connection))]
      (into {}
            (map (fn [k] [(make-key-from-string (:fkcolumn_name k)) (:pktable_name k)])
                 (resultset-seq (.getImportedKeys meta nil nil table)))))))

(defn- get-foreign-keys [db table]
  (if-let [key-map (get g-foreign-keys table)]
    key-map
    (do (def g-foreign-keys (assoc g-foreign-keys
                              table
                              (get-foreign-keys* db table)))
        (get-foreign-keys db table))))

;;;; with db

(defmacro with-db [db & body]
  `(jdbc/with-connection ~db
     (jdbc/transaction
      (try ~@body
           (catch Exception e#
             (try (jdbc/set-rollback-only)
                  (catch Exception inner-e#))
             (throw e#))))))

;;;; select

(defn delete-from-row [row fields]
  (if (empty? fields) row
      (recur (dissoc row (make-field-parameter (first fields)))
             (rest fields))))

(defn select [table-name & {:keys [fields where order join]}]
  (let [where-string (when where (make-select-filter where))
        order-string (when order (make-select-order order))
        fields-string (if fields (make-select-fields fields) (str table-name ".*"))
        join-string (when join (make-select-join join))
        select-string (str "select " fields-string " from " table-name " "
                           join-string " " where-string " " order-string)]
    (jdbc/with-query-results rs [select-string]
      (vec rs))))

(defn select-with-db [db table-name & {:keys [fields where order join]}]
  (jdbc/with-connection db
    (select table-name :fields fields :where where :order order :join join)))

;;;; select deep

(defn make-select-deep [db table-name rowset]
  (let [foreign-keys (get-foreign-keys db table-name)]
    (if (empty? foreign-keys)
      rowset
      (map (fn [row]
             (reduce
              (fn [prev-map relation]
                (if-not (get prev-map (first relation))
                  prev-map
                  (assoc prev-map
                    (first relation)
                    (fn [] (first (make-select-deep db (second relation)
                                                    (select-with-db db (second relation)
                                                      :where [:= 'id
                                                              (get prev-map (first relation))])))))))
              row
              foreign-keys))
           rowset))))

(defn select-deep [db table-name & {:keys [fields where order join]}]
  (jdbc/with-connection db
    (let [rowset (select table-name :fields fields :where where :order order :join join)]
      (make-select-deep db table-name rowset))))

;;;; insert

(defn insert [table fields values]
  (jdbc/insert-values
   (keyword table)
   (map make-field-parameter fields)
   values))

;;;; update

(defn make-update-map-parameters [parameters]
  (into {}
        (map #(vector (make-field-parameter (first %))
                      (second %))
             parameters)))

(defn update [table attribute-map update-filter]
  (jdbc/update-values
   (keyword table)
   [(str (prepare-select-filter update-filter))]
   (make-update-map-parameters attribute-map)))

(defn execute-update [sql]
  (with-open [stmt (.createStatement (jdbc/find-connection))]
    (.executeUpdate stmt sql)))