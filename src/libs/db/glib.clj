(ns libs.db.glib)

(defn writeln [writer & text]
  (.write writer (apply str text))
  (.write writer "\n"))

(defn write [writer & text]
  (.write writer (apply str text)))
