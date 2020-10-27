(ns datahike.index.hitchhiker-tree
  (:require [hitchhiker.tree.utils.clojure.async :as ha]
            [hitchhiker.tree.messaging :as hmsg]
            [hitchhiker.tree.key-compare :as kc]
            [hitchhiker.tree :as tree]
            [hitchhiker.tree.utils.gc :refer [mark]]
            [konserve.gc :refer [sweep!]]
            [datahike.constants :refer [e0 tx0 emax txmax]]
            [datahike.datom :as dd]
            [clojure.set :as set]
            [clojure.core.async :as async]
            [tupelo.misc :as tupelo])
  #?(:clj (:import [clojure.lang AMapEntry]
                   [datahike.datom Datom])))

(extend-protocol kc/IKeyCompare
  clojure.lang.PersistentVector
  (-compare [key1 key2]
    (if-not (= (class key2) clojure.lang.PersistentVector)
      -1                                                    ;; HACK for nil
      (let [[a b c d] key1
            [e f g h] key2]
        (dd/combine-cmp
         (kc/-compare a e)
         (kc/-compare b f)
         (kc/-compare c g)
         (kc/-compare d h)))))
  java.lang.String
  (-compare [key1 key2]
    (compare key1 key2))
  clojure.lang.Keyword
  (-compare [key1 key2]
    (compare key1 key2))
  nil
  (-compare [key1 key2]
    (if (nil? key2)
      0 -1)))

(def ^:const br 300) ;; TODO name better, total node size; maybe(!) make configurable
(def ^:const br-sqrt (long (Math/sqrt br))) ;; branching factor

(defn- index-type->datom-fn [index-type]
  (case index-type
    :aevt (fn [a e v tx] (dd/datom e a v tx true))
    :avet (fn [a v e tx] (dd/datom e a v tx true))
    (fn [e a v tx] (dd/datom e a v tx true))))

(defn- datom->node [^Datom datom index-type]
  (case index-type
    :aevt [(.-a datom) (.-e datom) (.-v datom) (.-tx datom)]
    :avet [(.-a datom) (.-v datom) (.-e datom) (.-tx datom)]
    [(.-e datom) (.-a datom) (.-v datom) (.-tx datom)]))

(defn- from-datom [^Datom datom index-type]
  (let [datom-seq (case index-type
                    :aevt (list  (.-a datom) (.-e datom) (.-v datom) (.-tx datom))
                    :avet (list (.-a datom) (.-v datom)  (.-e datom) (.-tx datom))
                    (list (.-e datom) (.-a datom) (.-v datom) (.-tx datom)))]
    (->> datom-seq
         (remove #{e0 tx0 emax txmax})
         (remove nil?)
         vec)))

(defn -slice
  [tree from to index-type]
  (let [create-datom (index-type->datom-fn index-type)
        [a b c d] (from-datom from index-type)
        [e f g h] (from-datom to index-type)
        xf (comp
            (take-while (fn [^AMapEntry kv]
                           ;; prefix scan
                          (let [key (.key kv)
                                [i j k l] key
                                new (not (cond (and e f g h)
                                               (or (> (kc/-compare i e) 0)
                                                   (> (kc/-compare j f) 0)
                                                   (> (kc/-compare k g) 0)
                                                   (> (kc/-compare l h) 0))

                                               (and e f g)
                                               (or (> (kc/-compare i e) 0)
                                                   (> (kc/-compare j f) 0)
                                                   (> (kc/-compare k g) 0))

                                               (and e f)
                                               (or (> (kc/-compare i e) 0)
                                                   (> (kc/-compare j f) 0))

                                               e
                                               (> (kc/-compare i e) 0)

                                               :else false))]
                            new)))
            (map (fn [kv]
                   (let [[a b c d] (.key ^AMapEntry kv)]
                     (create-datom a b c d)))))
        iter-chan (async/chan 1 xf)
        _ (hmsg/forward-iterator iter-chan tree [a b c d])]
    (ha/go-try (seq (ha/<? (async/into [] iter-chan))))))

(defn -seq [tree index-type]
  (-slice tree (dd/datom e0 nil nil tx0) (dd/datom emax nil nil txmax) index-type))

(defn -count [tree index-type]
  (count (-seq tree index-type)))

(defn -all [tree index-type]
  (map
   #(apply
     (index-type->datom-fn index-type)
     (first %))
   (hmsg/lookup-fwd-iter tree [])))

(defn empty-tree
  "Create empty hichthiker tree"
  []
  (tree/b-tree (tree/->Config br-sqrt br (- br br-sqrt))))

(defn -insert [tree ^Datom datom index-type]
  (hmsg/insert tree (datom->node datom index-type) nil))

(defn init-tree
  "Create tree with datoms"
  [datoms index-type]
  (ha/go-try
   (ha/<?
    (ha/reduce<
     (fn [tree datom]
       (hmsg/insert tree (datom->node datom index-type) nil))
     (ha/<? (empty-tree))
     (seq datoms)))))

(defn -remove [tree ^Datom datom index-type]
  (ha/<?? (hmsg/delete tree (datom->node datom index-type))))

(defn -flush [tree backend]
  (:tree (ha/<?? (tree/flush-tree-without-root tree backend))))

(def -persistent! identity)

(def -transient identity)

(defn gc
  "Invokes garbage collection on the database erasing all fragments that are not reachable and older than the date passed."
  [db date]
  (let [{:keys [eavt avet aevt temporal-eavt temporal-avet temporal-aevt config]} db
        marked (set/union #{:db}
                          (ha/<?? (mark (into #{} (:children eavt))))
                          (ha/<?? (mark (into #{} (:children avet))))
                          (ha/<?? (mark (into #{} (:children aevt))))
                          (when (:temporal-index config)
                            (set/union
                             (ha/<?? (mark (into #{} (:children temporal-eavt))))
                             (ha/<?? (mark (into #{} (:children temporal-avet))))
                             (ha/<?? (mark (into #{} (:children temporal-aevt)))))))]
    (async/<!! (sweep! (:store db) marked date))))
