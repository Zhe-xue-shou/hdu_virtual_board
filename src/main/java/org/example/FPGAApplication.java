package org.example;

import org.json.JSONObject;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootApplication
public class FPGAApplication {
    public static void main(String[] args) {
        SpringApplication.run(FPGAApplication.class, args);
    }
}

@RestController
@RequestMapping("/fpga")
class FPGAController {

    private final AtomicBoolean simulationRunning = new AtomicBoolean(false);
    private Process simulationProcess;
    private BufferedReader simOutput;
    private BufferedWriter simInput;

    @PostMapping("/simulate")
    public String simulate(@RequestParam("verilogFile") MultipartFile verilogFile,
                           @RequestParam("bindFile") MultipartFile bindFile) {
        String workspaceName = "test";
        String tmpSpacePath = "tmp";

        try {
            // Step 1: 创建工作区目录并保存上传的文件
            Files.createDirectories(Paths.get(tmpSpacePath));
            String verilogPath = tmpSpacePath + "/test.v";
            String bindPath = tmpSpacePath + "/bind.json";

            verilogFile.transferTo(Paths.get(verilogPath));
            bindFile.transferTo(Paths.get(bindPath));

            // Step 2: 调用 Python 脚本创建工作区
            ProcessBuilder builder = new ProcessBuilder(
                    "python3", "./script/create_workbench.py",
                    "--workspace-name", workspaceName,
                    "--verilog-file", verilogPath,
                    "--bind-json", bindPath
            );
            builder.redirectErrorStream(true);
            Process process = builder.start();

            // 打印 Python 脚本输出
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return "Error: Failed to create workbench. Exit code: " + exitCode;
            }

            // Step 3: 执行 make run
            builder = new ProcessBuilder("make", "run");
            builder.directory(new File(workspaceName));
            builder.redirectErrorStream(true);
            simulationProcess = builder.start();

            // 获取仿真程序的输入输出流
            simOutput = new BufferedReader(new InputStreamReader(simulationProcess.getInputStream()));
            simInput = new BufferedWriter(new OutputStreamWriter(simulationProcess.getOutputStream()));

            // 创建线程，异步读取仿真输出
            Thread outputReader = new Thread(() -> {
                try {
                    String line;
                    while ((line = simOutput.readLine()) != null) {
                        try {
                            // 解析仿真输出为 JSON 对象
                            JSONObject outputJson = new JSONObject(line);

                            // 推送 JSON 数据到 WebSocket
                            SimulationWebSocketHandler.broadcast(outputJson.toString());

                            System.out.println("Simulator Output: " + outputJson.toString(4));

                        } catch (Exception e) {
                            System.err.println("Failed to parse simulator output as JSON: " + line);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Error reading simulator output: " + e.getMessage());
                }
            });
            outputReader.start();
            simulationRunning.set(true);

            return "Simulation started successfully.";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    @PostMapping("/signal")
    public String sendSignal(@RequestBody String signalData) {
        try {
            if (!simulationRunning.get()) {
                return "Error: Simulation is not running.";
            }

            // 写入仿真程序输入流
            simInput.write(signalData + "\n");
            simInput.flush();
            System.out.println("Signal sent to simulator: " + signalData);

            return "Signal data received.";
        } catch (IOException e) {
            e.printStackTrace();
            return "Error sending signal: " + e.getMessage();
        }
    }

    @PostMapping("/stop")
    public String stopSimulation() {
        try {
            simulationRunning.set(false); // 停止 signal.json 监听
            if (simInput != null) {
                simInput.close(); // 发送 EOF
                System.out.println("Close input stream!");
            }
            if (simulationProcess != null) {
                simulationProcess.waitFor(); // 等待进程结束
            }
            return "Simulation stopped.";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error stopping simulation: " + e.getMessage();
        }
    }
}
