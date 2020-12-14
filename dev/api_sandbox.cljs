(ns api-sandbox
  (:require [datahike.api :as d]
            [clojure.core.async :as async :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [superv.async :refer [<?? S go-try <?]]
            [konserve.core :as k]))

(comment
  

  (def schema [{:db/ident       :name
                :db/cardinality :db.cardinality/one
                :db/index       true
                :db/unique      :db.unique/identity
                :db/valueType   :db.type/string}
               {:db/ident       :sibling
                :db/cardinality :db.cardinality/many
                :db/valueType   :db.type/ref}
               {:db/ident       :age
                :db/cardinality :db.cardinality/one
                :db/valueType   :db.type/long}])

  (def cfg {:store  {:backend :mem :id "sandbox"}
            :keep-history? false
            :schema-flexibility :write
            :initial-tx schema})
  

  (d/delete-database cfg)

  (d/create-database cfg)

  (go (def conn (<! (d/connect cfg))))
  
  (go (time (<! (d/transact conn (vec (for [i (range 10000)]
                                          {:age i}))))))


  (d/transact conn [{:name "Alice"
                     :age  25}
                    {:name "Bob"
                     :age  35}
                    {:name    "Charlie"
                     :age     45
                     :sibling [[:name "Alice"] [:name "Bob"]]}])

  (go (println (<! (d/q '[:find ?e ?a ?v ?t
                          :in $ ?a
                          :where [?e :name ?v ?t] [?e :age ?a]]
                        @conn
                        45))))
  
  ;; IndexedDB
  
   (def cfg-idb {:store  {:backend :indexeddb :id "idb-sandbox"}
            :keep-history? false
            :schema-flexibility :write
            :initial-tx schema})
  

  (d/delete-database cfg-idb)
  

  
  (println "-------------------------------")

 
  (d/create-database cfg-idb)
   
   

  (go (def conn-idb (<! (d/connect cfg-idb))))
   
  (d/release conn-idb)
  
  (defn release-connection []
    (js/console.log )
    (.close (:db (:store @conn-idb))))
  
  
  (release-connection)


  (d/transact conn-idb [{:name "Alice"
                     :age  25}
                    {:name "Bob"
                     :age  35}
                    {:name    "Charlie"
                     :age     45
                     :sibling [[:name "Alice"] [:name "Bob"]]}])
  
  
  (go (time (<! (d/transact conn-idb (vec (for [i (range 10000)]
                                              {:age i}))))))
  
  (go (time (println (<! (d/q '[:find (count ?e)
                                :where 
                                [?e :age _]]
                              @conn-idb)))))

  (go (println (<! (d/q '[:find ?e ?a ?v ?t
                          :in $ ?a
                          :where [?e :name ?v ?t] [?e :age ?a]]
                        @conn-idb
                        45))))
  

  
  
  
  (d/release conn-idb)
  
  (go (println (<! (d/database-exists? cfg-idb))))
  
  (d/delete-database cfg-idb)
  
  
  
  ;; second idb
  (def cfg-idb-test {:store  {:backend :indexeddb :id "idb-test"}
                   :keep-history? false
                   :schema-flexibility :write
                   :initial-tx schema})
  
  (d/create-database cfg-idb-test)
  

  
  ;;
  )
