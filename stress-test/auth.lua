-- wrk Lua script: set Authorization header from env or default
local token = os.getenv("WRK_TOKEN") or ""

wrk.headers["Authorization"] = "Bearer " .. token
wrk.headers["Content-Type"] = "application/json"
