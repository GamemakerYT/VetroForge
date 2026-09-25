#!/usr/bin/env ruby
# Reports player-facing string literals that still bypass the language system.

root = File.expand_path("../src/main/java", __dir__)
patterns = [
  /sendMessage\s*\(\s*"/,
  /sendActionBar\s*\(\s*"/,
  /setDisplayName\s*\(\s*"/
]

hits = Hash.new { |hash, key| hash[key] = [] }
Dir.glob(File.join(root, "**", "*.java")).sort.each do |file|
  File.readlines(file, chomp: true).each_with_index do |line, index|
    next unless patterns.any? { |pattern| line.match?(pattern) }
    relative = file.delete_prefix(root + "/")
    hits[relative] << [index + 1, line.strip]
  end
end

puts "[legacy-i18n] #{hits.values.sum(&:size)} direct player-facing literals in #{hits.size} files"
hits.sort_by { |file, lines| [-lines.size, file] }.each do |file, lines|
  puts format("%4d  %s", lines.size, file)
end
