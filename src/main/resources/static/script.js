document.addEventListener("DOMContentLoaded", () => {
    const uploadForm = document.getElementById("uploadForm");
    const stopButton = document.getElementById("stopSimulation");
    const sendSignalButton = document.getElementById("sendSignal");
    const signalInput = document.getElementById("signalInput");
    const signalFileInput = document.getElementById("signalFile");
    const statusDiv = document.getElementById("status");
    const outputDiv = document.getElementById("output");

    let isSimulationRunning = false;
    let pollInterval;

    // Handle form submission to start simulation
    uploadForm.addEventListener("submit", async (event) => {
        event.preventDefault();

        // Reset status and output
        statusDiv.textContent = "Uploading files and starting simulation...";
        statusDiv.className = "status";
        outputDiv.textContent = "";

        const formData = new FormData();
        formData.append("verilogFile", document.getElementById("verilogFile").files[0]);
        formData.append("bindFile", document.getElementById("bindFile").files[0]);

        try {
            const response = await fetch("/fpga/simulate", {
                method: "POST",
                body: formData,
            });

            if (!response.ok) {
                throw new Error(Failed to start simulation: ${response.statusText});
            }

            const result = await response.text();
            statusDiv.textContent = "Simulation started. Listening for signals...";
            console.log("Simulation started:", result);

            isSimulationRunning = true;
            stopButton.style.display = "inline-block";
            sendSignalButton.style.display = "inline-block";

            startPolling();
        } catch (error) {
            statusDiv.textContent = "Error: " + error.message;
            statusDiv.className = "error";
        }
    });

    // Handle stopping the simulation
    stopButton.addEventListener("click", async () => {
        try {
            const response = await fetch("/fpga/stop", { method: "POST" });
            if (!response.ok) {
                throw new Error("Failed to stop simulation");
            }

            isSimulationRunning = false;
            clearInterval(pollInterval);

            statusDiv.textContent = "Simulation stopped.";
            stopButton.style.display = "none";
            sendSignalButton.style.display = "none";
        } catch (error) {
            statusDiv.textContent = "Error stopping simulation: " + error.message;
            statusDiv.className = "error";
        }
    });

    // Handle sending signal.json data
    sendSignalButton.addEventListener("click", async () => {
        let signalData;

        // Check if a file is selected for signal input
        if (signalFileInput.files.length > 0) {
            const file = signalFileInput.files[0];
            signalData = await file.text();
        } else {
            signalData = signalInput.value;
        }

        try {
            const response = await fetch("/fpga/signal", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                },
                body: signalData,
            });

            if (!response.ok) {
                throw new Error(Failed to send signal data: ${response.statusText});
            }

            const result = await response.text();
            statusDiv.textContent = "Signal data sent successfully.";
            console.log("Signal sent:", result);
        } catch (error) {
            statusDiv.textContent = "Error sending signal: " + error.message;
            statusDiv.className = "error";
        }
    });

    // Start polling for simulation output
    function startPolling() {
        pollInterval = setInterval(async () => {
            if (!isSimulationRunning) return;

            try {
                const response = await fetch("/fpga/signal");
                if (response.ok) {
                    const signalData = await response.json();
                    updateOutput(signalData);
                } else if (response.status === 404) {
                    console.log("Waiting for signal.json...");
                } else {
                    throw new Error("Failed to fetch signal data");
                }
            } catch (error) {
                console.error("Polling error:", error.message);
            }
        }, 1000); // Poll every second
    }

    // Update the output display
    function updateOutput(signalData) {
        outputDiv.textContent = JSON.stringify(signalData, null, 4); // Replace old content
    }


    // Stop polling and simulation if needed
    window.addEventListener("beforeunload", () => {
        if (isSimulationRunning) {
            clearInterval(pollInterval);
            console.log("Stopped polling.");
        }
    });
});
