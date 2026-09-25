#!/usr/bin/env ruby
require "yaml"

LANGUAGES = %w[de en fr nl].freeze

def leaves(value, prefix = "", output = [])
  return output unless value.is_a?(Hash)

  value.each do |key, child|
    path = prefix.empty? ? key.to_s : "#{prefix}.#{key}"
    child.is_a?(Hash) ? leaves(child, path, output) : output << path
  end

  output
end

bundles = LANGUAGES.to_h do |language|
  path = File.join("src", "main", "resources", "lang", "#{language}.yml")
  abort "[lang] Missing #{path}" unless File.file?(path)
  [language, leaves(YAML.load_file(path)).sort]
rescue Psych::SyntaxError => error
  abort "[lang] Invalid YAML in #{path}: #{error.message}"
end

all_keys = bundles.values.flatten.uniq.sort
errors = bundles.flat_map do |language, keys|
  (all_keys - keys).map { |key| "[lang] #{language}.yml is missing #{key}" }
end

abort errors.join("\n") unless errors.empty?
puts "[lang] Validated #{all_keys.size} keys for #{LANGUAGES.join(", ")}"
