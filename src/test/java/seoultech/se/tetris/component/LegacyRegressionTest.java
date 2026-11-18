package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the existing standalone suite without letting System.exit stop JUnit. */
class LegacyRegressionTest {
    @TempDir
    Path workspace;

    @Test
    @DisplayName("기존 독립 실행 회귀 검사")
    void existingRegressionChecks() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
            File.separatorChar == '\\' ? "java.exe" : "java").toString();
        String classpath = System.getProperty("tetris.regression.classpath");
        assertTrue(classpath != null && !classpath.isBlank(),
            "Gradle test task must provide tetris.regression.classpath.");
        String coveragePath = System.getProperty("tetris.jacoco.destination");
        Path coverage = coveragePath == null || coveragePath.isBlank() ? null : Path.of(coveragePath);
        Path log = workspace.resolve("regression.log");
        List<String> command = new ArrayList<>();
        command.add(java);
        command.add("-Dfile.encoding=UTF-8");
        copyProperty(command, "tetris.jacoco.agent");
        copyProperty(command, "tetris.jacoco.destination");
        command.add("-cp");
        command.add(classpath);
        command.add(RefactoringRegressionTest.class.getName());
        Process process = new ProcessBuilder(command)
            .directory(workspace.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            // JaCoCo instruments every isolated child JVM; slower CI hosts need margin above the
            // uninstrumented suite without weakening each case's own 20-second timeout.
            long timeoutMinutes = coverage == null ? 5 : 10;
            boolean completed = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!completed) {
                terminate(process);
            }
            String output = Files.readString(log);
            System.out.print(output);
            assertTrue(completed, "회귀 검사 실행이 " + timeoutMinutes + "분을 초과했습니다.\n" + output);
            assertEquals(0, process.exitValue(), output);
        } finally {
            if (process.isAlive()) {
                terminate(process);
            }
        }
    }

    private static void copyProperty(List<String> command, String name) {
        String value = System.getProperty(name);
        if (value != null && !value.isBlank()) command.add("-D" + name + "=" + value);
    }

    private static void terminate(Process process) throws Exception {
        var descendants = process.descendants().toList();
        descendants.forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS), "회귀 검사 프로세스 종료 실패");
        for (ProcessHandle child : descendants) {
            child.onExit().get(10, TimeUnit.SECONDS);
        }
    }
}
