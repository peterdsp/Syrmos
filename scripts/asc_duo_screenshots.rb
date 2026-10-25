#!/usr/bin/env ruby
# frozen_string_literal: true

# Replace ONLY the iPhone Duo screenshot set on the current (already submitted)
# App Store version, preserving every other device's screenshots, then resubmit.
#
# App Store Connect locks screenshots once a version is submitted for review, so
# the flow is: cancel the in-flight review submission -> wait until the version
# is editable -> delete the Duo screenshots -> upload the new Duo PNGs -> submit
# again. Other devices (iPhone 6.5", iPad, Watch) are never touched.
#
# Env: ASC_KEY_ID, ASC_ISSUER_ID, ASC_API_KEY_P8_BASE64 (base64 of the .p8).
# Screenshots: fastlane/screenshots/en-US/*.png at the Duo native size.

require "jwt"
require "openssl"
require "base64"
require "json"
require "digest"
require "net/http"
require "uri"

BUNDLE   = "com.syrmosApp.ios"
DISPLAY  = "APP_IPHONE_DUO"
LOCALE   = "en-US"
BASE     = "https://api.appstoreconnect.apple.com"
SHOT_DIR = File.expand_path("../fastlane/screenshots/#{LOCALE}", __dir__)
# RESUBMIT_ONLY=true skips cancel/delete/upload and just resubmits the version
# whose screenshots were already uploaded by a prior run.
SKIP_UPLOAD = ENV["RESUBMIT_ONLY"] == "true"

def jwt
  kid = ENV.fetch("ASC_KEY_ID")
  iss = ENV.fetch("ASC_ISSUER_ID")
  key = OpenSSL::PKey.read(Base64.decode64(ENV.fetch("ASC_API_KEY_P8_BASE64")))
  JWT.encode({ iss: iss, iat: Time.now.to_i, exp: Time.now.to_i + 18 * 60,
               aud: "appstoreconnect-v1" }, key, "ES256", { kid: kid, typ: "JWT" })
end

# API request against the ASC host. Returns [code, parsed_json_hash].
def api(method, path, body = nil)
  uri = URI(path.start_with?("http") ? path : BASE + path)
  klass = { get: Net::HTTP::Get, post: Net::HTTP::Post,
            patch: Net::HTTP::Patch, delete: Net::HTTP::Delete }.fetch(method)
  r = klass.new(uri)
  r["Authorization"] = "Bearer #{jwt}"
  r["Content-Type"] = "application/json"
  r.body = JSON.dump(body) if body
  res = Net::HTTP.start(uri.host, uri.port, use_ssl: true) { |h| h.request(r) }
  parsed = res.body && !res.body.empty? ? (JSON.parse(res.body) rescue {}) : {}
  [res.code.to_i, parsed]
end

def ok?(code)
  (200..299).include?(code)
end

