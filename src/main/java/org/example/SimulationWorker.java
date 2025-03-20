package org.example;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class SimulationWorker {
    public final static ConcurrentHashMap<String, SimulationWorker> workers = new ConcurrentHashMap<>();

    private final String workspaceName;
    private final ExecutorService executorService;
    private Process simulationProcess;
    private BufferedWriter simInput;
    private BufferedReader simOutput;
    private final WebSocketSession session;
    private volatile boolean running = true;

    enum SimulationResponse {
        Ok, FailedCreateWorkbench, FailedMakeWorkbench, ErrorWhileSimulation
    }

    public SimulationWorker(String workspaceName, WebSocketSession session) {
        this.workspaceName = workspaceName;
        this.session = session;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public SimulationResponse startSimulation(String verilogPath, String bindPath) throws Exception {
        // Step 1: 调用 Python 脚本创建工作区
        ProcessBuilder builder = new ProcessBuilder(
                "python3", "./script/create_workbench.py",
                "--workspace-name", workspaceName,
                "--verilog-file", verilogPath,
                "--bind-json", bindPath
        );
        builder.redirectErrorStream(true);
        Process createProcess = builder.start();
        logProcessOutput(createProcess);
        int exitCode = createProcess.waitFor();
        if (exitCode != 0) {
//            throw new RuntimeException("Failed to create workbench. Exit code: " + exitCode);
//            System.err.println("Error creating simulation workspace, clear " + workspaceName);
            log.warn("Error creating simulation workspace, clear \" {}", workspaceName);
            deleteDirectory(new File(workspaceName));
            return SimulationResponse.FailedCreateWorkbench;
        }

        // Step 2: 构建仿真环境
        builder = new ProcessBuilder("make");
        builder.directory(new File(workspaceName));
        builder.redirectErrorStream(true);
        Process makeProcess = builder.start();
        logProcessOutput(makeProcess);
        exitCode = makeProcess.waitFor();
        if (exitCode != 0) {
//            System.err.println("Error making simulation environment, clear " + workspaceName);
            log.warn("Error making workspace, clear \" {}", workspaceName);
            deleteDirectory(new File(workspaceName));
            return SimulationResponse.FailedMakeWorkbench;
        }

        // Step 3: 启动仿真环境
        builder = new ProcessBuilder("make", "run");
        builder.directory(new File(workspaceName));
        builder.redirectErrorStream(true);
        simulationProcess = builder.start();
        simInput = new BufferedWriter(new OutputStreamWriter(simulationProcess.getOutputStream()));
        simOutput = new BufferedReader(new InputStreamReader(simulationProcess.getInputStream()));

        // Step 4: 开启线程读取输出
        executorService.submit(this::readSimulationOutput);

        return SimulationResponse.Ok;
    }

    public void sendSignal(String signalData) throws IOException {
//        System.out.println("received signal!");
//        System.out.println(signalData);
        log.debug("received signal!");
        log.debug(signalData);

        if (simInput != null) {
            simInput.write(signalData + "\n");
            simInput.flush();
        }
    }

    private void stopSimulation() {
        running = false;
        executorService.shutdownNow();
        if (simulationProcess != null) {
            simulationProcess.destroy();
        }
        try {
            deleteDirectory(new File(workspaceName));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readSimulationOutput() {
        try {
            String line;
            while (running && (line = simOutput.readLine()) != null) {
                try {
                    JSONObject signalJson = new JSONObject(line);
                    if (session != null && session.isOpen()) {
                        JSONObject outputJson = new JSONObject()
                                .put("type", "signal")
                                .put("data", signalJson);
//                        System.out.println(signalJson.toString(4));

                        log.debug(outputJson.toString());
                        session.sendMessage(new TextMessage(signalJson.toString(4)));
                    }
                } catch (Exception e) {
//                    System.err.println("Failed to parse simulator output as JSON: " + line);
                    log.warn("Failed to parse output: {}", line);
                }
            }
            if (!running) {
                if (session != null) {
                    SimulationWebSocketHandler.sendErrorMessage(
                            session, "Exception occurred while simulation"
                    );
                }
            }
        } catch (IOException e) {
//            System.err.println("Error reading simulator output: " + e.getMessage());
            log.error("Error reading simulation output: {}", e.getMessage());
        }
    }

    private void logProcessOutput(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
//                System.out.println(line);
                log.debug(line);
            }
        }
    }

    private void deleteDirectory(File directory) throws IOException {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        Files.delete(directory.toPath());
    }

    public static boolean stopSimulationWorker(String sessionId) {
        SimulationWorker worker = workers.remove(sessionId);
        if (worker == null) {
//            System.err.println("Error: No simulation running for sessionId " + sessionId);

            return false;
        }

        worker.stopSimulation();
        return true;
    }
}
