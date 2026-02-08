package main

import (
	"fmt"
	"html/template"
	"net/http"
	"os/exec"
	"strings"
	"time"
)

type Service struct {
	Name   string
	Port   string
	Status string
}

func checkDockerStatus() []Service {
	cmd := exec.Command("docker", "ps", "--format", "table {{.Names}}\\t{{.Ports}}\\t{{.Status}}")
	output, err := cmd.Output()
	if err != nil {
		return []Service{{Name: "Error", Port: "", Status: "Failed to check Docker"}}
	}

	lines := strings.Split(strings.TrimSpace(string(output)), "\n")
	var services []Service
	for _, line := range lines[1:] { // Skip header
		parts := strings.Fields(line)
		if len(parts) >= 3 {
			name := parts[0]
			port := parts[1]
			status := parts[2]
			services = append(services, Service{Name: name, Port: port, Status: status})
		}
	}
	return services
}

func checkHTTPHealth(url string) string {
	client := http.Client{Timeout: 5 * time.Second}
	resp, err := client.Get(url)
	if err != nil {
		return "Down"
	}
	defer resp.Body.Close()
	if resp.StatusCode == 200 {
		return "Up"
	}
	return "Down"
}

func updateServices(services []Service) {
	dockerServices := checkDockerStatus()
	for i, svc := range services {
		// Check Docker status
		found := false
		for _, ds := range dockerServices {
			if strings.Contains(ds.Name, svc.Name) {
				services[i].Status = ds.Status
				found = true
				break
			}
		}
		if !found {
			services[i].Status = "Not Running"
		}

		// Check HTTP health if applicable
		if svc.Port != "" {
			url := fmt.Sprintf("http://localhost:%s/health", svc.Port)
			httpStatus := checkHTTPHealth(url)
			services[i].Status += fmt.Sprintf(" (HTTP: %s)", httpStatus)
		}
	}
}

func main() {
	// Define services to monitor
	services := []Service{
		{Name: "guestbook", Port: "8000", Status: ""},
		{Name: "kafka", Port: "9092", Status: ""},
		{Name: "redis", Port: "6379", Status: ""},
	}

	// Serve static files
	http.Handle("/static/", http.StripPrefix("/static/", http.FileServer(http.Dir("static/"))))

	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.Method == "POST" && r.FormValue("action") == "refresh" {
			updateServices(services)
			http.Redirect(w, r, "/", http.StatusSeeOther)
			return
		}

		// Parse template each time for development
		tmpl, err := template.ParseFiles("templates/index.html")
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		err = tmpl.Execute(w, services)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
		}
	})

	fmt.Println("Server starting on :8080")
	http.ListenAndServe(":8080", nil)
}
