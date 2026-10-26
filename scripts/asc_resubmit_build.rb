#!/usr/bin/env ruby
# frozen_string_literal: true

# Replace the build on the current 3.0.0 App Store version with the latest
# processed build, set it to auto-release on approval, and (re)submit for
# review. Used when a newer binary must take over a version that is already in
# review (which requires cancelling the in-flight submission first).
#
# Env: ASC_KEY_ID, ASC_ISSUER_ID, ASC_API_KEY_P8_BASE64, optional VERSION (default 3.0.0).

require "jwt"
require "openssl"
require "base64"
require "json"
require "net/http"
require "uri"

BUNDLE = "com.syrmosApp.ios"
VERSION = ENV.fetch("VERSION", "3.0.0")
BASE = "https://api.appstoreconnect.apple.com"

def jwt
  key = OpenSSL::PKey.read(Base64.decode64(ENV.fetch("ASC_API_KEY_P8_BASE64")))
  JWT.encode({ iss: ENV.fetch("ASC_ISSUER_ID"), iat: Time.now.to_i,
               exp: Time.now.to_i + 18 * 60, aud: "appstoreconnect-v1" },
             key, "ES256", { kid: ENV.fetch("ASC_KEY_ID"), typ: "JWT" })
end

def api(method, path, body = nil)
  uri = URI(path.start_with?("http") ? path : BASE + path)
  klass = { get: Net::HTTP::Get, post: Net::HTTP::Post, patch: Net::HTTP::Patch, delete: Net::HTTP::Delete }.fetch(method)
  r = klass.new(uri)
  r["Authorization"] = "Bearer #{jwt}"
  r["Content-Type"] = "application/json"
  r.body = JSON.dump(body) if body
  res = Net::HTTP.start(uri.host, uri.port, use_ssl: true) { |h| h.request(r) }
  [res.code.to_i, (res.body && !res.body.empty? ? (JSON.parse(res.body) rescue {}) : {})]
end

def ok?(c)
  (200..299).include?(c)
end
def die(m, c = nil, d = nil)
  warn "ERROR: #{m}#{c ? " (HTTP #{c})" : ""}"
  warn JSON.pretty_generate(d) if d && !d.empty?
  exit 1
end

code, data = api(:get, "/v1/apps?filter[bundleId]=#{BUNDLE}")
app_id = data.dig("data", 0, "id") or die("app lookup", code, data)
puts "app: #{app_id}"

# If the version is already APPROVED and waiting (PENDING_DEVELOPER_RELEASE),
# you cannot swap a build onto it. Release it now; the new build ships as 3.0.1.
_, v0 = api(:get, "/v1/apps/#{app_id}/appStoreVersions?limit=10")
cur = (v0["data"] || []).find { |x| x.dig("attributes", "versionString") == VERSION }
state0 = cur&.dig("attributes", "appStoreState")
puts "#{VERSION} current state: #{state0}"
if state0 == "PENDING_DEVELOPER_RELEASE"
  c, d = api(:post, "/v1/appStoreVersionReleaseRequests",
             { data: { type: "appStoreVersionReleaseRequests",
                       relationships: { appStoreVersion: { data: { type: "appStoreVersions", id: cur["id"] } } } } })
  die("release approved version", c, d) unless ok?(c)
  puts "RELEASED #{VERSION} (it was approved + pending). It is going live now."
  puts "NOTE: iOS cannot add the newer build to an approved version; the weather fix ships as 3.0.1."
  exit 0
end

# Diagnose + clear EVERY in-flight review submission. A submission stuck in
# CANCELING holds the version hostage, so delete that one outright; cancel the
# rest. Terminal COMPLETE submissions are left alone.
_, allsubs = api(:get, "/v1/reviewSubmissions?filter[app]=#{app_id}&limit=50")
(allsubs["data"] || []).each do |s|
  st = s.dig("attributes", "state")
  puts "submission #{s["id"]} state=#{st}"
  next if st == "COMPLETE"
  if st == "CANCELING"
    c, _ = api(:delete, "/v1/reviewSubmissions/#{s["id"]}")
    puts "  delete stuck CANCELING -> HTTP #{c}"
  else
    c, _ = api(:patch, "/v1/reviewSubmissions/#{s["id"]}",
               { data: { type: "reviewSubmissions", id: s["id"], attributes: { canceled: true } } })
    puts "  cancel -> HTTP #{c}"
  end
end

# Wait for the version to be editable.
editable = %w[PREPARE_FOR_SUBMISSION DEVELOPER_REJECTED METADATA_REJECTED REJECTED]
version_id = nil
48.times do |i|
  _, v = api(:get, "/v1/apps/#{app_id}/appStoreVersions?limit=10")
  mine = (v["data"] || []).select { |x| x.dig("attributes", "versionString") == VERSION }
  puts "  [#{i}] #{VERSION} states: #{mine.map { |x| x.dig("attributes", "appStoreState") }}" if i % 4 == 0
  hit = mine.find { |x| editable.include?(x.dig("attributes", "appStoreState")) }
  if hit
    version_id = hit["id"]
    puts "editable version #{VERSION}: #{version_id} (#{hit.dig("attributes", "appStoreState")})"
    break
  end
  sleep 5
end
version_id or die("no editable #{VERSION} version")

# Latest processed build for this version train.
_, builds = api(:get, "/v1/builds?filter[app]=#{app_id}&filter[preReleaseVersion.version]=#{VERSION}&sort=-version&limit=20&include=buildBundles")
valid = (builds["data"] || []).find { |b| b.dig("attributes", "processingState") == "VALID" }
valid ||= (builds["data"] || []).first
build_id = valid && valid["id"] or die("no processed build for #{VERSION}", nil, builds)
puts "selecting build: #{valid.dig("attributes", "version")} (#{build_id}, #{valid.dig("attributes", "processingState")})"

# Attach the build + set auto-release.
c, d = api(:patch, "/v1/appStoreVersions/#{version_id}/relationships/build", { data: { type: "builds", id: build_id } })
die("attach build", c, d) unless ok?(c)
c, d = api(:patch, "/v1/appStoreVersions/#{version_id}", { data: { type: "appStoreVersions", id: version_id, attributes: { releaseType: "AFTER_APPROVAL" } } })
die("set auto-release", c, d) unless ok?(c)
puts "build attached + releaseType=AFTER_APPROVAL (auto-release on approval)"

# Resubmit for review (retry if a canceling submission is still clearing).
new_sub = nil
8.times do
  c, d = api(:post, "/v1/reviewSubmissions", { data: { type: "reviewSubmissions", attributes: { platform: "IOS" }, relationships: { app: { data: { type: "apps", id: app_id } } } } })
  (new_sub = d.dig("data", "id")) && break
  puts "create submission HTTP #{c}; retrying..."
  sleep 10
end
new_sub or die("create review submission")
c, d = api(:post, "/v1/reviewSubmissionItems", { data: { type: "reviewSubmissionItems", relationships: { reviewSubmission: { data: { type: "reviewSubmissions", id: new_sub } }, appStoreVersion: { data: { type: "appStoreVersions", id: version_id } } } } })
die("add item", c, d) unless ok?(c)
c, d = api(:patch, "/v1/reviewSubmissions/#{new_sub}", { data: { type: "reviewSubmissions", id: new_sub, attributes: { submitted: true } } })
die("submit", c, d) unless ok?(c)
puts "resubmitted #{VERSION} with the new build, auto-release -> #{d.dig("data", "attributes", "state")}"
puts "DONE"
