(defproject crystal.clojure_libs/query_libs "0.3"
  :description "Clojure query lib"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/java.jdbc "0.0.4"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.4.0"]
                                  [org.clojure/tools.nrepl "0.2.0-RC1"]
                                  [com.mysql/jdbc "5.1.5"]]}}
  :compile-path "build"
  :library-path "libs"
  :aot [libs.db.query
        libs.db.gentablecolumns
        libs.db.gencolumnvalues
        libs.db.java-interop])
