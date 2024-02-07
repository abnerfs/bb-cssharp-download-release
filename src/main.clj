(ns main
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [babashka.fs :as fs]))

(def user "roflmuffin")
(def repo "CounterStrikeSharp")

(defn get-latest-release
  [user repo]
  (http/get (format "https://api.github.com/repos/%s/%s/releases/latest" user repo)))

(defn get-assets
  [user repo]
  (let [assets (-> (get-latest-release user repo)
                   :body
                   (json/parse-string keyword)
                   :assets)]
    (->> assets
         (map #(select-keys % [:name :browser_download_url])))))


(defn choose-asset
  [assets platform]
  (->> assets
       (filter (comp (every-pred (complement #(string/includes? % "with-runtime"))
                                 #(string/includes? % platform))
                     :name))
       first))

(defn download-file
  [url file-dest]
  (io/copy
   (:body (http/get url {:as :stream}))
   (io/file file-dest)))

(defn fetch+download-latest-release
  [platform]
  (let [{:keys [name browser_download_url]
         :as file} (-> (get-assets user repo)
                       (choose-asset platform))]
    (download-file browser_download_url name)
    file))

(defn unzip-file
  [file dest-folder]
  (fs/unzip file dest-folder)
  file)

(defn dll-location
  [folder]
  (format "%s/addons/counterstrikesharp/api/CounterStrikeSharp.API.dll" folder))

(def dest-folder "dest")

(defn -main
  []
  (when (fs/exists? dest-folder)
    (fs/delete-tree dest-folder))

  (fs/create-dir dest-folder)

  (-> (fetch+download-latest-release "linux")
      :name
      (unzip-file dest-folder)
      fs/delete))
