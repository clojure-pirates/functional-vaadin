(ns functional-vaadin.config
  "Functions for doing map-based configuration of Vaadinwidgets. See config-table namespace"
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [functional-vaadin.thread-vars :refer :all]
            [functional-vaadin.config-table :refer :all]
            [functional-vaadin.event-handling :refer :all]
            [functional-vaadin.naming :refer :all]
            [functional-vaadin.utils :refer :all])
  (:import (java.util Map Collection)
           (clojure.lang Keyword)
           (com.vaadin.ui AbstractComponent AbstractOrderedLayout Alignment)
           (com.vaadin.shared.ui MarginInfo)))

(def attribute-translation
  "A mapping for alternative names for configuration attribute keys"
  {:alignment :componentAlignment})

(defn- save-for-layout-parent
  "Save an option for setting as an attribute on the parent. The saved value is an argument list for the attribute setter"
  [^AbstractComponent child opt-key opt-value]
  (.setData child (assoc-in (or (.getData child) {}) [:parent-data opt-key] [child opt-value])))

(defn- save-for-grid-parent
  "Save an option for setting as an attribute on the parent. The saved value is an argument list for the attribute setter"
  [^AbstractComponent child opt-key opt-value]
  (.setData child (assoc-in (or (.getData child) {}) [:parent-data opt-key] opt-value)))

(defn validate-margin [opt-val]
  (or (instance? Boolean opt-val)
      (and (instance? Collection opt-val)
           (<= (count opt-val) 4)
           (every? #{:left :right :top :bottom :vertical :horizontal} opt-val)
           )))


(defn ^MarginInfo ->MarginInfo [opt-val]
  (if (instance? Boolean opt-val)
    (MarginInfo. ^Boolean opt-val)
    (MarginInfo.
      (boolean (some #{:top :vertical} opt-val))
      (boolean (some #{:right :horizontal} opt-val))
      (boolean (some #{:bottom :vertical} opt-val))
      (boolean (some #{:left :horizontal} opt-val))
      )
    ))

(def synthetic-option-specs
  ""
  {
   :expandRatio        {; OrderedLayout expansionRation
                        :validate  (fn [optval] (instance? Float optval))
                        :error-msg "Expansion ration must be a Float"
                        :execute   save-for-layout-parent
                        }
   :componentAlignment {;OrderedLayout componentAlignment
                        :validate  (fn [optval] (instance? Alignment optval))
                        :error-msg "Component alignment must be an Alignment value"
                        :execute   save-for-layout-parent
                        }
   :position           {; GridLayout position
                        :validate  (fn [vals] (and (vector? vals) (= 2 (count vals)) (every? integer? vals)))
                        :error-msg "Grid position must be a vector of two integers"
                        :execute   save-for-grid-parent
                        }
   :span               {; GridLayout span
                        :validate  (fn [vals] (and (vector? vals) (= 2 (count vals)) (every? integer? vals)))
                        :error-msg "Element span must be a vector of two integers"
                        :execute   save-for-grid-parent}
   :margin             {
                        :validate  validate-margin
                        :execute   (fn [^AbstractOrderedLayout obj opt-key opt-val]
                                     (.setMargin obj (->MarginInfo opt-val)))
                        :error-msg "Margin info must be true/false or a vector of the keywords [:left :right :top :bottom :vertical :horizontal]"}
   :id                 {
                        :validate  (fn [val] (or (instance? Keyword val) (instance? String val)))
                        :execute   (fn [obj opt-key id] (addComponent *current-ui* obj (keyword id)) (.setId obj (name id)))
                        :error-msg "Id must be a String or Keyword"}
   :onClick            {:validate  (fn [val] (ifn? val))
                        :error-msg "Click handler must be a function"
                        :execute   (fn [obj opt-key action] (onClick obj action))}
   :onValueChange      {:validate  (fn [val] (ifn? val))
                        :error-msg "Value change handler must be a function"
                        :execute   (fn [obj opt-key action] (onValueChange obj action))}
   })

(defn- validate-option [errors [opt-key opt-val] specs]
  (if-let [{v-fn :validate msg :error-msg} (get specs opt-val)]
    (if (not (v-fn opt-val))
      (conj errors msg))
    errors)
  )

(defn validate-config [config specs]
  (reduce #(validate-option %1 %2 specs) [] config))

(defn translate-config-keys [config]
  (reduce (fn [nopts [k v]] (assoc nopts (or (get attribute-translation k) k) v)) {} config))

(defn do-syntheric-option [obj [opt-key opt-val]]
  ((get-in synthetic-option-specs [opt-key :execute]) obj opt-key opt-val))

(defn do-synthetic-options [config obj]
  (let [[syn-config attr-config] (extract-keys config (keys synthetic-option-specs))]
    (doseq [option syn-config]
      (do-syntheric-option obj option))
    attr-config))

(defn- set-attribute
  [obj [attribute args]]
  (let [arg-list (if (not (or (seq? args) (vector? args))) [args] args)
        attr-setter (keyword (str "set" (capitalize (name attribute))))
        f (get config-table [attr-setter (count arg-list)])]
    (if f
      (do
        (apply f obj arg-list))
      (unsupported-op (str "No such option for " (class obj) ": " attribute)))))

(defn do-configure [obj ^Map config]
  (doseq [attr-spec config]
    (set-attribute obj attr-spec))
  obj)

(defn- do-attribute-config
  "Apply a configuration option that sets an object attribute (e.g. setMargin)"
  [obj [attribute args]]
  (let [arg-list (if (not (or (seq? args) (vector? args))) [args] args)
        opt-key (keyword (str "set" (capitalize (name attribute))))
        f (get config-table [opt-key (count arg-list)])]
    (if f
      (do
        (apply f obj arg-list))
      (unsupported-op (str "No such option for " (class obj) ": " attribute)))))

(defn do-attribute-options
  "Configure a component from a set of configuration options (a Map key->value)"
  [^Map config obj]
  (doseq [attr-spec config]
    (do-attribute-config obj attr-spec))
  config)

(defn configure
  "Configure a component from a set of options. This extracts and executes any special options, then
  configures the component attributes from the remainder"
  [obj ^Map config]
  (if (not (instance? Map config))
    (bad-argument "Configuration options must be a Map"))
  (let [errors (validate-config config synthetic-option-specs)]
    (if (not (empty? errors))
      (bad-argument (str/join "\n" errors))))
  (do-configure obj
    (-> config
       (translate-config-keys)
       (do-synthetic-options obj)
       (do-attribute-options obj)
       ))
  obj)

