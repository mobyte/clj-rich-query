(ns db.entities)

;;;; players
(def table-players "players")
(def players-id {:type {:size 10, :name "INT UNSIGNED"}, :table "players", :name "id"})
(def players-name {:type {:size 255, :name "VARCHAR"}, :table "players", :name "name"})
(def players-type_id {:type {:size 10, :name "INT UNSIGNED"}, :table "players", :name "type_id"})

;;;; playertypes
(def table-playertypes "playertypes")
(def playertypes-id {:type {:size 10, :name "INT UNSIGNED"}, :table "playertypes", :name "id"})
(def playertypes-name {:type {:size 255, :name "VARCHAR"}, :table "playertypes", :name "name"})

;;;; records
(def table-records "records")
(def records-id {:type {:size 10, :name "INT UNSIGNED"}, :table "records", :name "id"})
(def records-type_id {:type {:size 10, :name "INT UNSIGNED"}, :table "records", :name "type_id"})
(def records-score {:type {:size 19, :name "BIGINT"}, :table "records", :name "score"})
(def records-player_id {:type {:size 10, :name "INT UNSIGNED"}, :table "records", :name "player_id"})

;;;; recordtypes
(def table-recordtypes "recordtypes")
(def recordtypes-id {:type {:size 10, :name "INT UNSIGNED"}, :table "recordtypes", :name "id"})
(def recordtypes-name {:type {:size 255, :name "VARCHAR"}, :table "recordtypes", :name "name"})

