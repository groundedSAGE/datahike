(ns ^:no-doc datahike.impl.entity
  (:refer-clojure :exclude [keys get])
  (:require [#?(:cljs cljs.core :clj clojure.core) :as c]
            [datahike.db :as db]
            [clojure.core.async :as async]
            [hitchhiker.tree.utils.cljs.async :as ha])
  #?(:clj (:import [datahike.java IEntity])))

(declare entity ->Entity equiv-entity lookup-entity touch)

(defn- entid [db eid]
  (ha/go-try
   (when (or (number? eid)
             (sequential? eid)
             (keyword? eid))
     (ha/<? (db/entid db eid)))))

(defn entity [db eid]
  {:pre [(db/db? db)]}
  (println "The entity function: " db " eid " eid)
  (when-let [e (entid db eid)]
    (let [return-entity (->Entity db e (volatile! false) (volatile! {}))]
      (println "return entity " return-entity)
      (touch return-entity)
      return-entity)))

(defn- entity-attr [db a datoms]
  (if (db/multival? db a)
    (if (db/ref? db a)
      (reduce #(conj %1 (entity db (:v %2))) #{} datoms)
      (reduce #(conj %1 (:v %2)) #{} datoms))
    (if (db/ref? db a)
      (entity db (:v (first datoms)))
      (:v (first datoms)))))

(defn- -lookup-backwards [db eid attr not-found]
  ;; becomes async
  
  (if-let [datoms (not-empty (db/-search db [nil attr eid]))]
    (if (db/component? db attr)
      (entity db (:e (first datoms)))
      (reduce #(conj %1 (entity db (:e %2))) #{} datoms))
    not-found))

#?(:cljs
   (defn- multival->js [val]
     (when val (to-array val))))



#?(:cljs
   (defn- js-seq [e]
     (touch e)
     (for [[a v] @(.-cache e)]
       (if (db/multival? (.-db e) a)
         [a (multival->js v)]
         [a v]))))

(deftype Entity [db eid touched cache]
  #?@(:cljs
      [Object
       (toString [this]
                 (pr-str* this))
       (equiv [this other]
              (equiv-entity this other))

       ;; js/map interface
       (keys [this]
             (es6-iterator (c/keys this)))  ; TODO: consider removing of async version
       (entries [this]
                (es6-entries-iterator (js-seq this)))
       (values [this]
               (es6-iterator (map second (js-seq this))))
       (has [this attr]
            (not (nil? (.get this attr))))
       (get [this attr]                     ; TODO: Needs to be async or use cache
            (if (= attr ":db/id")
              eid
              (if (db/reverse-ref? attr)
                (-> (-lookup-backwards db eid (db/reverse-ref attr) nil)
                    multival->js)
                (cond-> (lookup-entity this attr)
                  (db/multival? db attr) multival->js))))
       (forEach [this f]
                (doseq [[a v] (js-seq this)]
                  (f v a this)))
       (forEach [this f use-as-this]
                (doseq [[a v] (js-seq this)]
                  (.call f use-as-this v a this)))

       ;; js fallbacks
       (key_set   [this] (to-array (c/keys this)))
       (entry_set [this] (to-array (map to-array (js-seq this))))
       (value_set [this] (to-array (map second (js-seq this))))

       IEquiv
       (-equiv [this o] (equiv-entity this o))

       IHash
       (-hash [_]
              (hash eid)) ;; db? ; TODO: (hash db)

       ISeqable
       (-seq [this]
             (touch this) ;; TODO: If not already touched throw an exception in cljs (defn ensure-touched ..)
             (if (.-touched this) 
               (seq @cache)
               (throw (js/Error. "Entity not touched."))))

       ICounted
       (-count [this]
               (touch this)
               (if (.-touched this)
                 (count @cache)
                 (throw (js/Error. "Entity not touched."))))

       ILookup
       (-lookup [this attr]           (lookup-entity this attr nil))
       (-lookup [this attr not-found] (lookup-entity this attr not-found))

       IAssociative
       (-contains-key? [this k]
                       (not= ::nf (lookup-entity this k ::nf)))

       IFn
       (-invoke [this k]
                (lookup-entity this k))
       (-invoke [this k not-found]
                (lookup-entity this k not-found))

       IPrintWithWriter
       (-pr-writer [_ writer opts]
                   (-pr-writer (assoc @cache :db/id eid) writer opts))]

      :clj
      [Object
       IEntity
       (toString [e]      (pr-str (assoc @cache :db/id eid)))
       (hashCode [e]      (hash eid)) ; db?
       (equals [e o]      (equiv-entity e o))

       clojure.lang.Seqable
       (seq [e]           (touch e) (seq @cache))

       clojure.lang.Associative
       (equiv [e o]       (equiv-entity e o))
       (containsKey [e k] (not= ::nf (lookup-entity e k ::nf)))
       (entryAt [e k]     (some->> (lookup-entity e k) (clojure.lang.MapEntry. k)))

       (empty [e]         (throw (UnsupportedOperationException.)))
       (assoc [e k v]     (throw (UnsupportedOperationException.)))
       (cons  [e [k v]]   (throw (UnsupportedOperationException.)))
       (count [e]         (touch e) (count @(.-cache e)))

       clojure.lang.ILookup
       (valAt [e k]       (lookup-entity e k))
       (valAt [e k not-found] (lookup-entity e k not-found))

       clojure.lang.IFn
       (invoke [e k]      (lookup-entity e k))
       (invoke [e k not-found] (lookup-entity e k not-found))]))

(defn entity? [x] (instance? Entity x))

#?(:clj
   (defmethod print-method Entity [e, ^java.io.Writer w]
     (.write w (str e))))

(defn- equiv-entity [^Entity this that]
  (and
   (instance? Entity that)
   ;; (= db  (.-db ^Entity that))
   (= (.-eid this) (.-eid ^Entity that))))

(defn- lookup-entity
  ;; becomes async
  ([this attr] (lookup-entity this attr nil))
  ([^Entity this attr not-found]
   (println "inside lookup-entity: " "this: " this "attr: " attr )
   (ha/go-try
    (println "inside the go try of lookup-entity")
    (if (= attr :db/id)
      (do (println "first if" (ha/<? (.-eid this))) 
          (ha/<? (.-eid this)))
      (do 
        (println "running next if")
        (if (db/reverse-ref? attr)
          (-lookup-backwards (.-db this) (.-eid this) (db/reverse-ref attr) not-found)
          (do 
            (println "running if some")
            (if-some [v (@(.-cache this) attr)]
              (do (println "this is v " v) v)
              (do
                (println "running if-touched")
                (if @(.-touched this)
                  (do (println "not found") not-found)
                  (do
                    (println "running if-some because not touched")
                    (println "datoms? " (ha/<? (db/-search (.-db this) [(ha/<? (.-eid this)) attr])))
                    (if-some [datoms (not-empty (ha/<? (db/-search (.-db this) [(ha/<? (.-eid this)) attr])))]
                      (do
                        (println "this is the datoms")
                        (let [value (entity-attr (.-db this) attr datoms)]
                          (vreset! (.-cache this) (assoc @(.-cache this) attr value))
                          value))
                      not-found))))))))))))


(defn touch-components [db a->v]
  ;; becomes async
  (ha/go-try
   (reduce-kv (fn [acc a v]
                (assoc acc a
                       (if (db/component? db a)
                         (if (db/multival? db a)
                           (set (map touch v))
                           (ha/<? (touch v)))
                         v)))
              {} a->v)))

(defn- datoms->cache [db datoms]
  (reduce (fn [acc part]
            (let [a (:a (first part))]
              (assoc acc a (entity-attr db a part))))
          {} (partition-by :a datoms)))

(defn touch [^Entity e]
  ;; becomes async
  {:pre [(entity? e)]}
  ;(println "inside touch")
  (ha/go-try    ; This is kind of a lingering go-try maybe it needs to be closed?
   ;(println "inside touch go-try")
   ;(println "is it touched " @(.-touched e))
   (when-not @(.-touched e)
     (do
       ;(println "before the when-let")
       (println "another" [(ha/<? (.-eid e))]  (.-db e) [(.-eid e)])
       ;(println "with eid" (ha/<? (db/-search (.-db e) [(ha/<? (.-eid e))])))
       (when-let [datoms (not-empty (ha/<? (db/-search (.-db e) [(ha/<? (.-eid e))])))]
         ;(println "in the when-let")
         ;(println "touched? " (.-touched e))
         ;(println "cache " (.-cache e))
         (vreset! (.-cache e) (->> datoms
                                   (datoms->cache (.-db e))
                                   (touch-components (.-db e))
                                   (ha/<?)))
         (vreset! (.-touched e) true)
         ;(println "cache " (.-cache e))
         ;(println "touched? " (.-touched e))
         ))))
  e)

#?(:cljs (goog/exportSymbol "datahike.impl.entity.Entity" Entity))
