package de.rhm176.build.model.changelog;

import de.rhm176.build.model.changelog.Git.RawCommit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChangelogGenerator {
    private static final Pattern PR_MERGE = Pattern.compile("^Merge pull request #(\\d+) from \\S+.*$");
    private static final Pattern NOISE = Pattern.compile("^(?:fixup!|squash!|amend!|Merge ).*$");
    private static final Set<String> KNOWN_TYPES =
            Set.of("feat", "fix", "perf", "refactor", "revert", "docs", "style", "test", "tests", "build", "ci", "chore");
    private static final int MAX_LISTED_SKIPS = 10;

    private ChangelogGenerator() {}

    private record Entry(String hash, String subject, ConventionalCommit commit) {}

    public static ChangelogResult generate(ChangelogOptions o) {
        if (o.repository == null) throw new IllegalArgumentException("ChangelogOptions.repository is not set");
        if (o.version == null || o.version.isBlank()) throw new IllegalArgumentException("ChangelogOptions.version is not set");

        Git git = new Git(o.repository);
        if (!git.isWorkTree()) {
            throw new ChangelogException(o.repository + " is not inside a git repository, so no changelog can be generated.");
        }
        if (git.isShallow()) {
            throw new ChangelogException("This is a shallow clone, so the history and release tags needed for the changelog are "
                    + "missing. Fetch everything first: `git fetch --unshallow --tags` (in GitHub Actions: "
                    + "`actions/checkout` with `fetch-depth: 0`).");
        }
        String head = git.resolve("HEAD").orElseThrow(() -> new ChangelogException("The repository has no commits yet."));

        String tag = o.tagPrefix + o.version;
        Optional<String> tagCommit = git.resolve("refs/tags/" + tag);
        boolean tagElsewhere = tagCommit.isPresent() && !tagCommit.get().equals(head);

        List<String> warnings = new ArrayList<>();
        if (tagElsewhere) {
            warnings.add("Tag " + tag + " already exists on " + shortHash(tagCommit.get()) + " but you are releasing "
                    + shortHash(head) + ". Did you forget to bump the version?");
        }

        String previousTag;
        if (o.since != null && !o.since.isBlank()) {
            previousTag = o.since.strip();
        } else {
            previousTag = findPreviousTag(git, o, tag);
        }
        String base = null;
        if (previousTag != null) {
            base = git.resolve(o.since != null && !o.since.isBlank() ? previousTag : "refs/tags/" + previousTag)
                    .orElseThrow(() -> new ChangelogException("Cannot find '" + o.since + "'."));
        }
        boolean firstRelease = previousTag == null;

        List<RawCommit> commits = git.log(base == null ? "HEAD" : base + "..HEAD");
        List<Entry> entries = collectEntries(git, commits, o.includeUnconventional, warnings);

        Map<String, List<Entry>> bySection = new LinkedHashMap<>();
        for (Section section : o.sections) bySection.put(section.type(), new ArrayList<>());
        List<Entry> breaking = new ArrayList<>();
        List<Entry> other = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Set<String> unknownTypes = new java.util.TreeSet<>();
        for (Entry e : entries) {
            ConventionalCommit c = e.commit();
            if (c == null) {
                if (o.includeUnconventional) other.add(e);
                else skipped.add(shortHash(e.hash()) + " " + e.subject());
            } else if (c.breaking()) {
                breaking.add(e);
            } else if (bySection.containsKey(c.type())) {
                bySection.get(c.type()).add(e);
            } else if (!KNOWN_TYPES.contains(c.type())) {
                unknownTypes.add(c.type());
            }
        }
        if (!skipped.isEmpty()) {
            StringBuilder message = new StringBuilder(skipped.size() + " commit(s) are not conventional commits and were left out"
                    + " (use feat:/fix:/... or set includeUnconventional):");
            for (int i = 0; i < Math.min(skipped.size(), MAX_LISTED_SKIPS); i++) message.append("\n    ").append(skipped.get(i));
            if (skipped.size() > MAX_LISTED_SKIPS) message.append("\n    ... and ").append(skipped.size() - MAX_LISTED_SKIPS).append(" more");
            warnings.add(message.toString());
        }
        if (!unknownTypes.isEmpty()) {
            warnings.add("Unknown commit type(s) " + unknownTypes + " were left out. Add them with"
                    + " modPublishing.changelog { section(\"type\", \"Title\") } to show them.");
        }

        RepoLinks links = RepoLinks.parse(o.repositoryUrl).or(() -> git.remoteUrl().flatMap(RepoLinks::parse)).orElse(null);

        StringBuilder md = new StringBuilder();
        int listed = 0;
        if (firstRelease) md.append("\uD83C\uDF89 **First release!**\n\n");
        if (!breaking.isEmpty()) {
            appendSection(md, o.breakingTitle, breaking, links, true);
            listed += breaking.size();
        }
        for (Section section : o.sections) {
            List<Entry> list = bySection.get(section.type());
            if (list.isEmpty()) continue;
            appendSection(md, section.title(), list, links, false);
            listed += list.size();
        }
        if (!other.isEmpty()) {
            appendSection(md, o.otherTitle, other, links, false);
            listed += other.size();
        }
        if (listed == 0 && !firstRelease) md.append("_No user-facing changes in this release._\n\n");
        if (links != null) {
            if (firstRelease) md.append("**All changes**: [`").append(tag).append("`](").append(links.history(tag)).append(")\n");
            else md.append("**Full Changelog**: [`").append(previousTag).append("...").append(tag).append("`](")
                    .append(links.compare(previousTag, tag)).append(")\n");
        }

        String suggested = o.since == null ? suggestVersion(o.version, previousTag == null ? null : previousTag.substring(
                Math.min(previousTag.length(), o.tagPrefix.length())), entries) : null;
        return new ChangelogResult(md.toString().strip() + "\n", tag, previousTag, firstRelease, listed, suggested, tagElsewhere,
                List.copyOf(warnings));
    }

    private static String findPreviousTag(Git git, ChangelogOptions o, String currentTag) {
        Optional<SemVer> current = SemVer.parse(o.version);
        String best = null;
        SemVer bestVersion = null;
        for (String candidate : git.tagsMerged("HEAD")) {
            if (!candidate.startsWith(o.tagPrefix) || candidate.equals(currentTag)) continue;
            Optional<SemVer> version = SemVer.parse(candidate.substring(o.tagPrefix.length()));
            if (version.isEmpty()) continue;
            if (current.isPresent()) {
                if (!current.get().isPrerelease() && version.get().isPrerelease()) continue;
                if (version.get().compareTo(current.get()) >= 0) continue;
            }
            if (bestVersion == null || version.get().compareTo(bestVersion) > 0) {
                best = candidate;
                bestVersion = version.get();
            }
        }
        return best;
    }

    private static List<Entry> collectEntries(Git git, List<RawCommit> commits, boolean includeUnconventional, List<String> warnings) {
        Set<String> covered = new HashSet<>();
        Map<String, Entry> prMerges = new HashMap<>();
        for (RawCommit commit : commits) {
            if (commit.parents().size() < 2) continue;
            Matcher merge = PR_MERGE.matcher(commit.subject());
            if (!merge.matches()) continue;
            Optional<ConventionalCommit> title = ConventionalCommit.parse(commit.body());
            if (title.isEmpty()) continue;
            String titleLine = commit.body().split("\n", 2)[0].strip();
            prMerges.put(commit.hash(), new Entry(commit.hash(), titleLine, title.get().withPullRequest(Integer.parseInt(merge.group(1)))));
            covered.addAll(git.revList(commit.parents().get(0) + ".." + commit.parents().get(1)));
        }

        List<Entry> entries = new ArrayList<>();
        for (RawCommit commit : commits) {
            if (commit.parents().size() >= 2) {
                Entry pr = prMerges.get(commit.hash());
                if (pr != null) entries.add(pr);
                continue;
            }
            if (covered.contains(commit.hash())) continue;
            String subject = commit.subject();
            Optional<ConventionalCommit> parsed = ConventionalCommit.parse(commit.message());
            if (parsed.isPresent()) {
                entries.add(new Entry(commit.hash(), subject, parsed.get()));
            } else if (!NOISE.matcher(subject).matches()) {
                entries.add(new Entry(commit.hash(), subject, null));
            }
        }
        return cancelReverts(entries);
    }

    private static List<Entry> cancelReverts(List<Entry> entries) {
        Set<Entry> dropped = new HashSet<>();
        for (Entry revert : entries) {
            ConventionalCommit c = revert.commit();
            if (c == null || !c.type().equals("revert") || dropped.contains(revert)) continue;
            for (Entry target : entries) {
                if (target == revert || dropped.contains(target)) continue;
                boolean byHash = c.revertedHash() != null && target.hash().startsWith(c.revertedHash());
                boolean bySubject = target.subject().equals(unquote(c.description()));
                if (byHash || bySubject) {
                    dropped.add(revert);
                    dropped.add(target);
                    break;
                }
            }
        }
        List<Entry> kept = new ArrayList<>(entries);
        kept.removeIf(dropped::contains);
        return kept;
    }

    private static String unquote(String text) {
        String t = text.strip();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1);
        return t;
    }

    private static void appendSection(StringBuilder md, String title, List<Entry> entries, RepoLinks links, boolean withNotes) {
        md.append("### ").append(title).append("\n\n");
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(e -> e.commit() == null || e.commit().scope() == null ? "" : e.commit().scope().toLowerCase(Locale.ROOT)));
        for (Entry e : sorted) {
            md.append(renderLine(e, links)).append('\n');
            if (withNotes && e.commit() != null) {
                for (String note : e.commit().breakingNotes()) {
                    for (String line : note.split("\n")) md.append("  > ").append(line.strip()).append('\n');
                }
            }
        }
        md.append('\n');
    }

    private static String renderLine(Entry e, RepoLinks links) {
        ConventionalCommit c = e.commit();
        StringBuilder line = new StringBuilder("- ");
        Integer pr;
        if (c == null) {
            line.append(capitalize(e.subject()));
            pr = null;
        } else {
            if (c.scope() != null) line.append("**").append(c.scope()).append(":** ");
            line.append(capitalize(c.description()));
            pr = c.pullRequest();
        }
        if (pr != null) {
            line.append(links != null ? " ([#" + pr + "](" + links.pullRequest(pr) + "))" : " (#" + pr + ")");
        } else if (links != null) {
            line.append(" ([`").append(shortHash(e.hash())).append("`](").append(links.commit(e.hash())).append("))");
        }
        return line.toString();
    }

    private static String capitalize(String text) {
        String t = text.strip();
        if (t.endsWith(".")) t = t.substring(0, t.length() - 1);
        if (t.isEmpty()) return t;
        if (Character.isLowerCase(t.charAt(0)) && (t.length() == 1 || !Character.isUpperCase(t.charAt(1)))) {
            return Character.toUpperCase(t.charAt(0)) + t.substring(1);
        }
        return t;
    }

    private static String shortHash(String hash) {
        return hash.substring(0, Math.min(7, hash.length()));
    }

    private static String suggestVersion(String currentVersion, String previousVersionText, List<Entry> entries) {
        Optional<SemVer> current = SemVer.parse(currentVersion);
        Optional<SemVer> previous = SemVer.parse(previousVersionText);
        if (current.isEmpty() || previous.isEmpty() || current.get().isPrerelease() || previous.get().isPrerelease()) return null;
        SemVer.Bump bump = SemVer.Bump.NONE;
        for (Entry e : entries) {
            ConventionalCommit c = e.commit();
            if (c == null) continue;
            SemVer.Bump b;
            if (c.breaking()) b = previous.get().major() == 0 ? SemVer.Bump.MINOR : SemVer.Bump.MAJOR;
            else if (c.type().equals("feat")) b = SemVer.Bump.MINOR;
            else if (c.type().equals("fix") || c.type().equals("perf")) b = SemVer.Bump.PATCH;
            else b = SemVer.Bump.NONE;
            if (b.compareTo(bump) > 0) bump = b;
        }
        return bump == SemVer.Bump.NONE ? null : previous.get().next(bump).toString();
    }
}
