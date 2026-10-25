#!/usr/bin/env ruby
# frozen_string_literal: true

# Read-only: list the en-US App Store screenshots for the current version, by
# display type, with each screenshot's processing state and a public download
# URL (so the rendered image can be inspected). No mutations.
#
# Env: ASC_KEY_ID, ASC_ISSUER_ID, ASC_API_KEY_P8_BASE64.

require "jwt"
require "openssl"
require "base64"
require "json"
require "net/http"
require "uri"

BUNDLE = "com.syrmosApp.ios"
LOCALE = "en-US"
BASE   = "https://api.appstoreconnect.apple.com"

def jwt
  key = OpenSSL::PKey.read(Base64.decode64(ENV.fetch("ASC_API_KEY_P8_BASE64")))
  JWT.encode({ iss: ENV.fetch("ASC_ISSUER_ID"), iat: Time.now.to_i,
               exp: Time.now.to_i + 18 * 60, aud: "appstoreconnect-v1" },
             key, "ES256", { kid: ENV.fetch("ASC_KEY_ID"), typ: "JWT" })
end

def get(path)
  uri = URI(path.start_with?("http") ? path : BASE + path)
  r = Net::HTTP::Get.new(uri)
  r["Authorization"] = "Bearer #{jwt}"
  res = Net::HTTP.start(uri.host, uri.port, use_ssl: true) { |h| h.request(r) }
  [res.code.to_i, (res.body && !res.body.empty? ? JSON.parse(res.body) : {})]
end

_, apps = get("/v1/apps?filter[bundleId]=#{BUNDLE}")
app_id = apps.dig("data", 0, "id")
_, vers = get("/v1/apps/#{app_id}/appStoreVersions?limit=10")
ver = (vers["data"] || []).find { |v| v.dig("attributes", "versionString") == "3.0.0" } || vers.dig("data", 0)
ver_id = ver["id"]
puts "version 3.0.0: #{ver_id} (#{ver.dig("attributes", "appStoreState")})"

_, locs = get("/v1/appStoreVersions/#{ver_id}/appStoreVersionLocalizations?limit=50")
loc_id = (locs["data"] || []).find { |l| l.dig("attributes", "locale") == LOCALE }&.fetch("id")

_, sets = get("/v1/appStoreVersionLocalizations/#{loc_id}/appScreenshotSets?limit=50")
(sets["data"] || []).each do |s|
  dt = s.dig("attributes", "screenshotDisplayType")
  _, shots = get("/v1/appScreenshotSets/#{s["id"]}/appScreenshots?limit=50")
  puts "\n== #{dt} (#{(shots["data"] || []).size}) =="
  (shots["data"] || []).each do |sc|
    a = sc["attributes"] || {}
    asset = a["imageAsset"]
    url = if asset
            asset["templateUrl"].to_s.gsub("{w}", asset["width"].to_s)
                                .gsub("{h}", asset["height"].to_s).gsub("{f}", "png")
          else
            "(no imageAsset yet; state=#{a["assetDeliveryState"]&.dig("state")})"
          end
    puts "  #{a["fileName"]}  state=#{a.dig("assetDeliveryState", "state")}  #{url}"
  end
end
