package org.example;

import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
//import java.util.concurrent.ConcurrentHashMap;

import static org.example.SimulationWorker.stopSimulationHelper;
import static org.example.SimulationWorker.workers;

@RestController
@RequestMapping("/fpga")
public class FPGAController {
    @PostMapping("/simulate")
    public ResponseEntity<?> simulate(@RequestParam("verilogFile") MultipartFile verilogFile,
                                      @RequestParam("bindFile") MultipartFile bindFile,
                                      @RequestParam("sessionId") String sessionId) {
        String taskId = UUID.randomUUID().toString();
        String workspaceName = "workspace-" + taskId;
        String tmpSpacePath = "tmp/" + workspaceName;

        try {
            // Step 1: 创建工作区目录并保存上传的文件
            Files.createDirectories(Paths.get(tmpSpacePath));
            String verilogPath = tmpSpacePath + "/top.v";
            String bindPath = tmpSpacePath + "/bind.json";

            verilogFile.transferTo(Paths.get(verilogPath));
            bindFile.transferTo(Paths.get(bindPath));

            // Step 2: 获取 WebSocketSession
            WebSocketSession session = SimulationWebSocketHandler.getSession(sessionId);
            if (session == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Error: WebSocket session not found for sessionId: " + sessionId);
            }

            // Step 3: 创建并启动 SimulationWorker
            SimulationWorker worker = new SimulationWorker(workspaceName, session);
            SimulationWorker.SimulationResponse simulationResponse = worker.startSimulation(verilogPath, bindPath);

            switch (simulationResponse) {
                case Ok:
                    workers.put(sessionId, worker);
                    return ResponseEntity.ok("Simulation started successfully.");
                case ErrorWhileSimulation:
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Error occurred during simulation.");
                case FailedMakeWorkbench:
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("Failed to make workbench.");
                case FailedCreateWorkbench:
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("Failed to create workbench.");
                default:
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Unknown error occurred.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new JSONObject().put("error", e.getMessage()).toString());
        }
    }

//    @PostMapping("/signal")
//    public ResponseEntity<?> sendSignal(@RequestParam("sessionId") String sessionId, @RequestBody String signalData) {
//        SimulationWorker worker = workers.get(sessionId);
//        if (worker == null) {
//            System.out.println("Error: No simulation running for sessionId " + sessionId);
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
//                    .body("Error: No simulation running for sessionId " + sessionId);
//        }
//
//        try {
//            worker.sendSignal(signalData);
//            return ResponseEntity.ok("Signal data received.");
//        } catch (IOException e) {
//            e.printStackTrace();
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body("Error sending signal: " + e.getMessage());
//        }
//    }

    @PostMapping("/stop")
    ResponseEntity<?> stopSimulation(@RequestParam("sessionId") String sessionId) {
        boolean f = stopSimulationHelper(sessionId);
        if (!f) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error: No simulation running for sessionId " + sessionId);
        } else
            return ResponseEntity.ok("Simulation stopped and workspace cleaned up.");
    }
}
