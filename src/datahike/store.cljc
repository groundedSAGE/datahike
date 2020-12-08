(ns datahike.store
  (:require [hitchhiker.tree.bootstrap.konserve :as kons]
            [clojure.spec.alpha :as s]
            #?(:clj [konserve.filestore :as fs])
            [konserve.memory :as mem]
            [clojure.core.async :as async]
            [hitchhiker.tree.utils.cljs.async :as ha]
            [superv.async :refer [<?? S <? go-try]]
            #?(:cljs [konserve.indexeddb :refer [new-indexeddb-store]])
            [konserve.serializers :as ser]
            [environ.core :refer [env]]))

(defmulti empty-store
  "Creates an empty store"
  {:arglists '([config])}
  :backend)

(defmethod empty-store :default [{:keys [backend]}]
  #?(:clj (throw (IllegalArgumentException. (str "Can't create a store with scheme: " backend)))
     :cljs (throw (js/Error. (str "Can't create a store with scheme: " backend)))))

(defmulti delete-store
  "Deletes an existing store"
  {:arglists '([config])}
  :backend)

(defmethod delete-store :default [{:keys [backend]}]
  #?(:clj (throw (IllegalArgumentException. (str "Can't delete a store with scheme: " backend)))
     :cljs (throw (js/Error. (str "Can't delete a store with scheme: " backend)))))

(defmulti connect-store
  "Makes a connection to an existing store"
  {:arglists '([config])}
  :backend)

(defmethod connect-store :default [{:keys [backend]}]
  #?(:clj (throw (IllegalArgumentException. (str "Can't connect to store with scheme: " backend)))
     :cljs (throw (js/Error. (str "Can't connect to store with scheme: " backend)))))

(defmulti release-store
  "Releases the connection to an existing store (optional)."
  {:arglists '([config store])}
  (fn [{:keys [backend]} store]
    backend))

(defmethod release-store :default [_ _]
  nil)

(defmulti scheme->index
  "Returns the index type to use for this store"
  {:arglists '([config])}
  :backend)

(defmulti default-config
  "Defines default configuration"
  {:arglists '([config])}
  :backend)

(defmethod default-config :default [{:keys [backend] :as config}]
  (println "INFO: No default configuration found for" backend)
  config)

(defmulti config-spec
  "Returns spec for the store configuration"
  {:arglists '([config])}
  :backend)

(defmethod config-spec :default [{:keys [backend]}]
  (println "INFO: Not configuration spec found for" backend))

;; memory

(def memory (atom {}))

(defmethod empty-store :mem [{:keys [id]}]
  (go-try S
   (if-let [store (get @memory id)]
     store
     (let [store (<? S (mem/new-mem-store))]
       (swap! memory assoc id store)
       store))))



(defmethod delete-store :mem [{:keys [id]}]
  (swap! memory dissoc id))

(defmethod connect-store :mem [{:keys [id]}]
  (ha/go-try (@memory id)))

(defmethod scheme->index :mem [_]
  :datahike.index/hitchhiker-tree)

(defmethod default-config :mem [{:keys [id] :as config}]
  (merge
   {:id (:datahike-store-id env "default")}
   config))

(s/def :datahike.store.mem/backend #{:mem})
(s/def :datahike.store.mem/id string?)
(s/def ::mem (s/keys :req-un [:datahike.store.mem/backend
                              :datahike.store.mem/id]))

(defmethod config-spec :mem [config] ::mem)

;; file

#?(:clj (defmethod empty-store :file [{:keys [path]}]
          (kons/add-hitchhiker-tree-handlers
           (<?? S (fs/new-fs-store path)))))

#?(:cljs (defmethod empty-store :indexeddb [{:keys [id]}]
           (println "empty-store method")
           (println "empty-store id string?: " (string? id))
           (go-try S
                   (let [result (<? S (new-indexeddb-store id :serializer (ser/fressian-serializer)))
                         _ (println "empty-store result: " result)]
                     (kons/add-hitchhiker-tree-handlers
                      result)))))

#_#?(:clj (defmethod delete-store  :indexeddb [{:keys [id]}]
          (fs/delete-store id)))

#?(:cljs (defmethod delete-store :file [{:keys [path]}]
          (fs/delete-store path)))

#?(:clj (defmethod connect-store :file [{:keys [path]}]
          (<?? S (fs/new-fs-store path))))

#?(:cljs (defmethod connect-store :indexeddb [{:keys [id]}]
           (do 
             (println "connected-store")
             (new-indexeddb-store id :serializer (ser/fressian-serializer)))))


(defmethod scheme->index :file [_]
  :datahike.index/hitchhiker-tree)

(defmethod default-config :file [{:keys [id] :as config}]
  (merge
   {:path (:datahike-store-path env)}
   config))

(s/def :datahike.store.file/path string?)
(s/def :datahike.store.file/backend #{:file})
(s/def ::file (s/keys :req-un {:datahike.store.file/backend
                               :datahike.store.file/path}))

(defmethod config-spec :file [_] ::file)
