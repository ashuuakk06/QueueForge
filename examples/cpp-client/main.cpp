#include <curl/curl.h>
#include <nlohmann/json.hpp>

#include <cstdlib>
#include <iostream>
#include <string>

namespace {
size_t appendResponse(char* data, size_t size, size_t count, void* destination) {
    auto* response = static_cast<std::string*>(destination);
    response->append(data, size * count);
    return size * count;
}

std::string envOr(const char* key, const char* fallback) {
    const char* value = std::getenv(key);
    return value && *value ? value : fallback;
}
}

int main() {
    const std::string baseUrl = envOr("QUEUEFORGE_API", "http://localhost:8080");
    const std::string token = envOr("QUEUEFORGE_TOKEN", "");
    if (token.empty()) {
        std::cerr << "Set QUEUEFORGE_TOKEN to a JWT from POST /api/auth/login.\n";
        return 2;
    }

    nlohmann::json job = {
        {"name", "C++ client transform"},
        {"type", "TRANSFORM"},
        {"payload", {{"text", "submitted from modern C++"}}},
        {"maxAttempts", 3},
        {"retryDelaySeconds", 10},
        {"priority", 6}
    };

    CURL* curl = curl_easy_init();
    if (!curl) {
        std::cerr << "Could not initialize libcurl.\n";
        return 1;
    }

    std::string response;
    const std::string body = job.dump();
    const std::string authorization = "Authorization: Bearer " + token;
    struct curl_slist* headers = nullptr;
    headers = curl_slist_append(headers, "Content-Type: application/json");
    headers = curl_slist_append(headers, authorization.c_str());

    curl_easy_setopt(curl, CURLOPT_URL, (baseUrl + "/api/jobs").c_str());
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(curl, CURLOPT_POST, 1L);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, body.c_str());
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, appendResponse);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response);

    const CURLcode result = curl_easy_perform(curl);
    long status = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &status);
    curl_slist_free_all(headers);
    curl_easy_cleanup(curl);

    if (result != CURLE_OK) {
        std::cerr << "Request failed: " << curl_easy_strerror(result) << '\n';
        return 1;
    }
    std::cout << "HTTP " << status << '\n' << response << '\n';
    return status >= 200 && status < 300 ? 0 : 1;
}
