(ns sandbox
  (:require ;[datahike.api :as d]
   [datahike.query :as q]
   [datahike.db :as db]
   [clojure.core.async :as async :refer [<!]]
   [hitchhiker.tree.utils.cljs.async :as ha]
   ;[datahike.impl.entity :refer [entity]]
   [datahike.core :as d]
   [datahike.impl.entity :as de]))

(async/go
  (def working-tx-dummy {:initial-report {:db-before (async/<! (db/empty-db)), :db-after (async/<! (db/empty-db)), :tx-data [], :tempids {}, :tx-meta nil}
                         :initial-es [#:db{:ident :name, :cardinality :db.cardinality/one, :index true, :unique :db.unique/identity, :valueType :db.type/string} #:db{:ident :sibling, :cardinality :db.cardinality/many, :valueType :db.type/ref} #:db{:ident :age, :cardinality :db.cardinality/one, :valueType :db.type/long}]}))



(comment
  ;; REPL-driven code
  
  (println "test that browser is connected")


  ;;
  ;; Primary work
  ;;
  
  (defn with
    "Same as [[transact!]], but applies to an immutable database value. Returns transaction report (see [[transact!]])."
    ([db tx-data] (with db tx-data nil))
    ([db tx-data tx-meta]
     {:pre [(db/db? db)]}
     (db/transact-tx-data (db/map->TxReport
                           {:db-before db
                            :db-after  db
                            :tx-data   []
                            :tempids   {}
                            :tx-meta   tx-meta}) tx-data)))




  (ha/go-try (def bob-db (:db-after (ha/<? (with (ha/<? (db/empty-db)) [{:name "bob" :age 5}])))))



  (async/go (println (async/<! (q/q '[:find ?a :where
                                      [?e :name "bob"]
                                      [?e :age ?a]]
                                    bob-db))))

  (async/go (println (<! ((<! (de/entity bob-db 1)) :name))))
  (async/go (println (async/<! ((async/<! (de/entity bob-db 1)) :age))))
  (async/go (println (async/<! ((async/<! (de/entity bob-db 1)) :db/id))))
  
  (async/go  (def touched-entity (<! (de/touch (<! (de/entity bob-db 1))))))
  
  (.-cache touched-entity)
  
  

  ;;
  ;; Initial work
  ;; 
  
  (println working-tx-dummy) 

  (async/go (println (ha/<? (db/empty-db)))) 

  (async/go (println (async/<! (db/init-db []))))


  (async/go (println (async/<! (db/transact-tx-data (:initial-report working-tx-dummy) (:initial-es working-tx-dummy)))))
  

  ;; Testing that datom works
  
  (require '[datahike.datom :refer [datom]])

  (async/go
    (let [db-result (async/<! (db/init-db [(datom 1 :foo "bar") (datom 2 :qux :quun)]))
          _ (println "db-result" db-result)]
      (println (async/<! (db/-datoms db-result :eavt nil)))))










  ;(def tx-dummy {:initial-report #datahike.db.TxReport{:db-before #datahike/DB {:max-tx 536870912 :max-eid 0}, :db-after #datahike/DB {:max-tx 536870912 :max-eid 0}, :tx-data [], :tempids {}, :tx-meta nil}
  ;               :initial-es [#:db{:ident :name, :cardinality :db.cardinality/one, :index true, :unique :db.unique/identity, :valueType :db.type/string} #:db{:ident :sibling, :cardinality :db.cardinality/many, :valueType :db.type/ref} #:db{:ident :age, :cardinality :db.cardinality/one, :valueType :db.type/long}]})
  
  ;;
  )
