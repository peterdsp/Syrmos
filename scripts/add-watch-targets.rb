require 'xcodeproj'

PROJECT = '/Users/p.dhespollari/git/personal/Syrmos/iosApp/Syrmos.xcodeproj'
project = Xcodeproj::Project.open(PROJECT)

if project.targets.any? { |t| t.name == 'SyrmosWatch' }
  puts 'SyrmosWatch already exists; nothing to do'
  exit 0
end

app = project.targets.find { |t| t.name == 'Syrmos - Athens Rail Times' }
raise 'iOS app target not found' unless app

# --- Targets -------------------------------------------------------------
watch = project.new_target(:application, 'SyrmosWatch', :watchos, '10.0', nil, :swift)
comp  = project.new_target(:app_extension, 'SyrmosWatchComplications', :watchos, '10.0', nil, :swift)

# --- Group + file references --------------------------------------------
group = project.main_group.new_group('SyrmosWatch', 'SyrmosWatch')

def add_swift(project, group, target, filename)
  ref = project.new(Xcodeproj::Project::Object::PBXFileReference)
  ref.path = "SyrmosWatch/#{filename}"
  ref.source_tree = 'SOURCE_ROOT'
  ref.last_known_file_type = 'sourcecode.swift'
  group << ref
  target.source_build_phase.add_file_reference(ref)
  ref
end

def add_resource_ref(project, group, path, type)
  ref = project.new(Xcodeproj::Project::Object::PBXFileReference)
  ref.path = path
  ref.source_tree = 'SOURCE_ROOT'
  ref.last_known_file_type = type
  group << ref
  ref
end

# Watch app sources
%w[SyrmosWatchApp.swift WatchContentView.swift WatchConnectivityProvider.swift WatchModels.swift].each do |f|
  add_swift(project, group, watch, f)
end
# Complication sources (WatchModels shared)
%w[SyrmosWatchComplications.swift WatchModels.swift].each do |f|
  add_swift(project, group, comp, f)
end
# Non-compiled resources for visibility
add_resource_ref(project, group, 'SyrmosWatch/Info.plist', 'text.plist.xml')
add_resource_ref(project, group, 'SyrmosWatch/Complications-Info.plist', 'text.plist.xml')
add_resource_ref(project, group, 'SyrmosWatch/SyrmosWatch.entitlements', 'text.plist.entitlements')

# --- Build settings ------------------------------------------------------
common = {
  'SDKROOT' => 'watchos',
  'WATCHOS_DEPLOYMENT_TARGET' => '10.0',
  'TARGETED_DEVICE_FAMILY' => '4',
  'SWIFT_VERSION' => '5.0',
  'GENERATE_INFOPLIST_FILE' => 'NO',
  'CURRENT_PROJECT_VERSION' => '100',
  'MARKETING_VERSION' => '1.2.0',
  'CODE_SIGN_STYLE' => 'Automatic',
  'CODE_SIGN_ENTITLEMENTS' => 'SyrmosWatch/SyrmosWatch.entitlements',
  'PRODUCT_NAME' => '$(TARGET_NAME)',
  'ENABLE_PREVIEWS' => 'YES',
}

watch_settings = common.merge(
  'PRODUCT_BUNDLE_IDENTIFIER' => 'com.syrmosApp.ios.watchkitapp',
  'INFOPLIST_FILE' => 'SyrmosWatch/Info.plist',
  'SKIP_INSTALL' => 'NO',
)
comp_settings = common.merge(
  'PRODUCT_BUNDLE_IDENTIFIER' => 'com.syrmosApp.ios.watchkitapp.complications',
  'INFOPLIST_FILE' => 'SyrmosWatch/Complications-Info.plist',
  'SKIP_INSTALL' => 'YES',
)

watch.build_configurations.each { |c| c.build_settings.merge!(watch_settings) }
comp.build_configurations.each  { |c| c.build_settings.merge!(comp_settings) }

# --- Embed chain + dependencies -----------------------------------------
# Watch app embeds the complication extension (PlugIns).
watch.add_dependency(comp)
embed_ext = watch.new_copy_files_build_phase('Embed Foundation Extensions')
embed_ext.dst_subfolder_spec = '13' # PlugIns
bf = embed_ext.add_file_reference(comp.product_reference, true)
bf.settings = { 'ATTRIBUTES' => ['RemoveHeadersOnCopy'] }

# iOS app embeds the watch app (Embed Watch Content).
app.add_dependency(watch)
embed_watch = app.new_copy_files_build_phase('Embed Watch Content')
embed_watch.dst_subfolder_spec = '16' # Products Directory
embed_watch.dst_path = '$(CONTENTS_FOLDER_PATH)/Watch'
bf2 = embed_watch.add_file_reference(watch.product_reference, true)
bf2.settings = { 'ATTRIBUTES' => ['RemoveHeadersOnCopy'] }

project.save
puts 'Added SyrmosWatch (app) + SyrmosWatchComplications (extension), embedded and wired.'
puts "targets now: #{project.targets.map(&:name).join(', ')}"
