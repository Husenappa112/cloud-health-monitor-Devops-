from locust import HttpUser, task, between


class CloudHealthMonitorUser(HttpUser):
    wait_time = between(1, 3)

    @task(3)
    def check_health(self):
        self.client.get(
            "/health",
            name="/health",
            timeout=10
        )

    @task(1)
    def collect_prometheus_metrics(self):
        self.client.get(
            "/actuator/prometheus",
            name="/actuator/prometheus",
            timeout=10
        )
