package de.rhm176.build.model.changelog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class Git {
    record RawCommit(String hash, List<String> parents, String message) {
        String subject() {
            int newline = message.indexOf('\n');
            return (newline < 0 ? message : message.substring(0, newline)).strip();
        }

        String body() {
            int newline = message.indexOf('\n');
            return newline < 0 ? "" : message.substring(newline + 1).strip();
        }
    }

    private final Path directory;

    Git(Path directory) {
        this.directory = directory;
    }

    String run(String... args) {
        Optional<String> out = attempt(true, args);
        return out.orElse("");
    }

    Optional<String> tryRun(String... args) {
        return attempt(false, args);
    }

    private Optional<String> attempt(boolean failLoudly, String... args) {
        List<String> command = new ArrayList<>(List.of(
                "git", "-c", "core.quotepath=off", "-c", "log.showSignature=false", "-c", "color.ui=never"));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("LC_ALL", "C");
        try {
            Process process = builder.start();
            process.getOutputStream().close();
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
            String stdout = read(process.getInputStream());
            int exit = process.waitFor();
            if (exit != 0) {
                if (!failLoudly) return Optional.empty();
                throw new ChangelogException("`git " + String.join(" ", args) + "` failed (exit " + exit + "): "
                        + stderr.join().strip());
            }
            return Optional.of(stdout);
        } catch (IOException e) {
            throw new ChangelogException("Could not run git. Is it installed and on the PATH? (" + e.getMessage() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChangelogException("Interrupted while running git", e);
        }
    }

    private static String read(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    boolean isWorkTree() {
        return tryRun("rev-parse", "--is-inside-work-tree").map(s -> s.strip().equals("true")).orElse(false);
    }

    boolean isShallow() {
        return tryRun("rev-parse", "--is-shallow-repository").map(s -> s.strip().equals("true")).orElse(false);
    }

    Optional<String> resolve(String ref) {
        return tryRun("rev-parse", "--verify", "--quiet", ref + "^{commit}")
                .map(String::strip)
                .filter(s -> !s.isEmpty());
    }

    List<String> tagsMerged(String rev) {
        List<String> tags = new ArrayList<>();
        for (String line : run("tag", "--merged", rev).split("\n")) {
            if (!line.isBlank()) tags.add(line.strip());
        }
        return tags;
    }

    List<RawCommit> log(String range) {
        String out = run("log", "--topo-order", "--reverse", "--encoding=UTF-8", "--format=%x1e%H%x1f%P%x1f%B", range);
        List<RawCommit> commits = new ArrayList<>();
        for (String record : out.split("\u001e")) {
            if (record.isBlank()) continue;
            String[] fields = record.split("\u001f", 3);
            if (fields.length < 3) continue;
            List<String> parents = fields[1].isBlank() ? List.of() : List.of(fields[1].strip().split(" "));
            commits.add(new RawCommit(fields[0].strip(), parents, fields[2].strip()));
        }
        return commits;
    }

    List<String> revList(String range) {
        List<String> hashes = new ArrayList<>();
        for (String line : run("rev-list", range).split("\n")) {
            if (!line.isBlank()) hashes.add(line.strip());
        }
        return hashes;
    }

    Optional<String> remoteUrl() {
        return tryRun("remote", "get-url", "origin").map(String::strip).filter(s -> !s.isEmpty());
    }
}
