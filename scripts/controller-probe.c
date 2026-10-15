/* A read-only XInput probe for the actual Windows compatibility container.
 * It never injects input or changes game files. */
#define WIN32_LEAN_AND_MEAN
#define _WIN32_WINNT 0x0600
#include <windows.h>
#include <xinput.h>
#include <stdio.h>
#include <string.h>

typedef DWORD (WINAPI *GetStateFn)(DWORD, XINPUT_STATE *);
static GetStateFn get_state;
static HMODULE xinput;
static FILE *report;
static char status[2048];
static XINPUT_STATE previous[4];
static BOOL connected[4];

static void poll_controllers(void) {
    size_t used = (size_t)snprintf(status, sizeof(status),
        "Northland Thor input probe\r\n\r\n"
        "Checks the controller inside the Windows runtime.\r\n"
        "This window does not control the game.\r\n\r\n");
    for (DWORD i = 0; i < 4; ++i) {
        XINPUT_STATE state = {0};
        BOOL present = get_state && get_state(i, &state) == ERROR_SUCCESS;
        BOOL changed = present != connected[i] ||
            (present && memcmp(&state.Gamepad, &previous[i].Gamepad,
                               sizeof(state.Gamepad)) != 0);
        if (changed && report) {
            const XINPUT_GAMEPAD *p = &state.Gamepad;
            fprintf(report, "%lu pad=%lu connected=%d buttons=%04x "
                "LT=%u RT=%u LX=%d LY=%d RX=%d RY=%d\n",
                (unsigned long)GetTickCount(), (unsigned long)i, present,
                p->wButtons, p->bLeftTrigger, p->bRightTrigger,
                p->sThumbLX, p->sThumbLY, p->sThumbRX, p->sThumbRY);
            fflush(report);
        }
        connected[i] = present;
        previous[i] = state;
        if (used >= sizeof(status)) break;
        int written;
        if (present) {
            const XINPUT_GAMEPAD *p = &state.Gamepad;
            written = snprintf(status + used, sizeof(status) - used,
                "Pad %lu: CONNECTED\r\n"
                "Buttons %04X    LT %3u    RT %3u\r\n"
                "Left %6d, %6d    Right %6d, %6d\r\n\r\n",
                (unsigned long)i, p->wButtons, p->bLeftTrigger,
                p->bRightTrigger, p->sThumbLX, p->sThumbLY,
                p->sThumbRX, p->sThumbRY);
        } else {
            written = snprintf(status + used, sizeof(status) - used,
                "Pad %lu: disconnected\r\n\r\n", (unsigned long)i);
        }
        if (written < 0) break;
        used += (size_t)written;
    }
}

static LRESULT CALLBACK window_proc(HWND window, UINT message, WPARAM w, LPARAM l) {
    switch (message) {
    case WM_CREATE:
        SetTimer(window, 1, 33, NULL);
        return 0;
    case WM_TIMER:
        poll_controllers();
        InvalidateRect(window, NULL, FALSE);
        return 0;
    case WM_PAINT: {
        PAINTSTRUCT paint;
        HDC dc = BeginPaint(window, &paint);
        RECT area;
        GetClientRect(window, &area);
        FillRect(dc, &area, (HBRUSH)GetStockObject(BLACK_BRUSH));
        SetTextColor(dc, RGB(225, 236, 241));
        SetBkMode(dc, TRANSPARENT);
        SelectObject(dc, GetStockObject(ANSI_FIXED_FONT));
        area.left += 18;
        area.top += 18;
        DrawTextA(dc, status, -1, &area, DT_LEFT | DT_TOP | DT_NOPREFIX);
        EndPaint(window, &paint);
        return 0;
    }
    case WM_DESTROY:
        KillTimer(window, 1);
        PostQuitMessage(0);
        return 0;
    default:
        return DefWindowProcA(window, message, w, l);
    }
}

int WINAPI WinMain(HINSTANCE instance, HINSTANCE previous_instance,
                   LPSTR command_line, int show) {
    (void)previous_instance;
    (void)command_line;
    char path[MAX_PATH];
    DWORD length = GetModuleFileNameA(NULL, path, MAX_PATH);
    if (!length || length >= MAX_PATH) return 1;
    char *name = strrchr(path, '\\');
    if (!name || (size_t)(name + 1 - path) + sizeof("controller-probe.log") > sizeof(path))
        return 1;
    strcpy(name + 1, "controller-probe.log");
    report = fopen(path, "w");
    if (!report) return 1;

    const char *libraries[] = {"xinput1_4.dll", "xinput1_3.dll", "xinput9_1_0.dll"};
    for (unsigned i = 0; i < sizeof(libraries) / sizeof(libraries[0]); ++i) {
        xinput = LoadLibraryA(libraries[i]);
        if (!xinput) continue;
        FARPROC address = GetProcAddress(xinput, "XInputGetState");
        /* memcpy avoids incompatible function pointer casts under -Wextra. */
        if (address) memcpy(&get_state, &address, sizeof(get_state));
        if (get_state) {
            fprintf(report, "XInput backend: %s\n", libraries[i]);
            break;
        }
        FreeLibrary(xinput);
        xinput = NULL;
    }
    if (!get_state) {
        fprintf(report, "No XInputGetState implementation loaded.\n");
        fclose(report);
        MessageBoxA(NULL, "No XInput backend loaded. See controller-probe.log.",
                    "Northland Thor", MB_OK | MB_ICONERROR);
        return 1;
    }
    fflush(report);
    poll_controllers();
    WNDCLASSA cls = {0};
    cls.lpfnWndProc = window_proc;
    cls.hInstance = instance;
    cls.hCursor = LoadCursor(NULL, IDC_ARROW);
    cls.lpszClassName = "NorthlandThorControllerProbe";
    if (!RegisterClassA(&cls)) return 1;
    HWND window = CreateWindowA(cls.lpszClassName, "Northland Thor controller probe",
        WS_OVERLAPPEDWINDOW, CW_USEDEFAULT, CW_USEDEFAULT, 720, 520,
        NULL, NULL, instance, NULL);
    if (!window) return 1;
    ShowWindow(window, show);
    MSG message;
    int result;
    while ((result = GetMessageA(&message, NULL, 0, 0)) > 0) {
        TranslateMessage(&message);
        DispatchMessageA(&message);
    }
    fclose(report);
    FreeLibrary(xinput);
    return result < 0 ? 1 : 0;
}
