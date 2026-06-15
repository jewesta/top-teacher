#!/bin/bash

# Get the absolute directory path where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Change the current working directory to the script's directory
cd "$SCRIPT_DIR"

# Start the Spring Boot application in the background
java -jar TopTeacher.jar &

# Store the Process ID (PID) of the started Java process
APP_PID=$!

# Wait for 3 seconds to allow the Spring Boot server to initialize
sleep 5

# Open the default system browser with the specified local URL
xdg-open "http://localhost:8081/top-teacher/"

# Display a graphical dialog to the user (UI language remains German)
# This execution blocks until the user clicks the button or closes the window
zenity --info \
       --title="TopTeacher läuft" \
       --text="Die Anwendung läuft im Hintergrund.\nKlicke auf Anwendung beenden, um TopTeacher zu beenden." \
       --ok-label="Anwendung beenden"

# Terminate the Spring Boot application using its stored PID after the dialog is closed
kill "$APP_PID"