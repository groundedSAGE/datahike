(ns sandbox
  (:require ;[datahike.api :as d]
            ;[datahike.query :as q]
            [datahike.db :as db]
            [clojure.core.async :as async]
            [hitchhiker.tree.utils.cljs.async :as ha]
   ))

(async/go
  (def working-tx-dummy {:initial-report {:db-before (async/<! (db/empty-db)), :db-after (async/<! (db/empty-db)), :tx-data [], :tempids {}, :tx-meta nil}
                         :initial-es [#:db{:ident :name, :cardinality :db.cardinality/one, :index true, :unique :db.unique/identity, :valueType :db.type/string} #:db{:ident :sibling, :cardinality :db.cardinality/many, :valueType :db.type/ref} #:db{:ident :age, :cardinality :db.cardinality/one, :valueType :db.type/long}]}))



(comment

  (require '[datahike.db :as dd])
  (require '[datahike.datom :refer [datom]])

  (let [db (dd/init-db [(datom 1 :foo "bar") (datom 2 :qux :quun)])]
    (dd/-datoms db :eavt nil))

  (println "test")

  (macroexpand-1 '(ha/<?? (ha/go-try (+ 1 1))))

  (println working-tx-dummy)

  (async/go (println (async/<! (db/empty-db))))

  (async/go (println (async/<! (db/init-db []))))


  (async/go (println (async/<! (db/transact-tx-data (:initial-report working-tx-dummy) (:initial-es working-tx-dummy)))))


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

  (async/go (def bob-db (:db-after (async/<! (with (async/<! (db/empty-db)) [{:name "bob" :age 5}])))))

  (async/<!! (q/q '[:find ?a :where
                    [?e :name "bob"]
                    [?e :age ?a]]
                  bob-db))



  ;(def tx-dummy {:initial-report #datahike.db.TxReport{:db-before #datahike/DB {:max-tx 536870912 :max-eid 0}, :db-after #datahike/DB {:max-tx 536870912 :max-eid 0}, :tx-data [], :tempids {}, :tx-meta nil}
  ;               :initial-es [#:db{:ident :name, :cardinality :db.cardinality/one, :index true, :unique :db.unique/identity, :valueType :db.type/string} #:db{:ident :sibling, :cardinality :db.cardinality/many, :valueType :db.type/ref} #:db{:ident :age, :cardinality :db.cardinality/one, :valueType :db.type/long}]})

  ;;
  )
