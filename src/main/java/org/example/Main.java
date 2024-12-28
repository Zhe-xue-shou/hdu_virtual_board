package org.example;

import java.io.*;

import org.json.JSONObject;

public class Main {
    public static void main(String[] args) {
        try {
            // 切换到 top 目录，执行 make run
            ProcessBuilder builder = new ProcessBuilder();
            builder.directory(new File("test")); // 确保在 top 目录下运行
            builder.command("make", "run"); // 执行 make run
            builder.redirectErrorStream(true); // 合并错误流和标准输出流
            Process process = builder.start();

            // 获取仿真程序的输入输出流
            BufferedReader simOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedWriter simInput = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            // 创建线程，异步读取仿真输出
            Thread outputReader = new Thread(() -> {
                try {
                    String line;
                    while ((line = simOutput.readLine()) != null) {
                        try {
                            // 解析仿真输出为 JSON 对象
                            JSONObject outputJson = new JSONObject(line);
                            System.out.println("Simulator Output: " + outputJson.toString(4)); // 格式化输出
                        } catch (Exception e) {
                            System.err.println("Failed to parse simulator output as JSON: " + line);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Error reading simulator output: " + e.getMessage());
                }
            });
            outputReader.start();

            // 模拟 JSON 格式的用户输入信号
            String jsonInputs = "{ \"SW7\":0,\"SW6\":1,\"SW5\":0,\"SW4\":1,\"SW3\":0,\"SW2\":1,\"SW1\":1,\"SW0\":0 }";

            for (int i = 0; i < 10; i++) {
                if (i == 3) {
                    simInput.write(jsonInputs + "\n"); // 写入 JSON 格式输入
                    simInput.flush(); // 确保立即发送输入到仿真程序
                }

                if (i == 9) {
                    System.out.println("Simulating EOF at 10th second.");
                    simInput.close(); // 模拟 EOF，关闭输入流
                    break;
                }

                Thread.sleep(1000); // 等待 1 秒，观察仿真结果
            }
            System.out.println("Out of break!");
            // 等待仿真程序完成执行
            process.waitFor();
            outputReader.join();
        } catch (IOException |
                 InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Done!");
    }
}