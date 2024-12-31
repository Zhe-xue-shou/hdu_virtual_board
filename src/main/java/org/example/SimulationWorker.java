package org.example;

import org.json.JSONObject;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimulationWorker {
    private final String workspaceName;
    private final ExecutorService executorService;
    private Process simulationProcess;
    private BufferedWriter simInput;
    private BufferedReader simOutput;
    private final WebSocketSession session;
    private volatile boolean running = true;

    public SimulationWorker(String workspaceName, WebSocketSession session) {
        this.workspaceName = workspaceName;
        this.session = session;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public void startSimulation(String verilogPath, String bindPath) throws Exception {
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
            throw new RuntimeException("Failed to create workbench. Exit code: " + exitCode);
        }

        // Step 2: 启动仿真
        builder = new ProcessBuilder("make", "run");
        builder.directory(new File(workspaceName));
        builder.redirectErrorStream(true);
        simulationProcess = builder.start();
        simInput = new BufferedWriter(new OutputStreamWriter(simulationProcess.getOutputStream()));
        simOutput = new BufferedReader(new InputStreamReader(simulationProcess.getInputStream()));

        // Step 3: 开启线程读取输出
        executorService.submit(this::readSimulationOutput);
    }

    public void sendSignal(String signalData) throws IOException {
        if (simInput != null) {
            simInput.write(signalData + "\n");
            simInput.flush();
        }
    }

    public void stopSimulation() {
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
                    JSONObject outputJson = new JSONObject(line);
                    if (session != null && session.isOpen()) {
                        System.out.println(outputJson.toString(4));
                        session.sendMessage(new TextMessage(outputJson.toString(4)));
                    }
                } catch (Exception e) {
                    System.err.println("Failed to parse simulator output as JSON: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading simulator output: " + e.getMessage());
        }
    }

    private void logProcessOutput(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
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
}
