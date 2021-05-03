(ns martian.openapi-test
  (:require [martian.test-helpers #?@(:clj [:refer [json-resource]]
                                      :cljs [:refer-macros [json-resource]])]
            [schema-tools.core :as st]
            [clojure.test :refer [deftest is testing]]
            [schema.core :as s]
            [martian.openapi :refer [openapi->handlers]]))

(def openapi-json
  (json-resource "openapi.json"))

(def openapi-2-json
  (json-resource "openapi2.json"))

(def jira-openapi-v3-json
  (json-resource "jira-openapi-v3.json"))

(deftest openapi-sanity-check
  (testing "parses each handler"
    (is (= {:summary        "Update an existing pet"
            :description    "Update an existing pet by Id"
            :method         :put
            :produces       ["application/json"]
            :path-schema    {}
            :query-schema   {}
            :form-schema    {}
            :path-parts     ["/pet"]
            :headers-schema {}
            :consumes       ["application/json"]
            :body-schema
            {:body
             {(s/optional-key :id)       s/Int
              :name                      s/Str
              (s/optional-key :category) {(s/optional-key :id)   s/Int
                                          (s/optional-key :name) s/Str}
              :photoUrls                 [s/Str]
              (s/optional-key :tags)     [{(s/optional-key :id)   s/Int
                                           (s/optional-key :name) s/Str}]
              (s/optional-key :status)   (s/enum "sold" "pending" "available")}}
            :route-name     :update-pet
            :response-schemas
            [{:status (s/eq 200)
              :body
              {(s/optional-key :id)       s/Int
               :name                      s/Str
               (s/optional-key :category) {(s/optional-key :id)   s/Int
                                           (s/optional-key :name) s/Str}
               :photoUrls                 [s/Str]
               (s/optional-key :tags)     [{(s/optional-key :id)   s/Int
                                            (s/optional-key :name) s/Str}]
               (s/optional-key :status)   (s/enum "sold" "pending" "available")}}
             {:status (s/eq 400) :body nil}
             {:status (s/eq 404) :body nil}
             {:status (s/eq 405) :body nil}]}

           (-> openapi-json
               (openapi->handlers {:encodes ["application/json" "application/octet-stream"]
                                   :decodes ["application/json" "application/octet-stream"]})
               (->> (filter #(= (:route-name %) :update-pet)))
               first
               (dissoc :openapi-definition)))))

  (testing "chooses the first supported content-type"
    (is (= {:consumes ["application/xml"]
            :produces ["application/json"]}

           (-> openapi-json
               (openapi->handlers {:encodes ["application/xml"]
                                   :decodes ["application/json"]})
               (->> (filter #(= (:route-name %) :update-pet)))
               first
               (select-keys [:consumes :produces]))))))

(deftest openapi-parameters-test
  (testing "parses parameters"
    (is (= {:description nil,
            :method :get,
            :produces ["application/json"],
            :path-schema {:projectId s/Str},
            :query-schema {(s/optional-key :key) (st/default s/Str "some-default-key")},
            :form-schema {},
            :path-parts ["/project/" :projectKey],
            :headers-schema {},
            :consumes [nil],
            :summary "Get specific values from a configuration for a specific project",
            :body-schema nil,
            :route-name :get-project-configuration,
            :response-schemas
            [{:status (s/eq 200), :body s/Str}
             {:status (s/eq 403), :body nil}
             {:status (s/eq 404), :body nil}]}
           (-> openapi-2-json
               (openapi->handlers {:encodes ["application/json" "application/octet-stream"]
                                   :decodes ["application/json" "application/octet-stream"]})
               (->> (filter #(= (:route-name %) :get-project-configuration)))
               first
               (dissoc :openapi-definition))))))

(deftest openapi-param-ref-test
  (is (= {:body-schema      nil
          :consumes         [nil]
          :description      nil
          :form-schema      {}
          :headers-schema   {}
          :method           :get
          :path-parts       ["/some-operation"]
          :path-schema      {}
          :produces         []
          :query-schema     {{:k :direct-param-in-query} s/Any}
          :response-schemas []
          :route-name       :some-operation
          :summary          nil}
         (-> {:components
              {:parameters
               {:RefParam {:name "ref-param-name"
                           :in "query"}}}
              :paths
              {"/some-operation"
               {:get
                {:operationId "someOperation"
                 :parameters  [{:$ref "#/components/parameters/RefParam"}
                               {:name "direct-param-in-query"
                                :in "query"}]}}}}
             (clojure.walk/stringify-keys)
             (openapi->handlers {:encodes ["some/mime-type"]
                                 :decodes ["some/mime-type"]})
             first
             (dissoc :openapi-definition)
             (doto clojure.pprint/pprint)
             ))))

;(openapi-param-ref-test)

(deftest jira-openapi-v3-test
  (is (= 410
         (-> jira-openapi-v3-json
             (openapi->handlers {:encodes ["json"]
                                 :decodes ["json"]})
             count))))

(comment

  (def xero-json
    (-> (json-resource "xero_accounting.json")
        (update "paths" select-keys ["/Reports/ProfitAndLoss"])))

  (martian.openapi/openapi-schema? xero-json)

  (-> xero-json
      (clojure.walk/keywordize-keys)
      (get :components)
      (#'martian.openapi/lookup-ref "#/components/parameters/FromDate")
      )

  (-> xero-json
      ;(get "paths")
      (openapi->handlers {:encodes ["application/json"]
                          :decodes ["application/json"]})
      doall
      ;(martian.core/handler-for :get-report-profit-and-loss)
      ;(->> (def xero-handlers))
      )


  (-> xero-json
      (get-in ["components" "parameters" "FromDate"]))
  (def jira-api
    (-> jira-openapi-v3-json
        (openapi->handlers {:encodes ["application/json"]
                            :decodes ["application/json"]})))
  (first jira-api)
  )
