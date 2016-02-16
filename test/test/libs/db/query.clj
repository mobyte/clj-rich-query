(ns test.libs.db.query
  (:use [clojure.test])
  (:require [clojure.java.jdbc :as jdbc]
            [libs.db.query :as q]
            [db.entities :as e])
  (:import 
   (java.sql PreparedStatement)))

(defn execute-command [sql]
  (jdbc/do-commands sql))

(defn create-test-tables []
  (execute-command " CREATE TABLE `players` (
  `id` int(11) DEFAULT NULL,
  `name` varchar(25) DEFAULT NULL,
  `type_id` int(11) DEFAULT NULL ) ")
  (execute-command "CREATE TABLE `playertypes` (
  `id` int(11) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL ) "))

(defn get-test-db []
  (let [test-db
        {:classname "org.h2.Driver"
         :subprotocol "h2:mem"
         :subname ( str (java.util.UUID/randomUUID)  "temp;DB_CLOSE_DELAY=-1;MODE=MYSQL;")
         :user ""
         :password ""}
       res (q/with-db test-db (create-test-tables))]
    test-db))

(deftest make-full-field-name 
    (let [actual (#'q/make-full-field-name e/players-id )]
      (is (= "players.id" actual))))

(deftest make-constant
  (let [f #(#'q/make-constant %)]
    (is (= 0 (f 0)))
    (is (= "\"0\"" (f "0")))
    (is (= "\"abc\"" (f "abc")))
    ))


(deftest prepare-select-order
  (is (= "players.id desc" (#'q/prepare-select-order [e/players-id :desc])))
  (is (= "players.id " (#'q/prepare-select-order [e/players-id])))
  (is (= "players.id" (#'q/prepare-select-order e/players-id))))

(deftest make-select-filter
  (let [f #(#'q/make-select-filter %)]
   (is (= "where (players.id = 3)" (f [:= e/players-id 3])))
   (is (= "where (not (players.id = 3))" (f [:not [:= e/players-id 3]])))
   (is (= "where (players.id in ( 1,  2,  3 ))"  (f [:in e/players-id [1 2 3]])))
   (is (= "where (players.id in ( 1 ))" (f [:in e/players-id [1]])))
   (is (= "where (players.id in ( \"1\",  \"2\",  \"3\" ))" (f [:in e/players-id ["1" "2" "3"]])))
   (is (= "where (players.id in ( \"1\" ))" (f [:in e/players-id ["1"]])))   ))

(deftest make-select-order 
  (let [f #(#'q/make-select-order %)]
    (is (= "order by players.id desc" (f [[e/players-id :desc]])))
    (is (= "order by players.id desc,  players.name desc" (f [[e/players-id :desc] [e/players-name :desc]]))) ))

(deftest make-select-fields 
  (let [f #(#'q/make-select-fields %)]
    (is (= "players.id,  players.name" (f [e/players-id e/players-name])))
    (is (= "players.id" (f [e/players-id])))))

(deftest make-select-join 
  (let [f #(#'q/make-select-join %)]
    (is (= "inner join records on (records.player_id = players.id) inner join players on (players.type_id = playertypes.id)" (f [[e/table-records
                    [:= e/records-player_id e/players-id]]
                  [e/table-players
                   [:= e/players-type_id e/playertypes-id]]])))))


(deftest make-select-limit 
  (let [f #(#'q/make-select-limit %)]
    (is (= "limit 1" (f 1)))))

(deftest make-key-from-field
  (let [f #(q/make-key-from-field %)]
    (is (= :id (f e/players-id)))
    (is (= :name (f e/players-name)))))

(deftest make-key-from-string
  (let [f #(#'q/make-key-from-string %)]
    (is (= :id (f "id")))
    (is (= :name (f "name")))))

(deftest make-field-parameter
  (let [f #(#'q/make-field-parameter %)]
    (is (= :id (f e/players-id)))
    (is (= :name (f e/players-name)))
    (is (= :id :id))
    (is (= [:name :id] (vec (map f [e/players-name e/players-id]))))))


(deftest select-empty-test 
  (let [db (get-test-db)
        res (q/with-db db (q/select e/table-players :fileds [e/players-id] ))
        res2 (q/select-with-db db e/table-players :fileds [e/players-id])]
    (is (= [] res))
    (is (= [] res2))))

(deftest select-test
  (let [db (get-test-db)
        insert-res (q/with-db db (q/insert e/table-playertypes
                                        [e/playertypes-id e/playertypes-name]
                                        [1 "simple"]))
        insert-res (q/with-db db (q/insert e/table-playertypes
                                        [e/playertypes-id e/playertypes-name]
                                        [2 "super"]))
        insert-res (q/with-db db (q/insert e/table-players
                                        [e/players-id e/players-name e/players-type_id]
                                        [1 "simple player" 1]))
        select-result-fields (q/with-db db 
                               (q/select e/table-playertypes 
                                         :fields [e/playertypes-name]
                                    :where [:= e/playertypes-id 2]
                                    :order [e/playertypes-id]
                                    :limit 1))
        select-result (q/with-db db 
                                     (q/select e/table-playertypes 
                                    :where [:= e/playertypes-id 2]))
        fn-select #(q/with-db db 
                                     (q/select e/table-playertypes 
                                    :where [:= e/playertypes-id 2]))
        select-result-with-join (q/with-db db 
                                  (q/select e/table-players
                                    :fields [e/players-name e/players-id]
                                    :join [[e/table-playertypes
                                           [:= e/players-type_id e/playertypes-id]]]
                                    :where [:= e/playertypes-id 1]
                                    :order [e/playertypes-id]
                                    :limit 1))]
    (is (=  1 (count select-result-fields)))    
    (is (= {:name "super"} (first select-result-fields)))    
    (is (=  1 (count select-result)))    
    (is (= {:id 2, :name "super"} (first select-result)))
    (is (= {:name "simple player", :id 1} (first select-result-with-join)))
    (is (= "simple player" (q/get-field-from-row (first select-result-with-join) e/players-name)))
    ;; recursive result retriving not working?
    (is (= nil (q/get-field-from-row (first select-result-with-join) e/players-name e/players-id)))
    (is (= "super" (q/get-field-from-row #(first (fn-select)) e/playertypes-name)))    ))




(deftest update-test
  (let [db (get-test-db)
        insert-res (q/with-db db (q/insert e/table-playertypes
                                        [e/playertypes-id e/playertypes-name]
                                        [1 "simple"]))
        insert-res (q/with-db db (q/insert e/table-playertypes
                                        [e/playertypes-id e/playertypes-name]
                                        [2 "super"]))
        insert-res (q/with-db db (q/insert e/table-players
                                        [e/players-id e/players-name e/players-type_id]
                                        [1 "simple player" 1]))
        select-result (q/with-db db (q/select e/table-playertypes 
                                    :where [:= e/playertypes-id 2]))
        update-result (q/with-db db (q/update e/table-playertypes 
                                    {e/playertypes-name "boss"}
                                     [:= e/playertypes-id 2] ))
        select-result-after-update (q/with-db db 
                                     (q/select e/table-playertypes 
                                    :where [:= e/playertypes-id 2]))]
    
    (is (= {:id 2, :name "super"} (first select-result)))    
    (is (= {:id 2, :name "boss"} (first select-result-after-update)))))


