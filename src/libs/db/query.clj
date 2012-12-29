(ns libs.db.query
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.java.jdbc.internal :as jdbcint])
  (:import 
   (java.sql PreparedStatement)))

(defn- make-full-field-name [field]
  (str (:table field) "." (:name field)))

(defn- join-with [items sep & [trans-fn]]
  (apply str (interpose (str sep " ") (map (or trans-fn identity) items))))

(defn- join-with-comma [items & [trans-fn]]
  (join-with items ", " trans-fn))

(defn- join-with-space [items & [trans-fn]]
  (join-with items "" trans-fn))

(defn- make-constant [expr]
  (cond (= java.lang.String (type expr)) (str "\"" expr "\"")
        :else expr))

(defn- prepare-select-filter [expr]
  (if (vector? expr) ;; expression
    (str "(" (case (first expr)
               :in (join-with-space [(prepare-select-filter (second expr))
                                     "in" "(" (join-with-comma (nth expr 2)
                                                prepare-select-filter) ")"])
               :not (join-with-space ["not" (prepare-select-filter (second expr))])
               ;; (join-with-space [(prepare-select-filter (second expr))
               ;;                   (name (first expr))
               ;;                   (prepare-select-filter (nth expr 2))])
               (join-with-space (interpose (name (first expr)) 
                                           (map prepare-select-filter 
                                                (rest expr))))) ")")
    (if (map? expr)
      (make-full-field-name expr)  ;; field
      (make-constant expr)))) ;; constant

(defn- make-select-filter [expr]
  (str "where " (prepare-select-filter expr)))

(defn- prepare-select-order [expr]
  (if (vector? expr)
    (str (make-full-field-name (first expr)) " " (name (or (second expr) "")))
    (make-full-field-name expr)))

(defn- make-select-order [expr]
  (str "order by " (join-with-comma expr prepare-select-order)))

(defn- make-select-fields [fields]
  (join-with-comma fields make-full-field-name))

(defn- make-select-join [expr]
  (join-with-space
    (map #(join-with-space ["inner join" (symbol (first %))
                           "on" (prepare-select-filter (second %))])
         expr)))

(defn- make-select-limit [limit]
  (str "limit " limit))

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

(defn select [table-name & {:keys [fields where order join limit]}]
  (let [where-string (when where (make-select-filter where))
        order-string (when order (make-select-order order))
        fields-string (if fields (make-select-fields fields) (str table-name ".*"))
        join-string (when join (make-select-join join))
        limit-string (when limit (make-select-limit limit))
        select-string (str "select " fields-string " from " table-name " "
                           join-string " " where-string " " order-string " "
                           limit-string)]
    (jdbc/with-query-results rs [select-string]
      (vec rs))))

(defn select-with-db [db table-name & {:keys [fields where order join limit]}]
  (jdbc/with-connection db
    (select table-name :fields fields :where where :order order :join join :limit limit)))

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