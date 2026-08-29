(ns kotoba-erp.fi.post-journal-kotoba
  "Host binding for the compiled `kotoba/fi/post_journal.kotoba` slice.

  Everything here is mechanism: find the compiler, run it, import the ESM
  artifact, and translate at the document boundary. No FI rule is repeated --
  the rules are in the `.kotoba`, and this namespace would still be correct if
  they changed.

  Availability is measured by EXECUTING the compiler, never by `which`. There
  is more than one binary called `kotoba` on a developer machine and the other
  one rejects `-M` outright, so a PATH hit is not evidence."
  (:require [clojure.string :as str]
            [kotoba-erp.kotoba-doc :as doc]
            ["node:child_process" :as cp]
            ["node:fs" :as fs]
            ["node:path" :as path]))

(def ^:private source-rel "kotoba/fi/post_journal.kotoba")

(defn repo-root
  "The kotoba-erp checkout root (this file lives under <root>/clj/src/...)."
  []
  (path/resolve (or (.. js/process -env -KOTOBA_ERP_ROOT) (.cwd js/process)) ".."))

(defn candidate-bins []
  (let [root (repo-root)]
    (->> [(.. js/process -env -KOTOBA_BIN)
          ;; west layout: <superproject>/orgs/cloud-itonami/kotoba-erp
          (path/resolve root ".." ".." "kotoba-lang" "amu" "bin" "kotoba")]
         (remove nil?)
         (filterv #(fs/existsSync %)))))

(defn compile-artifact
  "Compile the slice to a `:js-kotoba-v1` ESM artifact.
  Returns {:ok true :path p :bin b} or {:ok false :reason ... :detail ...}."
  []
  (let [root (repo-root)
        src  (path/join root source-rel)
        out  (path/join root "clj" "target" "fi_post_journal.mjs")
        bins (candidate-bins)]
    (cond
      (not (fs/existsSync src))
      {:ok false :reason :missing-source :detail src}

      (empty? bins)
      {:ok false :reason :no-compiler
       :detail (str "no kotoba compiler found; set KOTOBA_BIN "
                    "(a PATH `kotoba` is not assumed -- the homebrew binary of "
                    "that name rejects `-M`)")}

      :else
      (do
        (fs/mkdirSync (path/dirname out) #js {:recursive true})
        (loop [[bin & more] bins errs []]
          (if (nil? bin)
            {:ok false :reason :compile-failed :detail (str/join "\n" errs)}
            (let [r (cp/spawnSync bin
                                  #js ["-M" "compile" src
                                       "--target" "js" "--output" out]
                                  #js {:encoding "utf8"})]
              (if (zero? (or (.-status r) 1))
                {:ok true :path out :bin bin}
                (recur more (conj errs (str bin " exit=" (.-status r) " "
                                            (str/trim (str (.-stderr r))
                                                      ))))))))))))

(defn load-guest
  "Import a compiled artifact. Returns a promise of {:init f :step f}."
  [artifact-path]
  (-> (js/import (str "file://" artifact-path))
      (.then (fn [m]
               (let [mk #(.instantiateKotoba m #js {})]
                 {:required-capabilities (vec (.. m -kotobaArtifact -requiredCapabilities))
                  ;; Fuel is per instance (512 charges), so every call gets a
                  ;; fresh one. Sharing an instance across postings runs out
                  ;; part-way through a document and is not a guest bug.
                  :init (fn [] (doc/doc-> (.init (mk))))
                  :step (fn [state event]
                          (doc/doc-> (.step (mk) (doc/->doc state) (doc/->doc event))))})))))
