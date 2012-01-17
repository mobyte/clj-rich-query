(ns libs.db.java-interop
  (require [libs.db.query :as q]))

;;;; java interoperability

;; unbean

(defn- get-methods-and-names [class]
  (remove #(= "class" (:name %))
          (map (fn [x]
                 {:name (.getName x) :method (.getWriteMethod x)})
               (.getPropertyDescriptors (java.beans.Introspector/getBeanInfo class)))))

(defn unbean [class bean-map]
  (let [instance (.newInstance class)]
    (doseq [{:keys [name method]} (get-methods-and-names class)]
      (when-let [value (get bean-map (keyword name))]
        (.invoke method instance (into-array [value]))))
    instance))

;; map -> object

(defn- transform-fields [map-entity field-processors field-col-map]
  (reduce (fn [result-map processor]
            (assoc result-map (get field-col-map (first processor))
                   ((second processor) (q/get-field-from-row result-map (first processor)))))
          map-entity
          field-processors))

(defn map-to-object [class map-entity field-processors field-col-map]
  (unbean class (transform-fields map-entity field-processors field-col-map)))

;; transaction object -> map

(defn object-to-map [object reverse-trans-field-map]
  (let [tran-map (bean object)]
    (reduce (fn [xs x] (assoc xs (first x)
                              ((second x) (get xs (first x)))))
            tran-map
            reverse-trans-field-map)))

;;;; insert with filter

(defn- delete-exception-fields [field-col-map exceptions]
  (remove #((into #{} exceptions) (first %)) field-col-map))

;; prepare [[columns][values]] for sql/insert

(defn- prepare-insert-values [entity-map field-col-map insert-exceptions]
  (reduce (fn [xs x] [(conj (first xs) (first x))
                      (conj (second xs) (get entity-map (second x)))])
          [[][]]
          (delete-exception-fields field-col-map insert-exceptions)))

(defn filter-insert [table entity-map field-col-map insert-exceptions]
  (do (let [params (prepare-insert-values entity-map field-col-map insert-exceptions)]
        (q/insert table (first params) (second params)))))

