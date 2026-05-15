-- wrk Lua script: POST request with JSON body
local token = os.getenv("WRK_TOKEN") or ""
local body = os.getenv("WRK_BODY") or "{}"

wrk.headers["Authorization"] = "Bearer " .. token
wrk.headers["Content-Type"] = "application/json"

wrk.method = "POST"
wrk.body = body
