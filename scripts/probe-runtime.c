/* Build as a 32-bit Windows program and run beside the owned Game.exe copy. */
#define WIN32_LEAN_AND_MEAN
#define _WIN32_WINNT 0x0600
#include <windows.h>
#include <stdio.h>
#include <string.h>

int main(int argc, char **argv) {
    if (argc > 2 || (argc == 2 && strcmp(argv[1], "--trace") != 0)) return 2;
    const char *libraries[] = {
        "KERNEL32.dll", "USER32.dll", "GDI32.dll", "OLE32.dll", "WINMM.dll",
        "WSOCK32.dll", "DSOUND.dll", "VERSION.dll", "DINPUT.dll", "ADVAPI32.dll",
        "DDRAW.dll", "DMUSIC.dll", "gedx8musicdrv.dll"
    };
    FILE *report = fopen("northland-runtime.log", "w");
    if (!report) return 1;
    fprintf(report, "32-bit process started. Pointer size: %u\n", (unsigned)sizeof(void *));
    for (unsigned i = 0; i < sizeof(libraries) / sizeof(libraries[0]); ++i) {
        SetLastError(0);
        HMODULE module = LoadLibraryA(libraries[i]);
        DWORD error = module ? 0 : GetLastError();
        fprintf(report, "%s: %s (Win32 error %lu)\n", libraries[i],
                module ? "loaded" : "FAILED", (unsigned long)error);
        fflush(report);
        if (module) FreeLibrary(module);
    }
    if (argc == 1) {
        fclose(report);
        return 0;
    }
    STARTUPINFOA startup = {0};
    PROCESS_INFORMATION process = {0};
    startup.cb = sizeof(startup);
    char command[] = "Game.exe";
    BOOL created = CreateProcessA(NULL, command, NULL, NULL, FALSE, DEBUG_ONLY_THIS_PROCESS,
                                  NULL, NULL, &startup, &process);
    fprintf(report, "CreateProcess: %d (Win32 error %lu)\n", created,
            (unsigned long)(created ? 0 : GetLastError()));
    fflush(report);
    if (created) {
        DWORD deadline = GetTickCount() + 15000;
        BOOL exited = FALSE;
        while (!exited && (LONG)(deadline - GetTickCount()) > 0) {
            DEBUG_EVENT event;
            if (!WaitForDebugEvent(&event, 1000)) continue;
            DWORD status = DBG_CONTINUE;
            switch (event.dwDebugEventCode) {
            case CREATE_PROCESS_DEBUG_EVENT:
                fprintf(report, "Process entry point: %p\n", event.u.CreateProcessInfo.lpStartAddress);
                if (event.u.CreateProcessInfo.hFile) CloseHandle(event.u.CreateProcessInfo.hFile);
                CloseHandle(event.u.CreateProcessInfo.hThread);
                CloseHandle(event.u.CreateProcessInfo.hProcess);
                break;
            case CREATE_THREAD_DEBUG_EVENT:
                CloseHandle(event.u.CreateThread.hThread);
                break;
            case LOAD_DLL_DEBUG_EVENT: {
                char path[MAX_PATH] = "[path unavailable]";
                if (event.u.LoadDll.hFile) {
                    GetFinalPathNameByHandleA(event.u.LoadDll.hFile, path, MAX_PATH, 0);
                    CloseHandle(event.u.LoadDll.hFile);
                }
                fprintf(report, "DLL: %s\n", path);
                break;
            }
            case EXCEPTION_DEBUG_EVENT: {
                DWORD code = event.u.Exception.ExceptionRecord.ExceptionCode;
                fprintf(report, "Exception: 0x%08lx at %p; first chance %lu\n",
                        (unsigned long)code, event.u.Exception.ExceptionRecord.ExceptionAddress,
                        (unsigned long)event.u.Exception.dwFirstChance);
                if (code != EXCEPTION_BREAKPOINT && code != EXCEPTION_SINGLE_STEP)
                    status = DBG_EXCEPTION_NOT_HANDLED;
                break;
            }
            case EXIT_PROCESS_DEBUG_EVENT:
                fprintf(report, "Full exit code: 0x%08lx\n", (unsigned long)event.u.ExitProcess.dwExitCode);
                exited = TRUE;
                break;
            default: break;
            }
            fflush(report);
            ContinueDebugEvent(event.dwProcessId, event.dwThreadId, status);
        }
        if (!exited) {
            BOOL detached = DebugActiveProcessStop(process.dwProcessId);
            fprintf(report, "No exit event within 15 seconds; detach %s (Win32 error %lu).\n",
                    detached ? "succeeded" : "FAILED",
                    (unsigned long)(detached ? 0 : GetLastError()));
        }
        CloseHandle(process.hThread);
        CloseHandle(process.hProcess);
    }
    fclose(report);
    return 0;
}
