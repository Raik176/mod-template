import de.rhm176.build.model.changelog.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

class ChangelogTests {
    static final class Repo {
        final Path dir;

        Repo(Path dir) throws Exception {
            this.dir = dir;
            git("init", "-q", "-b", "main");
        }

        String git(String... args) throws Exception {
            List<String> cmd = new ArrayList<>(List.of("git", "-c", "commit.gpgsign=false", "-c", "tag.gpgsign=false",
                    "-c", "core.hooksPath=/dev/null"));
            cmd.addAll(List.of(args));
            ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true);
            pb.environment().put("GIT_AUTHOR_NAME", "Test");
            pb.environment().put("GIT_AUTHOR_EMAIL", "test@example.com");
            pb.environment().put("GIT_COMMITTER_NAME", "Test");
            pb.environment().put("GIT_COMMITTER_EMAIL", "test@example.com");
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (p.waitFor() != 0) throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + out);
            return out.strip();
        }

        int counter;

        String commit(String message) throws Exception {
            Files.writeString(dir.resolve("f" + (counter++) + ".txt"), message);
            git("add", "-A");
            git("commit", "-q", "-m", message);
            return git("rev-parse", "HEAD");
        }

        void tag(String name) throws Exception {
            git("tag", name);
        }
    }

    static ChangelogOptions options(Repo repo, String version) {
        ChangelogOptions o = new ChangelogOptions();
        o.repository = repo.dir;
        o.version = version;
        o.repositoryUrl = "https://github.com/rhm176/ExampleMod";
        return o;
    }

    static Repo repo() throws Exception {
        return new Repo(Files.createTempDirectory("changelog-test"));
    }

    static void run() throws Exception {
        semver();
        parser();
        links();
        firstRelease();
        laterRelease();
        tagSelection();
        pullRequests();
        reverts();
        breaking();
        options();
        failures();
    }

    static void semver() {
        eq("semver parse", "1.2.3", SemVer.parse("1.2.3").get().toString());
        eq("semver lenient", "1.2.0", SemVer.parse("1.2").get().toString());
        eq("semver pre", "1.0.0-beta.1", SemVer.parse("1.0.0-beta.1+build.7").get().toString());
        truth("semver junk", SemVer.parse("latest").isEmpty());
        truth("semver 1.10 > 1.9", SemVer.parse("1.10.0").get().compareTo(SemVer.parse("1.9.0").get()) > 0);
        truth("release > pre", SemVer.parse("1.0.0").get().compareTo(SemVer.parse("1.0.0-rc.1").get()) > 0);
        truth("beta.2 < beta.10", SemVer.parse("1.0.0-beta.2").get().compareTo(SemVer.parse("1.0.0-beta.10").get()) < 0);
        truth("alpha < beta", SemVer.parse("1.0.0-alpha").get().compareTo(SemVer.parse("1.0.0-beta").get()) < 0);
        eq("bump minor", "1.3.0", SemVer.parse("1.2.3").get().next(SemVer.Bump.MINOR).toString());
        eq("bump major", "2.0.0", SemVer.parse("1.2.3").get().next(SemVer.Bump.MAJOR).toString());
        eq("channel stable", ReleaseChannel.STABLE, ReleaseChannel.of("1.2.0"));
        eq("channel build meta", ReleaseChannel.STABLE, ReleaseChannel.of("1.2.0+1.21.1"));
        eq("channel beta", ReleaseChannel.BETA, ReleaseChannel.of("1.2.0-beta.1"));
        eq("channel rc", ReleaseChannel.BETA, ReleaseChannel.of("1.2.0-rc.1"));
        eq("channel alpha", ReleaseChannel.ALPHA, ReleaseChannel.of("1.2.0-alpha.3"));
    }

    static ConventionalCommit cc(String message) {
        return ConventionalCommit.parse(message).orElse(null);
    }

    static void parser() {
        ConventionalCommit c = cc("feat(config): add per-world options (#42)");
        eq("type", "feat", c.type());
        eq("scope", "config", c.scope());
        eq("desc strips PR", "add per-world options", c.description());
        eq("PR", 42, c.pullRequest());
        truth("not breaking", !c.breaking());
        eq("no scope", null, cc("fix: crash on start").scope());
        eq("uppercase type", "fix", cc("Fix: crash").type());
        truth("bang breaking", cc("feat(api)!: rename x").breaking());
        ConventionalCommit b = cc("refactor: new format\n\nsome body\n\nBREAKING CHANGE: old files must be\nmigrated by hand\n\nCo-authored-by: A <a@b.c>");
        truth("footer breaking", b.breaking());
        eq("footer note spans lines", List.of("old files must be\nmigrated by hand"), b.breakingNotes());
        truth("hyphen footer", cc("fix: x\n\nBREAKING-CHANGE: yes").breaking());
        truth("plain message", cc("Update README") == null);
        truth("merge message", cc("Merge branch 'main' into feature") == null);
        truth("empty description", cc("fix:") == null);
        eq("git revert", "revert", cc("Revert \"feat: add x\"\n\nThis reverts commit abcdef1234567.").type());
        eq("git revert hash", "abcdef1234567", cc("Revert \"feat: add x\"\n\nThis reverts commit abcdef1234567.").revertedHash());
        eq("scope with slash", "fabric/forge", cc("fix(fabric/forge): x").scope());
        eq("crlf", "fix", cc("fix: x\r\n\r\nbody").type());
    }

    static void links() {
        RepoLinks gh = RepoLinks.parse("https://github.com/rhm176/ExampleMod").get();
        eq("gh slug", "rhm176/ExampleMod", gh.slug());
        eq("gh commit", "https://github.com/rhm176/ExampleMod/commit/abc", gh.commit("abc"));
        eq("gh pr", "https://github.com/rhm176/ExampleMod/pull/3", gh.pullRequest(3));
        eq("gh compare", "https://github.com/rhm176/ExampleMod/compare/v1...v2", gh.compare("v1", "v2"));
        eq("ssh remote", "rhm176/ExampleMod", RepoLinks.parse("git@github.com:rhm176/ExampleMod.git").get().slug());
        eq("https .git", "rhm176/ExampleMod", RepoLinks.parse("https://github.com/rhm176/ExampleMod.git").get().slug());
        eq("deep url", "rhm176/ExampleMod", RepoLinks.parse("https://github.com/rhm176/ExampleMod/issues").get().slug());
        eq("token url", "rhm176/ExampleMod", RepoLinks.parse("https://x-access-token:abc@github.com/rhm176/ExampleMod").get().slug());
        eq("gitlab mr", "https://gitlab.com/a/b/-/merge_requests/4", RepoLinks.parse("https://gitlab.com/a/b").get().pullRequest(4));
        eq("codeberg pr", "https://codeberg.org/a/b/pulls/4", RepoLinks.parse("https://codeberg.org/a/b").get().pullRequest(4));
        truth("junk", RepoLinks.parse("not a url").isEmpty());
        truth("null", RepoLinks.parse(null).isEmpty());
    }

    static void firstRelease() throws Exception {
        Repo r = repo();
        r.commit("Initial commit");
        r.commit("feat: add the thing");
        r.commit("fix(fabric): crash on startup (#3)");
        r.commit("chore: bump gradle");
        r.commit("docs: readme");
        r.commit("perf: faster chunk loading");
        r.commit("wip stuff");
        ChangelogResult res = ChangelogGenerator.generate(options(r, "1.0.0"));
        truth("first release flag", res.firstRelease());
        eq("first release tag", "v1.0.0", res.tag());
        eq("first release has no previous", null, res.previousTag());
        String md = res.markdown();
        truth("first header", md.startsWith("\uD83C\uDF89 **First release!**"));
        truth("feature listed", md.contains("- Add the thing"));
        truth("scoped fix listed", md.contains("- **fabric:** Crash on startup ([#3](https://github.com/rhm176/ExampleMod/pull/3))"));
        truth("perf listed", md.contains("### \u26A1 Performance"));
        truth("chore hidden", !md.contains("gradle"));
        truth("docs hidden", !md.contains("readme"));
        truth("unconventional hidden", !md.contains("wip") && !md.contains("Initial commit"));
        truth("hash link without PR", md.contains("](https://github.com/rhm176/ExampleMod/commit/"));
        truth("history link", md.contains("https://github.com/rhm176/ExampleMod/commits/v1.0.0"));
        eq("entries", 3, res.entries());
        truth("warns about skipped commits", res.warnings().stream().anyMatch(w -> w.contains("not conventional") && w.contains("wip stuff")));
        eq("no suggestion on first release", null, res.suggestedVersion());

        Repo plain = repo();
        plain.commit("Initial commit");
        String only = ChangelogGenerator.generate(options(plain, "1.0.0")).markdown();
        truth("empty first release still says something", only.contains("First release!"));
    }

    static void laterRelease() throws Exception {
        Repo r = repo();
        r.commit("feat: old feature");
        r.tag("v1.0.0");
        r.commit("fix: new fix");
        r.commit("feat(ui): new screen");
        ChangelogResult res = ChangelogGenerator.generate(options(r, "1.1.0"));
        truth("not first", !res.firstRelease());
        eq("previous tag", "v1.0.0", res.previousTag());
        String md = res.markdown();
        truth("new fix listed", md.contains("New fix"));
        truth("old feature absent", !md.contains("Old feature"));
        truth("no first banner", !md.contains("First release"));
        truth("compare link", md.contains("(https://github.com/rhm176/ExampleMod/compare/v1.0.0...v1.1.0)"));
        eq("suggested minor", "1.1.0", res.suggestedVersion());
        truth("fix section before nothing odd", md.indexOf("Features") < md.indexOf("Bug Fixes"));

        Repo quiet = repo();
        quiet.commit("feat: a");
        quiet.tag("v1.0.0");
        quiet.commit("chore: tidy");
        ChangelogResult q = ChangelogGenerator.generate(options(quiet, "1.0.1"));
        truth("quiet release", q.markdown().contains("No user-facing changes"));
        eq("no suggestion for chores", null, q.suggestedVersion());

        Repo tagged = repo();
        tagged.commit("feat: a");
        tagged.tag("v1.0.0");
        tagged.commit("fix: b");
        tagged.tag("v1.0.1");
        ChangelogResult t = ChangelogGenerator.generate(options(tagged, "1.0.1"));
        eq("current tag excluded", "v1.0.0", t.previousTag());
        truth("tag at HEAD is fine", !t.tagElsewhere());
        truth("describes b", t.markdown().contains("B") && !t.markdown().contains("- A"));

        tagged.commit("fix: c");
        ChangelogResult reused = ChangelogGenerator.generate(options(tagged, "1.0.1"));
        truth("reused version detected", reused.tagElsewhere());
        truth("reused version warning", reused.warnings().stream().anyMatch(w -> w.contains("forget to bump")));

        String firstSha = r.git("rev-list", "--max-parents=0", "HEAD");
        ChangelogOptions since = options(r, "1.1.0");
        since.since = firstSha;
        ChangelogResult s = ChangelogGenerator.generate(since);
        truth("since not first", !s.firstRelease());
        truth("since lists all later", s.markdown().contains("New fix") && s.markdown().contains("New screen"));
        eq("since no suggestion", null, s.suggestedVersion());
    }

    static void tagSelection() throws Exception {
        Repo r = repo();
        r.commit("feat: one");
        r.tag("v1.0.0");
        r.commit("feat: two");
        r.tag("v1.1.0-beta.1");
        r.commit("fix: three");
        r.tag("v1.1.0-beta.2");
        r.commit("fix: four");
        r.tag("not-a-version");
        r.tag("v1.1");
        ChangelogResult stable = ChangelogGenerator.generate(options(r, "1.2.0"));
        eq("stable skips betas, takes 1.1", "v1.1", stable.previousTag());
        r.git("tag", "-d", "v1.1");
        ChangelogResult stable2 = ChangelogGenerator.generate(options(r, "1.1.0"));
        eq("stable skips betas", "v1.0.0", stable2.previousTag());
        truth("betas rolled up", stable2.markdown().contains("Two") && stable2.markdown().contains("Three") && stable2.markdown().contains("Four"));
        eq("stable suggestion", "1.1.0", stable2.suggestedVersion());
        ChangelogResult beta = ChangelogGenerator.generate(options(r, "1.1.0-beta.3"));
        eq("beta relative to beta", "v1.1.0-beta.2", beta.previousTag());
        eq("no suggestion for prerelease", null, beta.suggestedVersion());
        r.git("checkout", "-q", "-b", "side", "v1.0.0");
        r.commit("feat: side");
        r.tag("v9.9.9");
        r.git("checkout", "-q", "main");
        eq("unmerged tag ignored", "v1.0.0", ChangelogGenerator.generate(options(r, "1.1.0")).previousTag());
        Repo p = repo();
        p.commit("feat: a");
        p.tag("release-1.0.0");
        p.tag("v7.0.0");
        p.commit("fix: b");
        ChangelogOptions o = options(p, "1.0.1");
        o.tagPrefix = "release-";
        ChangelogResult res = ChangelogGenerator.generate(o);
        eq("custom prefix", "release-1.0.0", res.previousTag());
        eq("custom prefix tag", "release-1.0.1", res.tag());
    }

    static void pullRequests() throws Exception {
        Repo r = repo();
        r.commit("feat: base");
        r.tag("v1.0.0");
        r.git("checkout", "-q", "-b", "feature-x");
        r.commit("wip");
        r.commit("fix typo");
        r.git("checkout", "-q", "main");
        r.git("merge", "-q", "--no-ff", "feature-x", "-m", "Merge pull request #5 from user/feature-x\n\nfeat(ui): add the x screen");
        r.git("checkout", "-q", "-b", "feature-y");
        r.commit("fix(core): y is broken");
        r.commit("wip again");
        r.git("checkout", "-q", "main");
        r.git("merge", "-q", "--no-ff", "feature-y", "-m", "Merge pull request #6 from user/feature-y\n\nUpdate stuff");
        r.commit("fix: squashed fix (#7)");
        r.git("checkout", "-q", "-b", "feature-z");
        r.commit("perf: z is quick");
        r.git("checkout", "-q", "main");
        r.git("merge", "-q", "--no-ff", "feature-z", "-m", "Merge branch 'feature-z'");
        String md = ChangelogGenerator.generate(options(r, "1.1.0")).markdown();
        truth("PR title used", md.contains("- **ui:** Add the x screen ([#5](https://github.com/rhm176/ExampleMod/pull/5))"));
        truth("PR inner commits hidden", !md.contains("wip") && !md.contains("Fix typo"));
        truth("fallback to inner commit", md.contains("- **core:** Y is broken"));
        truth("squash PR number", md.contains("Squashed fix ([#7]"));
        truth("local merge uses inner commits", md.contains("Z is quick"));
        truth("merge noise hidden", !md.contains("Merge branch") && !md.contains("Merge pull request"));
        truth("no duplicate of x", md.indexOf("x screen") == md.lastIndexOf("x screen"));
    }

    static void reverts() throws Exception {
        Repo r = repo();
        r.commit("feat: base");
        r.tag("v1.0.0");
        String bad = r.commit("feat: risky thing");
        r.commit("fix: good fix");
        r.git("revert", "--no-edit", bad);
        String md = ChangelogGenerator.generate(options(r, "1.0.1")).markdown();
        truth("revert cancels feature", !md.contains("Risky thing") && !md.contains("Reverts"));
        truth("other changes survive", md.contains("Good fix"));

        Repo r2 = repo();
        String released = r2.commit("feat: shipped");
        r2.tag("v1.0.0");
        r2.git("revert", "--no-edit", released);
        String md2 = ChangelogGenerator.generate(options(r2, "1.0.1")).markdown();
        truth("revert of released shown", md2.contains("Reverts") && md2.contains("Feat: shipped"));
    }

    static void breaking() throws Exception {
        Repo r = repo();
        r.commit("feat: base");
        r.tag("v1.0.0");
        r.commit("feat(config)!: new config format");
        r.commit("refactor(api): rename methods\n\nBREAKING CHANGE: addons must update\ntheir imports");
        r.commit("fix: small");
        ChangelogResult res = ChangelogGenerator.generate(options(r, "2.0.0"));
        String md = res.markdown();
        truth("breaking first", md.indexOf("Breaking Changes") < md.indexOf("Bug Fixes"));
        truth("breaking bang listed", md.contains("- **config:** New config format"));
        truth("hidden type still shows when breaking", md.contains("- **api:** Rename methods"));
        truth("breaking notes", md.contains("  > addons must update\n  > their imports"));
        truth("breaking not repeated in features", md.indexOf("New config format") == md.lastIndexOf("New config format"));
        eq("breaking suggests major", "2.0.0", res.suggestedVersion());
        Repo z = repo();
        z.commit("feat: base");
        z.tag("v0.3.0");
        z.commit("fix!: drop old api");
        eq("0.x breaking is minor", "0.4.0", ChangelogGenerator.generate(options(z, "0.4.0")).suggestedVersion());
    }

    static void options() throws Exception {
        Repo r = repo();
        r.commit("feat: base");
        r.tag("v1.0.0");
        r.commit("refactor(core): tidy internals");
        r.commit("docs: explain config");
        r.commit("Tweak the thing");
        r.commit("fixup! feat: base");
        r.commit("compat: support other mod");
        ChangelogOptions o = options(r, "1.0.1");
        ChangelogResult hidden = ChangelogGenerator.generate(o);
        truth("defaults hide refactor/docs", !hidden.markdown().contains("internals") && !hidden.markdown().contains("config"));
        truth("unknown type warned", hidden.warnings().stream().anyMatch(w -> w.contains("compat")));
        o.sections = new ArrayList<>(o.sections);
        o.sections.add(new Section("refactor", "Refactoring"));
        o.sections.add(new Section("compat", "\uD83D\uDD0C Compatibility"));
        o.includeUnconventional = true;
        String md = ChangelogGenerator.generate(o).markdown();
        truth("custom section", md.contains("### Refactoring") && md.contains("- **core:** Tidy internals"));
        truth("custom type section", md.contains("Compatibility") && md.contains("Support other mod"));
        truth("unconventional included", md.contains("Other Changes") && md.contains("- Tweak the thing"));
        truth("fixup noise dropped", !md.contains("fixup"));
        eq("section codec", new Section("fix", "A = B"), Section.decode(new Section("fix", "A = B").encode()));
        ChangelogOptions bare = options(r, "1.0.1");
        bare.repositoryUrl = null;
        String plain = ChangelogGenerator.generate(bare).markdown();
        truth("no links without repo", !plain.contains("http") && !plain.contains("]("));
        r.git("remote", "add", "origin", "git@github.com:rhm176/ExampleMod.git");
        truth("links from origin remote", ChangelogGenerator.generate(bare).markdown().contains("https://github.com/rhm176/ExampleMod/compare/v1.0.0...v1.0.1"));
        Repo g = repo();
        g.commit("feat: base");
        g.tag("v1.0.0");
        g.commit("fix(zeta): one");
        g.commit("fix: no scope");
        g.commit("fix(alpha): two");
        g.commit("fix(zeta): three");
        String grouped = ChangelogGenerator.generate(options(g, "1.0.1")).markdown();
        truth("unscoped first", grouped.indexOf("No scope") < grouped.indexOf("**alpha:**"));
        truth("scopes sorted and grouped", grouped.indexOf("**alpha:**") < grouped.indexOf("**zeta:** One")
                && grouped.indexOf("**zeta:** One") < grouped.indexOf("**zeta:** Three"));
    }

    static void failures() throws Exception {
        Path empty = Files.createTempDirectory("not-a-repo");
        ChangelogOptions o = new ChangelogOptions();
        o.repository = empty;
        o.version = "1.0.0";
        throwsChangelog("not a repo", () -> ChangelogGenerator.generate(o), "not inside a git repository");

        Repo fresh = repo();
        throwsChangelog("no commits", () -> ChangelogGenerator.generate(options(fresh, "1.0.0")), "no commits");

        Repo source = repo();
        source.commit("feat: a");
        source.tag("v1.0.0");
        source.commit("fix: b");
        Path clone = Files.createTempDirectory("shallow").resolve("c");
        source.git("clone", "-q", "--depth", "1", "file://" + source.dir, clone.toString());
        ChangelogOptions shallow = new ChangelogOptions();
        shallow.repository = clone;
        shallow.version = "1.0.1";
        throwsChangelog("shallow clone", () -> ChangelogGenerator.generate(shallow), "fetch-depth: 0");

        throwsChangelog("unknown since", () -> {
            ChangelogOptions bad = options(source, "1.0.1");
            bad.since = "no-such-ref";
            ChangelogGenerator.generate(bad);
        }, "no-such-ref");
    }

    static void throwsChangelog(String what, Runnable r, String messagePart) {
        ModelSelfTest.checks++;
        try {
            r.run();
            ModelSelfTest.failures++;
            System.out.println("FAIL " + what + ": no exception");
        } catch (ChangelogException e) {
            if (!e.getMessage().contains(messagePart)) {
                ModelSelfTest.failures++;
                System.out.println("FAIL " + what + ": message was '" + e.getMessage() + "'");
            }
        }
    }

    static void eq(String what, Object expected, Object actual) {
        ModelSelfTest.eq("changelog: " + what, expected, actual);
    }

    static void truth(String what, boolean condition) {
        ModelSelfTest.truth("changelog: " + what, condition);
    }
}
