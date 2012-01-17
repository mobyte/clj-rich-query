(defproject crystal.clojure_libs/query_libs "0.2"
  :description "Clojure query lib"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.clojure/java.jdbc "0.0.4"]]
  :dev-dependencies [[swank-clojure "1.3.3"]
                     [com.mysql/jdbc "5.1.5"]]
  :compile-path  "build"
  :library-path  "libs"
  :aot [libs.db.query
        libs.db.gentablecolumns
        libs.db.gencolumnvalues
        libs.db.java-interop])
