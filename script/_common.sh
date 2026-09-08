#!/usr/bin/env bash
# Shared helper for script/*.sh: stop the Gradle daemons when the machine
# is under memory pressure. Source it, then register the EXIT trap:
#   source "$(dirname "$0")/_common.sh"
#   trap maybe_stop_daemon_on_memory_pressure EXIT
#
# Thresholds (percent of physical memory in use): Windows 90, macOS 95;
# override both with EASYAPI_MEM_PRESSURE_PCT. Linux/CI is left untouched.
# Best effort — never fails the calling script.
maybe_stop_daemon_on_memory_pressure() {
    local os threshold used_pct="" free_pct total free
    os="$(uname -s 2>/dev/null || true)"
    case "$os" in
        MINGW*|MSYS*|CYGWIN*)
            threshold="${EASYAPI_MEM_PRESSURE_PCT:-90}"
            if [[ -r /proc/meminfo ]]; then
                total="$(awk '/^MemTotal:/{print $2}' /proc/meminfo 2>/dev/null)"
                free="$(awk '/^MemFree:/{print $2}' /proc/meminfo 2>/dev/null)"
                if [[ -n "$total" && -n "$free" && "$total" -gt 0 ]]; then
                    used_pct=$(( (total - free) * 100 / total ))
                fi
            fi
            if [[ -z "$used_pct" ]]; then
                used_pct="$(powershell.exe -NoProfile -Command '$os = Get-CimInstance Win32_OperatingSystem; [int](($os.TotalVisibleMemorySize - $os.FreePhysicalMemory) * 100 / $os.TotalVisibleMemorySize)' 2>/dev/null | tr -d '[:space:]' || true)"
            fi
            ;;
        Darwin)
            threshold="${EASYAPI_MEM_PRESSURE_PCT:-95}"
            # memory_pressure reports "System-wide memory free percentage: N%"
            free_pct="$(memory_pressure 2>/dev/null | awk -F': ' '/memory free percentage/ {gsub(/%/, "", $2); print $2}' | tail -1 || true)"
            [[ -n "$free_pct" ]] && used_pct=$(( 100 - free_pct ))
            ;;
        *)
            return 0 ;;
    esac

    if [[ -z "$used_pct" || ! "$used_pct" =~ ^[0-9]+$ ]]; then
        return 0  # probe unavailable — leave the daemon alone
    fi

    if (( used_pct > threshold )); then
        echo "Memory pressure: ${used_pct}% in use (threshold ${threshold}%) — stopping Gradle daemons"
        ./gradlew --stop >/dev/null 2>&1 || true
    fi
    return 0
}