def die(msg, code = nil, data = nil)
  warn "ERROR: #{msg}#{code ? " (HTTP #{code})" : ""}"
  warn JSON.pretty_generate(data) if data && !data.empty?
  exit 1
end

# --- resolve the app -------------------------------------------------------
code, data = api(:get, "/v1/apps?filter[bundleId]=#{BUNDLE}")
die("app lookup", code, data) unless ok?(code)
app_id = data.dig("data", 0, "id") or die("app not found", code, data)
puts "app: #{app_id}"

# --- cancel any in-flight review submission --------------------------------
code, data = api(:get, "/v1/reviewSubmissions?filter[app]=#{app_id}" \
                       "&filter[state]=READY_FOR_REVIEW,WAITING_FOR_REVIEW,IN_REVIEW,UNRESOLVED_ISSUES")
die("list submissions", code, data) unless ok?(code)
subs = data["data"] || []
puts "in-flight submissions: #{subs.map { |s| [s["id"], s.dig("attributes", "state")] }}"
subs.each do |s|
  c, d = api(:patch, "/v1/reviewSubmissions/#{s["id"]}",
             { data: { type: "reviewSubmissions", id: s["id"], attributes: { canceled: true } } })
  if ok?(c)
    puts "canceled #{s["id"]} -> #{d.dig("data", "attributes", "state")}"
  else
    # Already canceling/terminal (e.g. ENTITY_STATE_INVALID from a prior run):
    # not fatal, the editable-version check below is the real gate.
    puts "cancel #{s["id"]} returned HTTP #{c}; continuing (likely already canceling)"
  end
end

# --- wait until a version is editable --------------------------------------
EDITABLE = %w[PREPARE_FOR_SUBMISSION DEVELOPER_REJECTED METADATA_REJECTED REJECTED
              INVALID_BINARY].freeze
version_id = nil
30.times do
  code, data = api(:get, "/v1/apps/#{app_id}/appStoreVersions?limit=10")
  die("list versions", code, data) unless ok?(code)
  v = (data["data"] || []).find { |x| EDITABLE.include?(x.dig("attributes", "appStoreState")) }
  if v
    version_id = v["id"]
    puts "editable version: #{version_id} (#{v.dig("attributes", "versionString")}, #{v.dig("attributes", "appStoreState")})"
    break
  end
  sleep 5
end
version_id or die("no editable version appeared after cancel")

# --- locate the en-US Duo screenshot set -----------------------------------
code, data = api(:get, "/v1/appStoreVersions/#{version_id}/appStoreVersionLocalizations?limit=50")
die("list localizations", code, data) unless ok?(code)
loc = (data["data"] || []).find { |l| l.dig("attributes", "locale") == LOCALE }
loc_id = loc && loc["id"] or die("#{LOCALE} localization not found", code, data)
puts "localization #{LOCALE}: #{loc_id}"

code, data = api(:get, "/v1/appStoreVersionLocalizations/#{loc_id}/appScreenshotSets?limit=50")
die("list screenshot sets", code, data) unless ok?(code)
set = (data["data"] || []).find { |s| s.dig("attributes", "screenshotDisplayType") == DISPLAY }
if set
  set_id = set["id"]
  puts "existing #{DISPLAY} set: #{set_id}"
else
  c, d = api(:post, "/v1/appScreenshotSets",
             { data: { type: "appScreenshotSets", attributes: { screenshotDisplayType: DISPLAY },
                       relationships: { appStoreVersionLocalization: { data: { type: "appStoreVersionLocalizations", id: loc_id } } } } })
  die("create screenshot set", c, d) unless ok?(c)
  set_id = d.dig("data", "id")
  puts "created #{DISPLAY} set: #{set_id}"
end

unless SKIP_UPLOAD
# --- delete existing Duo screenshots ---------------------------------------
code, data = api(:get, "/v1/appScreenshotSets/#{set_id}/appScreenshots?limit=50")
die("list screenshots", code, data) unless ok?(code)
(data["data"] || []).each do |sc|
  c, _ = api(:delete, "/v1/appScreenshots/#{sc["id"]}")
  die("delete screenshot #{sc["id"]}", c) unless ok?(c)
  puts "deleted old screenshot #{sc["id"]}"
end

# --- upload the new Duo screenshots ----------------------------------------
files = Dir.glob(File.join(SHOT_DIR, "*.png")).sort
die("no PNGs in #{SHOT_DIR}") if files.empty?
files.each do |path|
  bytes = File.binread(path)
  name  = File.basename(path)
  c, d = api(:post, "/v1/appScreenshots",
             { data: { type: "appScreenshots", attributes: { fileName: name, fileSize: bytes.bytesize },
                       relationships: { appScreenshotSet: { data: { type: "appScreenshotSets", id: set_id } } } } })
  die("reserve #{name}", c, d) unless ok?(c)
  sid = d.dig("data", "id")
  ops = d.dig("data", "attributes", "uploadOperations") || []
  die("no uploadOperations for #{name}", c, d) if ops.empty?
  ops.each do |op|
    u = URI(op["url"])
    put = Net::HTTP::Put.new(u)
    (op["requestHeaders"] || []).each { |h| put[h["name"]] = h["value"] }
    put.body = bytes.byteslice(op["offset"].to_i, op["length"].to_i)
    r = Net::HTTP.start(u.host, u.port, use_ssl: u.scheme == "https") { |h| h.request(put) }
    die("upload chunk #{name} -> #{r.code}") unless ok?(r.code.to_i)
  end
  c, d = api(:patch, "/v1/appScreenshots/#{sid}",
             { data: { type: "appScreenshots", id: sid,
                       attributes: { uploaded: true, sourceFileChecksum: Digest::MD5.hexdigest(bytes) } } })
  die("commit #{name}", c, d) unless ok?(c)
  puts "uploaded #{name} (#{bytes.bytesize} bytes) -> #{sid}"
end
end # unless SKIP_UPLOAD

# --- wait for screenshots to finish post-processing ------------------------
# Resubmitting while an asset is still processing fails with
# STATE_ERROR.ASSET_IN_POST_PROCESSING, so poll until all are COMPLETE.
puts "waiting for screenshot processing..."
processed = false
120.times do
  code, data = api(:get, "/v1/appScreenshotSets/#{set_id}/appScreenshots?limit=50")
  states = (data["data"] || []).map { |s| s.dig("attributes", "assetDeliveryState", "state") }
  die("a screenshot failed processing: #{states}") if states.include?("FAILED")
  if !states.empty? && states.all? { |s| s == "COMPLETE" }
    processed = true
    puts "all screenshots COMPLETE"
    break
  end
  sleep 5
end
die("screenshots still processing after timeout") unless processed

# --- resubmit for review ---------------------------------------------------
new_sub = nil
8.times do
  c, d = api(:post, "/v1/reviewSubmissions",
             { data: { type: "reviewSubmissions", attributes: { platform: "IOS" },
                       relationships: { app: { data: { type: "apps", id: app_id } } } } })
  if ok?(c)
    new_sub = d.dig("data", "id")
    break
  end
  # A still-canceling prior submission can block a new one; wait and retry.
  puts "create review submission HTTP #{c}; retrying..."
  sleep 10
end
die("create review submission (after retries)") unless new_sub
c, d = api(:post, "/v1/reviewSubmissionItems",
           { data: { type: "reviewSubmissionItems",
                     relationships: { reviewSubmission: { data: { type: "reviewSubmissions", id: new_sub } },
                                      appStoreVersion: { data: { type: "appStoreVersions", id: version_id } } } } })
die("add submission item", c, d) unless ok?(c)
c, d = api(:patch, "/v1/reviewSubmissions/#{new_sub}",
           { data: { type: "reviewSubmissions", id: new_sub, attributes: { submitted: true } } })
die("submit", c, d) unless ok?(c)
puts "resubmitted for review -> #{d.dig("data", "attributes", "state")}"
puts "DONE: Duo screenshots replaced and version resubmitted."
