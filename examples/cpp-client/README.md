# QueueForge C++ submit client

This C++20 example submits a `TRANSFORM` job through QueueForge's authenticated REST API. It uses libcurl for HTTP and nlohmann/json for JSON construction.

1. Sign in to the dashboard and copy a bearer token from browser developer tools, or call `POST /api/auth/login` directly.
2. Set `QUEUEFORGE_TOKEN` and optionally `QUEUEFORGE_API`.
3. Configure and build with CMake using installed libcurl and nlohmann/json packages.
4. Run `queueforge-submit`.

PowerShell example:

```powershell
$env:QUEUEFORGE_API = 'http://localhost:8080'
$env:QUEUEFORGE_TOKEN = '<jwt-from-login-response>'
cmake -S . -B build
cmake --build build
.uildDebugqueueforge-submit.exe
```

The successful response contains the newly created job ID and its initial state.
