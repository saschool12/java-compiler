package com.jhonny.compiler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class CompilerController {

    @PostMapping("/compile")
    public ResponseEntity<Map<String, Object>> compile(
            @RequestBody CompileRequest request) {

        Map<String, Object> response = new HashMap<>();

        if (request == null ||
            request.getCode() == null ||
            request.getCode().trim().isEmpty()) {

            response.put("success", false);
            response.put("output", "No Java code provided.");

            return ResponseEntity.badRequest().body(response);
        }

        Path directory = null;

        try {

            directory = Files.createTempDirectory(
                "jhonny-java-"
            );

            Path javaFile =
                directory.resolve("Main.java");

            Files.writeString(
                javaFile,
                request.getCode()
            );

            ProcessBuilder compiler =
                new ProcessBuilder(
                    "javac",
                    "Main.java"
                );

            compiler.directory(directory.toFile());
            compiler.redirectErrorStream(true);

            Process compileProcess =
                compiler.start();

            String compileOutput =
                readOutput(compileProcess);

            boolean compileFinished =
                compileProcess.waitFor(
                    10,
                    TimeUnit.SECONDS
                );

            if (!compileFinished) {

                compileProcess.destroyForcibly();

                response.put("success", false);
                response.put(
                    "output",
                    "Compilation timed out."
                );

                return ResponseEntity.ok(response);
            }

            if (compileProcess.exitValue() != 0) {

                response.put("success", false);
                response.put(
                    "output",
                    compileOutput
                );

                return ResponseEntity.ok(response);
            }

            ProcessBuilder runner =
                new ProcessBuilder(
                    "java",
                    "-cp",
                    ".",
                    "Main"
                );

            runner.directory(directory.toFile());
            runner.redirectErrorStream(true);

            Process runProcess =
                runner.start();

            String runOutput =
                readOutput(runProcess);

            boolean runFinished =
                runProcess.waitFor(
                    5,
                    TimeUnit.SECONDS
                );

            if (!runFinished) {

                runProcess.destroyForcibly();

                response.put("success", false);
                response.put(
                    "output",
                    "Program execution timed out."
                );

                return ResponseEntity.ok(response);
            }

            response.put(
                "success",
                runProcess.exitValue() == 0
            );

            response.put(
                "output",
                runOutput
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            response.put("success", false);
            response.put(
                "output",
                "Server error: " + e.getMessage()
            );

            return ResponseEntity
                .internalServerError()
                .body(response);

        } finally {

            if (directory != null) {
                deleteDirectory(directory);
            }
        }
    }

    private String readOutput(Process process)
            throws IOException {

        BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(
                    process.getInputStream()
                )
            );

        StringBuilder output =
            new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        return output.toString();
    }

    private void deleteDirectory(Path directory) {

        try {

            Files.walk(directory)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });

        } catch (IOException ignored) {
        }
    }
}
