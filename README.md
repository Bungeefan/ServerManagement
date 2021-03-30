# ServerManagement
Small and lightweight BungeeCord Plugin to shut down your server when nobody is online and restarts it when somebody wants to join.

# Example config
```json
{
  "startupTime": 20,
  "server": [
    {
      "name": "survival",
      "startScript": "survival/start.sh",
      "stopScript": "survival/stop.sh",
      "maxIdleTime": 10
    }
  ]
}
```
