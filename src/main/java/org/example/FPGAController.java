package org.example;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.socket.WebSocketSession;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
//import java.util.concurrent.ConcurrentHashMap;

import static org.example.SimulationWorker.stopSimulationWorker;
import static org.example.SimulationWorker.workers;

@Slf4j
@RestController
//@CrossOrigin(origins = "http://127.0.0.1:5173") // 允许前端访问
@RequestMapping("/fpga")
public class FPGAController {
    private SimulationWorker.SimulationResponse simulationResponse;

    @PostMapping("/simulate")
//    @CrossOrigin(origins = "http://127.0.0.1:5173")
    public ResponseEntity<?> simulate(@RequestParam("verilogFile") MultipartFile verilogFile,
                                      @RequestParam("bindFile") MultipartFile bindFile,
                                      @RequestParam("sessionId") String sessionId
    ) {
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
            log.debug("Received sessionId:{}", sessionId);
//            System.out.println("Received sessionId:" + sessionId);
            if (session == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Error: WebSocket session not found for sessionId: " + sessionId);
            }

            // Step 3: 创建并启动 SimulationWorker
            SimulationWorker worker = new SimulationWorker(workspaceName, session);
            SimulationWorker.SimulationResponse simulationResponse = worker.startSimulation(verilogPath, bindPath);

            JSONObject responseJson = new JSONObject();

            switch (simulationResponse) {
                case Ok:
                    responseJson.put("msg", "Simulation started successful");
                    responseJson.put("code", 0);
                    workers.put(sessionId, worker);
                    return ResponseEntity.status(HttpStatus.OK).
                            body(responseJson.toString());
                case ErrorWhileSimulation:
                    responseJson.put("msg", "Error occurred during simulation.");
                    responseJson.put("notes", "Please try again later");
                    responseJson.put("code", 1);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(responseJson.toString());
                case FailedMakeWorkbench:
                    responseJson.put("msg", "failed to make workbench.");
                    responseJson.put("notes", "Please check your verilog file and bind_pins");
                    responseJson.put("code", 1);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(responseJson.toString());
                case FailedCreateWorkbench:
                    responseJson.put("msg", "failed to create workbench.");
                    responseJson.put("notes", "please try again later");
                    responseJson.put("code", 1);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(responseJson.toString());
                default:
                    responseJson.put("msg", "unknown error occurred");
                    responseJson.put("notes", "please report the error to the administrator");
                    responseJson.put("code", 1);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(responseJson.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getMessage());
//            System.err.println(e.getMessage());
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
        boolean f = stopSimulationWorker(sessionId);
        JSONObject response_msg = new JSONObject();
        if (!f) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error: No simulation running for sessionId " + sessionId);
        } else {
            log.debug("Simulation stopped successful by fetch url");
//            System.out.println("Simulation stopped successful by fetch url");
            response_msg.put("msg", "Simulation stopped successful and workspace cleaned up.");
            response_msg.put("code", 0);
            return ResponseEntity.ok(response_msg.toString());
        }
    }
}
